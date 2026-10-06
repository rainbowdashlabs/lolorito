/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.zaxxer.hikari.HikariDataSource;
import de.chojo.sadu.core.configuration.DatabaseConfig;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.postgresql.mapper.PostgresqlMapper;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Base class for repository integration tests — one Postgres container per
 * test <em>class</em>, each in its own schema so parallel classes never
 * step on each other. Ported straight from Ember's {@code
 * RepositoryTestBase}, trimmed to lolorito's smaller repo fleet.
 *
 * <p>Subclasses get a live {@link DataSource}, the current schema name, and
 * a {@link QueryConfiguration} already set as sadu's default. Wire up the
 * concrete repositories yourself — this base intentionally does not carry
 * the whole DAO graph so each suite only pays for what it uses.
 */
@Tag("database")
@Testcontainers
public abstract class RepositoryTestBase {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("lolorito_test")
            .withUsername("test")
            .withPassword("test");

    private static final AtomicInteger SCHEMA_COUNTER = new AtomicInteger();
    protected static DataSource dataSource;
    protected static HikariDataSource pool;
    protected static String schemaName;

    @BeforeAll
    static void setupDatabase() throws Exception {
        // Each subclass grabs a fresh schema so parallel test classes don't collide.
        schemaName = "lolorito_t" + SCHEMA_COUNTER.incrementAndGet();

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

        pool = DataSourceCreator.create(PostgreSql.get())
                .configure(config ->
                        config.withConfig(dbConfig).currentSchema(schemaName).applicationName("LoloritoTest"))
                .create()
                .withMaximumPoolSize(4)
                .build();
        dataSource = pool;

        // sadu-updater refuses to run without the schema existing.
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
    }

    @AfterAll
    static void teardownDatabase() {
        if (pool != null) pool.close();
    }

    /**
     * Seed the {@code worlds} row that most repositories join against.
     * Idempotent — {@code ON CONFLICT DO NOTHING}, so repeated calls are
     * fine.
     */
    protected static void insertWorld(int worldId, String worldName, int dataCenterId, String dcName, String region) {
        query("""
                INSERT INTO worlds(region_name, data_center, data_center_name, world, world_name)
                VALUES (:region, :dc, :dc_name, :world, :world_name)
                ON CONFLICT (data_center, world) DO NOTHING
                """)
                .single(call().bind("region", region)
                        .bind("dc", dataCenterId)
                        .bind("dc_name", dcName)
                        .bind("world", worldId)
                        .bind("world_name", worldName))
                .insert();
    }
}
