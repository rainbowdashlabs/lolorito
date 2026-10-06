/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import de.chojo.lolorito.entity.Basket;
import de.chojo.lolorito.entity.BasketItem;
import de.chojo.lolorito.entity.BasketVisibility;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasketsIntegrationTest extends RepositoryTestBase {

    private Baskets repo;

    @BeforeEach
    void setUp() {
        repo = new Baskets();
        query("DELETE FROM basket").single(call()).delete();
    }

    private static BasketItem item(String key, int itemId, int qty, int buyPrice) {
        return new BasketItem(
                key, itemId, "Item " + itemId, false, 66, "Odin", qty, buyPrice, "resale", 1000.0, Instant.now(), 0);
    }

    private Basket newBasket(long owner, BasketVisibility vis, List<BasketItem> items) {
        var now = Instant.now();
        return new Basket(UUID.randomUUID(), owner, "tok-" + UUID.randomUUID(), "cart", vis, now, now, items);
    }

    @Test
    void insertAndReadRoundTripsAllFields() {
        var basket = newBasket(1L, BasketVisibility.PUBLIC, List.of(item("k1", 100, 3, 500), item("k2", 200, 1, 1000)));
        repo.insert(basket);
        var loaded = repo.findById(basket.id()).orElseThrow();
        assertEquals(basket.name(), loaded.name());
        assertEquals(BasketVisibility.PUBLIC, loaded.visibility());
        assertEquals(2, loaded.items().size());
        assertEquals(500, loaded.items().get(0).buyPrice());
        assertEquals(1000, loaded.items().get(1).buyPrice());
    }

    @Test
    void findByShareTokenMatchesInsert() {
        var basket = newBasket(2L, BasketVisibility.PRIVATE, List.of(item("k", 1, 1, 1)));
        repo.insert(basket);
        var loaded = repo.findByShareToken(basket.shareToken()).orElseThrow();
        assertEquals(basket.id(), loaded.id());
    }

    @Test
    void replaceItemsSwapsContents() {
        var basket = newBasket(3L, BasketVisibility.PRIVATE, List.of(item("old", 1, 1, 1)));
        repo.insert(basket);
        repo.replaceItems(basket.id(), List.of(item("new-a", 2, 5, 200), item("new-b", 3, 2, 300)), Instant.now());
        var loaded = repo.findById(basket.id()).orElseThrow();
        assertEquals(
                List.of("new-a", "new-b"),
                loaded.items().stream().map(BasketItem::key).toList());
    }

    @Test
    void updateMetaChangesNameAndVisibility() {
        var basket = newBasket(4L, BasketVisibility.PRIVATE, List.of());
        repo.insert(basket);
        assertTrue(repo.updateMeta(basket.id(), "renamed", BasketVisibility.AUTHENTICATED, Instant.now()));
        var loaded = repo.findById(basket.id()).orElseThrow();
        assertEquals("renamed", loaded.name());
        assertEquals(BasketVisibility.AUTHENTICATED, loaded.visibility());
    }

    @Test
    void updateMetaReturnsFalseWhenMissing() {
        assertFalse(repo.updateMeta(UUID.randomUUID(), "x", BasketVisibility.PRIVATE, Instant.now()));
    }

    @Test
    void deleteRemovesRowAndCascadesItems() {
        var basket = newBasket(5L, BasketVisibility.PRIVATE, List.of(item("k", 1, 1, 1)));
        repo.insert(basket);
        assertTrue(repo.delete(basket.id()));
        assertTrue(repo.findById(basket.id()).isEmpty());
        int items = query("SELECT count(*) AS n FROM basket_item WHERE basket_id = :id::uuid")
                .single(call().bind("id", basket.id(), StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("n"))
                .first()
                .orElse(0);
        assertEquals(0, items);
    }

    @Test
    void deleteReturnsFalseWhenMissing() {
        assertFalse(repo.delete(UUID.randomUUID()));
    }

    @Test
    void listByOwnerReturnsNewestFirst() throws InterruptedException {
        var a = newBasket(9L, BasketVisibility.PRIVATE, List.of());
        repo.insert(a);
        Thread.sleep(15);
        var b = newBasket(9L, BasketVisibility.PRIVATE, List.of());
        repo.insert(b);
        var list = repo.listByOwner(9L);
        assertEquals(2, list.size());
        assertEquals(b.id(), list.get(0).id());
        assertEquals(a.id(), list.get(1).id());
    }

    @Test
    void listByOwnerFiltersToOwner() {
        repo.insert(newBasket(10L, BasketVisibility.PRIVATE, List.of()));
        repo.insert(newBasket(11L, BasketVisibility.PRIVATE, List.of()));
        assertEquals(1, repo.listByOwner(10L).size());
    }
}
