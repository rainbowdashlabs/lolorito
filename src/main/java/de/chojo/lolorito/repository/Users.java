/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.sql.DataSource;

/**
 * SQL access for user rows. The {@code users} table is currently a shell —
 * every persistent user attribute lives on adjacent tables (offer_filter,
 * session) keyed by {@code discord_user_id}. Kept as a repo so that if we
 * grow a real {@code users} row later, callers already thread through here.
 */
@Singleton
public class Users {
    @Inject
    public Users(DataSource dataSource) {
        // sadu 2 uses the default QueryConfiguration; retained for callers.
    }
}
