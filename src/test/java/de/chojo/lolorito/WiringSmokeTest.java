/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.web.Routes;
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
import de.chojo.lolorito.web.auth.TokenCipher;
import de.chojo.universalis.provider.NameSupplier;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.sql.Connection;
import java.util.Set;
import java.util.logging.Logger;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot-time smoke test. Runs a stripped-down copy of {@link LoloritoModule}
 * (roots + route multibinding, no eager socket-opening singletons) and
 * asserts that every route class the app wires up resolves — a stand-in
 * for "the graph is coherent". Fails fast on missing {@code @Inject},
 * cycles, or a removed multibinding entry, without needing testcontainers.
 *
 * <p>The eager singletons (Universalis WS, Discord shard manager, both
 * workers) are covered by the route-level integration tests instead.
 */
class WiringSmokeTest {

    @Test
    void everyRouteClassResolves() {
        var config = new File();
        var threading = new Threading();
        DataSource dataSource = new StubDataSource();
        // NameSupplier.EMPTY dodges the Java 25 + Mockito inline-mock issue
        // where the interface can't be mocked at runtime.
        var nameSupplier = NameSupplier.EMPTY;

        var injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(File.class).toInstance(config);
                bind(Threading.class).toInstance(threading);
                bind(DataSource.class).toInstance(dataSource);
                bind(NameSupplier.class).toInstance(nameSupplier);

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
                // AlertService now depends on an AlertDispatcher; bind a no-op
                // so the smoke test doesn't need the composite / DM wiring.
                bind(de.chojo.lolorito.service.AlertDispatcher.class).toInstance((rule, price) -> {});
            }

            @Provides
            @Singleton
            TokenCipher tokenCipher() {
                // Smoke test doesn't care about the actual key material — mint one so
                // the strict TokenCipher constructor doesn't fail on empty config.
                return new TokenCipher(TokenCipher.generateBase64Key());
            }
        });

        Set<Routes> routes = injector.getInstance(Key.get(new TypeLiteral<Set<Routes>>() {}));
        assertThat(routes).hasSize(20);
    }

    /**
     * Placeholder DataSource — no route class actually reaches SQL during the smoke test.
     */
    private static final class StubDataSource implements DataSource {
        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("smoke test");
        }

        @Override
        public Connection getConnection(String u, String p) {
            throw new UnsupportedOperationException("smoke test");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger("smoke");
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
