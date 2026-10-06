/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.entity.Basket;
import de.chojo.lolorito.entity.BasketVisibility;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasketServiceTest {

    private static Basket basket(BasketVisibility visibility) {
        return new Basket(UUID.randomUUID(), 1L, "tok", "b", visibility, Instant.now(), Instant.now(), List.of());
    }

    @Test
    void publicBasketReadableWithoutSession() {
        assertTrue(BasketService.canRead(basket(BasketVisibility.PUBLIC), false));
        assertTrue(BasketService.canRead(basket(BasketVisibility.PUBLIC), true));
    }

    @Test
    void authenticatedBasketRequiresSession() {
        assertFalse(BasketService.canRead(basket(BasketVisibility.AUTHENTICATED), false));
        assertTrue(BasketService.canRead(basket(BasketVisibility.AUTHENTICATED), true));
    }

    @Test
    void privateBasketNeverReadableViaShareLink() {
        assertFalse(BasketService.canRead(basket(BasketVisibility.PRIVATE), false));
        assertFalse(BasketService.canRead(basket(BasketVisibility.PRIVATE), true));
    }
}
