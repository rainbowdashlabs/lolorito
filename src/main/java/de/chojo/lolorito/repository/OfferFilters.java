/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.OfferFilterRow;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Read/write for the {@code offer_filter} table. Consumed by both the SPA
 * and the Discord {@code /offers filter} command through
 * {@link de.chojo.lolorito.service.FilterService}.
 */
@Singleton
public class OfferFilters {
    @Inject
    public OfferFilters(DataSource dataSource) {
        // sadu 2 uses the default QueryConfiguration
    }

    private static OfferFilterRow readRow(Row row) throws SQLException {
        return new OfferFilterRow(
                row.getInt("world"),
                row.getInt("offer_limit"),
                row.getInt("unit_price"),
                row.getDouble("factor"),
                row.getInt("refresh_hours"),
                row.getDouble("popularity"),
                row.getDouble("market_volume"),
                row.getDouble("interest"),
                row.getInt("sales"),
                row.getInt("views"),
                row.getInt("profit"),
                row.getInt("effective_profit"),
                row.getString("target"),
                row.getInt("budget"),
                row.getInt("inventory_slots"));
    }

    public Optional<OfferFilterRow> find(long userId) {
        return query("SELECT * FROM offer_filter WHERE user_id = :user_id")
                .single(call().bind("user_id", userId))
                .map(OfferFilters::readRow)
                .first();
    }

    public void upsert(long userId, OfferFilterRow filter) {
        query("""
                INSERT INTO offer_filter (user_id, world, offer_limit, unit_price, factor,
                                          refresh_hours, popularity, market_volume, interest,
                                          sales, views, profit, effective_profit, target,
                                          budget, inventory_slots)
                VALUES (:user_id, :world, :offer_limit, :unit_price, :factor,
                        :refresh_hours, :popularity, :market_volume, :interest,
                        :sales, :views, :profit, :effective_profit, :target,
                        :budget, :inventory_slots)
                ON CONFLICT (user_id) DO UPDATE SET
                    world            = excluded.world,
                    offer_limit      = excluded.offer_limit,
                    unit_price       = excluded.unit_price,
                    factor           = excluded.factor,
                    refresh_hours    = excluded.refresh_hours,
                    popularity       = excluded.popularity,
                    market_volume    = excluded.market_volume,
                    interest         = excluded.interest,
                    sales            = excluded.sales,
                    views            = excluded.views,
                    profit           = excluded.profit,
                    effective_profit = excluded.effective_profit,
                    target           = excluded.target,
                    budget           = excluded.budget,
                    inventory_slots  = excluded.inventory_slots
                """)
                .single(call().bind("user_id", userId)
                        .bind("world", filter.worldId())
                        .bind("offer_limit", filter.offerLimit())
                        .bind("unit_price", filter.unitPrice())
                        .bind("factor", filter.factor())
                        .bind("refresh_hours", filter.refreshHours())
                        .bind("popularity", filter.popularity())
                        .bind("market_volume", filter.marketVolume())
                        .bind("interest", filter.interest())
                        .bind("sales", filter.sales())
                        .bind("views", filter.views())
                        .bind("profit", filter.profit())
                        .bind("effective_profit", filter.effectiveProfit())
                        .bind("target", filter.target())
                        .bind("budget", filter.budget())
                        .bind("inventory_slots", filter.inventorySlots()))
                .insert();
    }

    /** @return true when a row was removed, false when none existed. */
    public boolean delete(long userId) {
        return query("DELETE FROM offer_filter WHERE user_id = :user_id")
                        .single(call().bind("user_id", userId))
                        .delete()
                        .rows()
                > 0;
    }
}
