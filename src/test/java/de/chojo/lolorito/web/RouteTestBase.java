/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.zaxxer.hikari.HikariDataSource;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.entity.Session;
import de.chojo.lolorito.repository.Baskets;
import de.chojo.lolorito.repository.DesynthResults;
import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.repository.Items;
import de.chojo.lolorito.repository.MarketModelResiduals;
import de.chojo.lolorito.repository.MarketModels;
import de.chojo.lolorito.repository.OfferFilters;
import de.chojo.lolorito.repository.Offers;
import de.chojo.lolorito.repository.Recipes;
import de.chojo.lolorito.repository.Sessions;
import de.chojo.lolorito.repository.Users;
import de.chojo.lolorito.service.BasketService;
import de.chojo.lolorito.service.CalibrationService;
import de.chojo.lolorito.service.FilterService;
import de.chojo.lolorito.service.ItemDetailService;
import de.chojo.lolorito.service.ItemsService;
import de.chojo.lolorito.service.OffersService;
import de.chojo.lolorito.service.PlannerService;
import de.chojo.lolorito.service.UserService;
import de.chojo.lolorito.service.WorldsService;
import de.chojo.lolorito.web.api.AlertRoutes;
import de.chojo.lolorito.web.api.BasketRoutes;
import de.chojo.lolorito.web.api.CalibrationRoutes;
import de.chojo.lolorito.web.api.ItemRoutes;
import de.chojo.lolorito.web.api.ItemSearchRoutes;
import de.chojo.lolorito.web.api.MeFilterRoutes;
import de.chojo.lolorito.web.api.OffersRoutes;
import de.chojo.lolorito.web.api.PlannerRoutes;
import de.chojo.lolorito.web.api.WorldsRoutes;
import de.chojo.lolorito.web.auth.AuthRoutes;
import de.chojo.lolorito.web.auth.AuthService;
import de.chojo.lolorito.web.auth.DiscordOAuthClient;
import de.chojo.lolorito.web.auth.SessionResolver;
import de.chojo.lolorito.web.auth.TokenCipher;
import de.chojo.sadu.core.configuration.DatabaseConfig;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.postgresql.mapper.PostgresqlMapper;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.chojo.universalis.provider.NameSupplier;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * One-stop harness for route tests. Boots a Postgres testcontainer once per
 * subclass, wires a Guice injector using the production {@code LoloritoModule}
 * bindings but with the {@link DiscordOAuthClient} swapped for a stub, and
 * exposes helpers for building the Javalin app and seeding sessions.
 *
 * <p>Pattern lifted from the Javalin testing guide (JavalinTest) plus
 * Ember's per-class {@code RepositoryTestBase}. The intent is that each
 * concrete route test extends this class and drives requests via
 * {@code JavalinTest.test(app(), ...)} without repeating the setup.
 */
@Tag("database")
@Testcontainers
public abstract class RouteTestBase {

    protected static final String SESSION_COOKIE_NAME = "lolorito_session";
    /** Any test wanting to hit admin-guarded routes should call {@code seedSession(TEST_ADMIN_USER_ID)}. */
    protected static final long TEST_ADMIN_USER_ID = 90_001L;
    /**
     * 32 zero bytes base64 — a deterministic AES key that satisfies TokenCipher.
     */
    protected static final String TEST_ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("lolorito_test")
            .withUsername("test")
            .withPassword("test");

    private static final AtomicInteger SCHEMA_COUNTER = new AtomicInteger();
    protected static DataSource dataSource;
    protected static HikariDataSource pool;
    protected static String schemaName;
    protected static File config;
    protected static Injector injector;
    protected static StubDiscordOAuthClient stubDiscord;

    @BeforeAll
    static void setupBase() throws Exception {
        schemaName = "lolorito_tr" + SCHEMA_COUNTER.incrementAndGet();
        pool = buildPool();
        dataSource = pool;

        try (var conn = pool.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        }
        SqlUpdater.builder(pool, PostgreSql.get())
                .setReplacements(new QueryReplacement("lolorito", schemaName))
                .setVersionTable(schemaName + ".lolorito_version")
                .setSchemas(schemaName)
                .execute();

        var qc = QueryConfiguration.builder(pool)
                .setThrowExceptions(true)
                .setRowMapperRegistry(new RowMapperRegistry().register(PostgresqlMapper.getDefaultMapper()))
                .build();
        QueryConfiguration.setDefault(qc);

        config = testConfig();
        stubDiscord = new StubDiscordOAuthClient(config);
        injector = Guice.createInjector(new TestModule());
    }

    @AfterAll
    static void teardownBase() {
        if (pool != null) pool.close();
    }

    /**
     * Build the Javalin app for JavalinTest — no port bind, just the router.
     */
    protected static Javalin app() {
        return injector.getInstance(Web.class).build();
    }

    /**
     * Create a valid session row for {@code userId} and return the opaque id
     * that lives in the {@code lolorito_session} cookie. The session's access-
     * expires and membership-checked timestamps sit far enough in the future
     * that the auth revalidation ladder short-circuits without calling
     * Discord.
     */
    protected static String seedSession(long userId) {
        // Ensure the referenced offer_filter row exists — session has an FK to it.
        query("INSERT INTO offer_filter (user_id) VALUES (:u) ON CONFLICT (user_id) DO NOTHING")
                .single(call().bind("u", userId))
                .insert();

        var cipher = injector.getInstance(TokenCipher.class);
        var access = cipher.encrypt("test-access");
        var refresh = cipher.encrypt("test-refresh");
        var now = Instant.now();
        var id = UUID.randomUUID().toString();
        var session = new Session(
                id,
                userId,
                now,
                now.plus(30, ChronoUnit.DAYS),
                "route-test/1.0",
                access.ciphertext(),
                access.iv(),
                now.plus(1, ChronoUnit.HOURS),
                refresh.ciphertext(),
                refresh.iv(),
                true,
                now);
        injector.getInstance(Sessions.class).create(session);
        return id;
    }

    protected static String cookieHeader(String sessionId) {
        return SESSION_COOKIE_NAME + "=" + sessionId;
    }

    // -- Config + Guice wiring --------------------------------------------

    private static HikariDataSource buildPool() {
        DatabaseConfig dbConfig = new DatabaseConfig() {
            @Override
            public String host() {
                return PG.getHost();
            }

            @Override
            public String port() {
                return String.valueOf(PG.getFirstMappedPort());
            }

            @Override
            public String user() {
                return PG.getUsername();
            }

            @Override
            public String password() {
                return PG.getPassword();
            }

            @Override
            public String database() {
                return PG.getDatabaseName();
            }
        };
        return DataSourceCreator.create(PostgreSql.get())
                .configure(cfg ->
                        cfg.withConfig(dbConfig).currentSchema(schemaName).applicationName("LoloritoRouteTest"))
                .create()
                .withMaximumPoolSize(4)
                .build();
    }

    private static File testConfig() {
        File f = new File();
        setField(f.http(), "tokenEncryptionKey", TEST_ENCRYPTION_KEY);
        setField(f.http(), "sessionCookieName", SESSION_COOKIE_NAME);
        setField(f.http(), "secureCookies", false);
        // Port 0 → OS picks a free ephemeral port. Prevents "already running
        // backend on 8080" collisions when smoke tests boot Javalin locally.
        setField(f.http(), "port", 0);
        setField(f.baseSettings(), "botGuild", StubDiscordOAuthClient.DEFAULT_GUILD_ID);
        // Seed a canonical admin id so AdminSessionRoutes tests can hit the
        // owner-only branch by starting a session as this user.
        setField(f.baseSettings(), "botOwner", new java.util.ArrayList<>(java.util.List.of(TEST_ADMIN_USER_ID)));
        return f;
    }

    /**
     * Ocular POJOs don't expose setters — poke through reflection for tests.
     */
    private static void setField(Object target, String name, Object value) {
        try {
            Class<?> cls = target.getClass();
            while (cls != null) {
                for (Field f : cls.getDeclaredFields()) {
                    if (f.getName().equals(name)) {
                        f.setAccessible(true);
                        f.set(target, value);
                        return;
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not set field " + name, e);
        }
    }

    /**
     * Test-scope Guice module. Mirrors {@code LoloritoModule} but skips the
     * Data / Threading roots — the repositories and the DataSource come from
     * the container above, and {@link DiscordOAuthClient} is swapped for
     * {@link StubDiscordOAuthClient} so we don't hit real Discord.
     */
    private static final class TestModule extends AbstractModule {
        @Override
        protected void configure() {
            Multibinder<Routes> routes = Multibinder.newSetBinder(binder(), Routes.class);
            routes.addBinding().to(AuthRoutes.class);
            routes.addBinding().to(OffersRoutes.class);
            routes.addBinding().to(MeFilterRoutes.class);
            routes.addBinding().to(WorldsRoutes.class);
            routes.addBinding().to(ItemRoutes.class);
            routes.addBinding().to(PlannerRoutes.class);
            routes.addBinding().to(CalibrationRoutes.class);
            routes.addBinding().to(BasketRoutes.class);
            routes.addBinding().to(AlertRoutes.class);
            routes.addBinding().to(ItemSearchRoutes.class);
            routes.addBinding().to(de.chojo.lolorito.web.api.RetainerSaleRoutes.class);
            routes.addBinding().to(de.chojo.lolorito.web.api.PlannerPresetRoutes.class);
            routes.addBinding().to(de.chojo.lolorito.web.api.CharacterRoutes.class);
            routes.addBinding().to(de.chojo.lolorito.web.api.RetainerRoutes.class);
            routes.addBinding().to(de.chojo.lolorito.web.api.SessionRoutes.class);
            routes.addBinding().to(de.chojo.lolorito.web.api.ItemCatalogRoutes.class);
            routes.addBinding().to(de.chojo.lolorito.web.api.UserSkillLevelRoutes.class);
            routes.addBinding().to(de.chojo.lolorito.web.api.AdminSessionRoutes.class);
            routes.addBinding().to(de.chojo.lolorito.web.api.DesynthExplorerRoutes.class);
            routes.addBinding().to(de.chojo.lolorito.web.api.AdminMarketRoutes.class);
            routes.addBinding().to(de.chojo.lolorito.web.api.TrendRoutes.class);
        }

        @Provides
        @Singleton
        de.chojo.lolorito.service.TrendService trendService(NameSupplier names) {
            return new de.chojo.lolorito.service.TrendService(
                    config, new de.chojo.lolorito.repository.SalesTrends(null), names);
        }

        @Provides
        @Singleton
        de.chojo.lolorito.repository.CharacterRetainers characterRetainers() {
            return new de.chojo.lolorito.repository.CharacterRetainers();
        }

        @Provides
        @Singleton
        de.chojo.lolorito.service.RetainerAttributionService retainerAttributionService(
                de.chojo.lolorito.repository.CharacterRetainers repo) {
            return new de.chojo.lolorito.service.RetainerAttributionService(repo);
        }

        @Provides
        @Singleton
        de.chojo.lolorito.repository.RetainerSales retainerSales() {
            return new de.chojo.lolorito.repository.RetainerSales();
        }

        @Provides
        @Singleton
        de.chojo.lolorito.repository.PlannerPresets plannerPresets() {
            return new de.chojo.lolorito.repository.PlannerPresets();
        }

        @Provides
        @Singleton
        de.chojo.lolorito.repository.CharacterProfiles characterProfiles() {
            return new de.chojo.lolorito.repository.CharacterProfiles();
        }

        @Provides
        @Singleton
        de.chojo.lolorito.service.LodestoneClient lodestoneClient() {
            // Route tests want deterministic behaviour and no outbound
            // Lodestone traffic. This fetcher returns synthetic HTML so
            // the parser + service happy-path is exercised end-to-end.
            return new de.chojo.lolorito.service.LodestoneClient(url -> {
                if (url.endsWith("/class_job/")) {
                    return "<html><body><div class=\"character__job__role\">"
                            + "<ul class=\"character__job\">"
                            + "<li><div class=\"character__job__name\">Blacksmith</div>"
                            + "<div class=\"character__job__level\">80</div></li>"
                            + "</ul></div></body></html>";
                }
                return "<html><body><p class=\"frame__chara__name\">TestChar</p>"
                        + "<p class=\"frame__chara__world\">Odin [Light]</p></body></html>";
            });
        }

        @Provides
        @Singleton
        de.chojo.lolorito.service.CharacterProfileService characterProfileService(
                de.chojo.lolorito.repository.CharacterProfiles repo,
                de.chojo.lolorito.service.LodestoneClient client,
                de.chojo.lolorito.service.UserSkillLevelService skills) {
            return new de.chojo.lolorito.service.CharacterProfileService(repo, client, skills);
        }

        @Provides
        @Singleton
        File config() {
            return config;
        }

        @Provides
        @Singleton
        NameSupplier nameSupplier() {
            return NameSupplier.EMPTY;
        }

        @Provides
        @Singleton
        Sessions sessions() {
            return new Sessions(dataSource);
        }

        @Provides
        @Singleton
        OfferFilters offerFilters() {
            return new OfferFilters(dataSource);
        }

        @Provides
        @Singleton
        Offers offers() {
            return new Offers(dataSource);
        }

        @Provides
        @Singleton
        MarketModels marketModels() {
            return new MarketModels(dataSource);
        }

        @Provides
        @Singleton
        ItemDetail itemDetail() {
            return new ItemDetail();
        }

        @Provides
        @Singleton
        Recipes recipes() {
            return new Recipes(dataSource);
        }

        @Provides
        @Singleton
        DesynthResults desynthResults() {
            return new DesynthResults(dataSource);
        }

        @Provides
        @Singleton
        MarketModelResiduals marketModelResiduals() {
            return new MarketModelResiduals(dataSource);
        }

        @Provides
        @Singleton
        Baskets baskets() {
            return new Baskets();
        }

        @Provides
        @Singleton
        BasketService basketService(Baskets repo) {
            return new BasketService(repo);
        }

        @Provides
        @Singleton
        de.chojo.lolorito.repository.AlertRules alertRules() {
            return new de.chojo.lolorito.repository.AlertRules();
        }

        @Provides
        @Singleton
        de.chojo.lolorito.service.AlertService alertService(de.chojo.lolorito.repository.AlertRules repo) {
            return new de.chojo.lolorito.service.AlertService(repo, (rule, price) -> {});
        }

        @Provides
        @Singleton
        de.chojo.lolorito.repository.UserPreferencesRepo userPreferencesRepo() {
            return new de.chojo.lolorito.repository.UserPreferencesRepo();
        }

        @Provides
        @Singleton
        de.chojo.lolorito.service.UserPreferencesService userPreferencesService(
                de.chojo.lolorito.repository.UserPreferencesRepo repo) {
            return new de.chojo.lolorito.service.UserPreferencesService(repo);
        }

        @Provides
        @Singleton
        de.chojo.lolorito.service.ItemSearchService itemSearchService(NameSupplier names) {
            return new de.chojo.lolorito.service.ItemSearchService(names);
        }

        @Provides
        @Singleton
        de.chojo.lolorito.repository.PerfMetrics perfMetrics() {
            return new de.chojo.lolorito.repository.PerfMetrics();
        }

        @Provides
        @Singleton
        Items items(NameSupplier names) {
            return new Items(names);
        }

        @Provides
        @Singleton
        Users users() {
            return new Users(dataSource);
        }

        @Provides
        @Singleton
        FilterService filterService(OfferFilters repo) {
            return new FilterService(repo);
        }

        @Provides
        @Singleton
        UserService userService(Users repo) {
            return new UserService(repo);
        }

        @Provides
        @Singleton
        ItemsService itemsService(Items repo, FilterService filters, NameSupplier names) {
            return new ItemsService(repo, filters, names);
        }

        @Provides
        @Singleton
        WorldsService worldsService() {
            return new WorldsService();
        }

        @Provides
        @Singleton
        OffersService offersService(Offers repo, NameSupplier names) {
            return new OffersService(config, repo, names, new de.chojo.lolorito.service.ItemCatalog());
        }

        @Provides
        @Singleton
        PlannerService plannerService(
                Offers repo, NameSupplier names, Recipes recipes, ItemDetail itemDetail, DesynthResults desynth) {
            return new PlannerService(
                    config,
                    repo,
                    names,
                    new de.chojo.lolorito.service.ItemCatalog(),
                    recipes,
                    itemDetail,
                    desynth,
                    new de.chojo.lolorito.repository.MarketModels(dataSource));
        }

        @Provides
        @Singleton
        ItemDetailService itemDetailService(
                ItemDetail detail, Recipes recipes, DesynthResults desynth, NameSupplier names) {
            return new ItemDetailService(
                    config,
                    detail,
                    recipes,
                    desynth,
                    names,
                    new de.chojo.lolorito.service.ItemCatalog(),
                    new de.chojo.lolorito.value.MarketModelFitter(
                            config,
                            new de.chojo.lolorito.repository.MarketModelResiduals(null),
                            new de.chojo.lolorito.repository.ListingEpisodes(null)),
                    new de.chojo.lolorito.repository.MarketModels(null));
        }

        @Provides
        @Singleton
        CalibrationService calibrationService(
                MarketModelResiduals repo, de.chojo.lolorito.repository.PerfMetrics metrics) {
            return new CalibrationService(
                    repo,
                    metrics,
                    new de.chojo.lolorito.repository.ListingEpisodes(null),
                    new de.chojo.lolorito.repository.MarketModels(null),
                    de.chojo.universalis.provider.NameSupplier.EMPTY);
        }

        @Provides
        @Singleton
        TokenCipher tokenCipher() {
            return new TokenCipher(config.http().tokenEncryptionKey());
        }

        @Provides
        @Singleton
        DiscordOAuthClient discord() {
            return stubDiscord;
        }

        @Provides
        @Singleton
        AuthService authService(Sessions sessions, DiscordOAuthClient client, TokenCipher cipher) {
            // Tests don't need a bot for guild-membership checks — the stub
            // Discord OAuth client always answers eligible.
            com.google.inject.Provider<de.chojo.lolorito.core.Discord> noBot = () -> null;
            return new AuthService(config, sessions, client, cipher, noBot);
        }

        @Provides
        @Singleton
        SessionResolver sessionResolver(Sessions sessions, AuthService auth) {
            return new SessionResolver(config, sessions, auth);
        }

        @Provides
        @Singleton
        Threading threading() {
            return new Threading();
        }
    }
}
