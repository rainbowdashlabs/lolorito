/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.core;

import com.zaxxer.hikari.HikariDataSource;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.service.CraftDesynthLoader;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.postgresql.mapper.PostgresqlMapper;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.chojo.universalis.worlds.DataCenter;
import de.chojo.universalis.worlds.Region;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Everything that has to happen <em>before</em> Guice can start wiring:
 * open the Hikari pool, run sadu migrations, install the default
 * {@link QueryConfiguration}, and seed the {@code worlds} table. The
 * returned {@link HikariDataSource} is then bound in the module as a
 * Guice singleton.
 *
 * <p>The recipe / desynth cold seed is deliberately <em>not</em> loaded
 * synchronously here — {@link CraftDesynthLoader#loadAll()} does ~25 k
 * INSERTs against Postgres and would tack tens of seconds onto startup
 * on a slow connection. It's fired off on
 * {@link Threading#botWorker()} instead so boot returns as soon as the
 * pool is up. Handlers that need recipe data early are transparently
 * fine because the tables are simply empty until the background load
 * lands (a matter of seconds on any reasonable DB); the async
 * {@link de.chojo.lolorito.catalog.CatalogRefreshWorker} then keeps
 * them fresh from XIVAPI on the configured cadence.
 */
public final class DatabaseBootstrap {

    private static final Logger log = getLogger(DatabaseBootstrap.class);

    private DatabaseBootstrap() {}

    public static HikariDataSource bootstrap(Threading threading, File config) throws IOException, SQLException {
        var dataSource = openPool(threading, config);
        runMigrations(dataSource, config.database().schema());
        installQueryConfiguration(dataSource);
        upsertWorlds();
        submitClasspathSeedLoad(threading);
        return dataSource;
    }

    /**
     * Kick off the classpath cold seed on the bot worker so app startup
     * doesn't block on ~25 k INSERTs. On fresh clones without a build-
     * time {@code refreshCatalog}, the loader logs "seed missing" and
     * bails — the async
     * {@link de.chojo.lolorito.catalog.CatalogRefreshWorker} then
     * populates from XIVAPI at {@code catalog.initialDelaySeconds}.
     */
    private static void submitClasspathSeedLoad(Threading threading) {
        log.info("Scheduling async classpath seed load on the bot worker");
        threading.botWorker().submit(() -> {
            long start = System.currentTimeMillis();
            try {
                CraftDesynthLoader.loadAll();
                log.info("Async classpath seed load done in {}ms", System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("Async classpath seed load failed", e);
            }
        });
    }

    private static HikariDataSource openPool(Threading threading, File config) {
        log.info("Creating connection pool.");
        var data = config.database();
        return DataSourceCreator.create(PostgreSql.get())
                .configure(builder -> builder.host(data.host())
                        .port(data.port())
                        .user(data.user())
                        .password(data.password())
                        .database(data.database())
                        .applicationName("Lolorito"))
                .create()
                .withMinimumIdle(2)
                .withMaximumPoolSize(data.poolSize())
                .withThreadFactory(Threading.createThreadFactory(threading.hikariGroup()))
                .forSchema(data.schema())
                .build();
    }

    private static void runMigrations(HikariDataSource dataSource, String schema) throws IOException, SQLException {
        SqlUpdater.builder(dataSource, PostgreSql.get())
                .setReplacements(new QueryReplacement("lolorito", schema))
                .setVersionTable(schema + ".lolorito_version")
                .setSchemas(schema)
                .execute();
    }

    private static void installQueryConfiguration(HikariDataSource dataSource) {
        log.info("Configuring QueryConfiguration");
        var registry = new RowMapperRegistry().register(PostgresqlMapper.getDefaultMapper());
        var qc = QueryConfiguration.builder(dataSource)
                .setExceptionHandler(
                        err -> log.error(LogNotify.NOTIFY_ADMIN, "An error occurred during a database request", err))
                .setThrowExceptions(true)
                .setRowMapperRegistry(registry)
                .build();
        QueryConfiguration.setDefault(qc);
    }

    private static void upsertWorlds() {
        log.info("Updating worlds");
        for (Region region : Worlds.regions()) {
            for (World world : region.worlds()) {
                DataCenter dataCenter = world.dataCenter();
                query("""
                        INSERT INTO worlds(region_name, data_center, data_center_name, world, world_name)
                        VALUES (:region, :dc, :dc_name, :world, :world_name)
                        ON CONFLICT (data_center, world)
                            DO UPDATE SET region_name = excluded.region_name,
                                          data_center_name = excluded.data_center_name,
                                          world_name = excluded.world_name
                        """)
                        .single(call().bind("region", region.name())
                                .bind("dc", dataCenter.id())
                                .bind("dc_name", dataCenter.name())
                                .bind("world", world.id())
                                .bind("world_name", world.name()))
                        .insert();
            }
        }
        log.info("Updated worlds");
    }
}
