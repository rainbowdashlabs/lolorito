/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import de.chojo.lolorito.catalog.CatalogRefreshWorker;
import de.chojo.lolorito.config.Conf;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.core.Discord;
import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.core.Universalis;
import de.chojo.lolorito.service.AlertDispatcher;
import de.chojo.lolorito.service.AlertScanner;
import de.chojo.lolorito.service.CalibrationHistoryWorker;
import de.chojo.lolorito.service.CompositeAlertDispatcher;
import de.chojo.lolorito.service.DataRefreshWorker;
import de.chojo.lolorito.value.MarketModelWorker;
import de.chojo.lolorito.web.Routes;
import de.chojo.lolorito.web.api.AlertRoutes;
import de.chojo.lolorito.web.api.BasketRoutes;
import de.chojo.lolorito.web.api.CalibrationRoutes;
import de.chojo.lolorito.web.api.CharacterRoutes;
import de.chojo.lolorito.web.api.ItemRoutes;
import de.chojo.lolorito.web.api.ItemSearchRoutes;
import de.chojo.lolorito.web.api.MeFilterRoutes;
import de.chojo.lolorito.web.api.OffersRoutes;
import de.chojo.lolorito.web.api.PlannerPresetRoutes;
import de.chojo.lolorito.web.api.PlannerRoutes;
import de.chojo.lolorito.web.api.RetainerRoutes;
import de.chojo.lolorito.web.api.RetainerSaleRoutes;
import de.chojo.lolorito.web.api.WorldsRoutes;
import de.chojo.lolorito.web.auth.AuthRoutes;
import de.chojo.lolorito.web.auth.SessionRevalidator;
import de.chojo.lolorito.web.auth.TokenCipher;
import de.chojo.universalis.provider.NameSupplier;
import org.slf4j.Logger;

import java.lang.reflect.Field;

import javax.sql.DataSource;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Guice module. Binds the pre-built roots (File, Threading, DataSource,
 * NameSupplier), the {@link Routes} multibinding, and the eager singletons
 * whose constructors kick off scheduled work (workers, Universalis WS,
 * Discord shard manager).
 *
 * <p>Repositories and services are {@code @Inject}-annotated themselves
 * so Guice can construct them on demand — no per-class {@code @Provides}
 * methods needed here.
 */
public class LoloritoModule extends AbstractModule {

    private static final Logger log = getLogger(LoloritoModule.class);

    private final File config;
    private final Threading threading;
    private final DataSource dataSource;
    private final NameSupplier nameSupplier;
    private final Conf conf;

    public LoloritoModule(File config, Threading threading, DataSource dataSource, NameSupplier nameSupplier) {
        this(config, threading, dataSource, nameSupplier, null);
    }

    /**
     * Full-fat ctor used from {@link Lolorito#main}. The {@code conf}
     * handle lets us persist auto-generated config (e.g. the first-boot
     * token-encryption key) back to disk instead of regenerating an
     * ephemeral value on every restart.
     */
    public LoloritoModule(
            File config, Threading threading, DataSource dataSource, NameSupplier nameSupplier, Conf conf) {
        this.config = config;
        this.threading = threading;
        this.dataSource = dataSource;
        this.nameSupplier = nameSupplier;
        this.conf = conf;
    }

    @Override
    protected void configure() {
        // Roots — everything built before Guice is bound here.
        bind(File.class).toInstance(config);
        bind(Threading.class).toInstance(threading);
        bind(DataSource.class).toInstance(dataSource);
        bind(NameSupplier.class).toInstance(nameSupplier);

        // Route multibinding — new endpoints slot in with one line.
        Multibinder<Routes> routes = Multibinder.newSetBinder(binder(), Routes.class);
        routes.addBinding().to(AuthRoutes.class);
        routes.addBinding().to(OffersRoutes.class);
        routes.addBinding().to(MeFilterRoutes.class);
        routes.addBinding().to(WorldsRoutes.class);
        routes.addBinding().to(ItemRoutes.class);
        routes.addBinding().to(PlannerRoutes.class);
        routes.addBinding().to(de.chojo.lolorito.web.api.ShoppingRoutes.class);
        routes.addBinding().to(CalibrationRoutes.class);
        routes.addBinding().to(BasketRoutes.class);
        routes.addBinding().to(AlertRoutes.class);
        routes.addBinding().to(ItemSearchRoutes.class);
        routes.addBinding().to(RetainerSaleRoutes.class);
        routes.addBinding().to(PlannerPresetRoutes.class);
        routes.addBinding().to(CharacterRoutes.class);
        routes.addBinding().to(RetainerRoutes.class);
        routes.addBinding().to(de.chojo.lolorito.web.api.SessionRoutes.class);
        routes.addBinding().to(de.chojo.lolorito.web.api.ItemCatalogRoutes.class);
        routes.addBinding().to(de.chojo.lolorito.web.api.UserSkillLevelRoutes.class);
        routes.addBinding().to(de.chojo.lolorito.web.api.AdminSessionRoutes.class);
        routes.addBinding().to(de.chojo.lolorito.web.api.DesynthExplorerRoutes.class);
        routes.addBinding().to(de.chojo.lolorito.web.api.AdminMarketRoutes.class);
        routes.addBinding().to(de.chojo.lolorito.web.api.TrendRoutes.class);

        // Eager singletons — their constructors schedule work / open sockets.
        bind(DataRefreshWorker.class).asEagerSingleton();
        bind(MarketModelWorker.class).asEagerSingleton();
        bind(Universalis.class).asEagerSingleton();
        bind(Discord.class).asEagerSingleton();
        bind(SessionRevalidator.class).asEagerSingleton();

        // Alert dispatch: default binding is the Discord DM sink. Tests
        // override this to capture calls in-memory.
        // AlertDispatcher fans out to Discord DM + (optional) webhook via the composite.
        bind(AlertDispatcher.class).to(CompositeAlertDispatcher.class);
        bind(AlertScanner.class).asEagerSingleton();
        bind(CalibrationHistoryWorker.class).asEagerSingleton();
        bind(CatalogRefreshWorker.class).asEagerSingleton();
    }

    /**
     * TokenCipher takes a raw base64 key from config, not an injectable
     * dependency. If none is configured we mint one and — when a {@link Conf}
     * handle is available — persist it back to disk so sessions survive
     * the next restart.
     */
    @Provides
    @Singleton
    TokenCipher tokenCipher(File config) {
        String key = config.http().tokenEncryptionKey();
        if (key == null || key.isBlank()) {
            key = TokenCipher.generateBase64Key();
            setHttpField("tokenEncryptionKey", key);
            if (conf != null) {
                try {
                    conf.save();
                    log.warn("http.tokenEncryptionKey was empty — generated a fresh 32-byte AES key "
                            + "and persisted it to config/config.yaml.");
                } catch (Exception e) {
                    log.warn(
                            "http.tokenEncryptionKey was empty — generated an ephemeral key, but "
                                    + "persisting it to config failed. Sessions will drop on restart.",
                            e);
                }
            } else {
                log.warn("http.tokenEncryptionKey was empty — generated an ephemeral key. No Conf handle "
                        + "available to persist it; sessions will drop on restart.");
            }
        }
        return new TokenCipher(key);
    }

    /** Reflect a value into the runtime {@link File}'s {@code http()} sub-config. Ocular POJOs have no setters. */
    private void setHttpField(String name, Object value) {
        try {
            Field f = config.http().getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(config.http(), value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to poke http." + name + " into the runtime config", e);
        }
    }
}
