/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Postgres container per test JVM, shared by every database-backed test
 * base. Each test class still gets its own schema, so classes stay isolated;
 * starting a container per class instead made parallel runs flaky. The
 * container is stopped by Testcontainers' cleanup when the JVM exits.
 */
public final class SharedPostgres {

    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("lolorito_test")
            .withUsername("test")
            .withPassword("test");

    static {
        CONTAINER.start();
    }

    private SharedPostgres() {}

    public static PostgreSQLContainer container() {
        return CONTAINER;
    }
}
