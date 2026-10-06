/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.config;

import de.chojo.lolorito.config.file.elements.Value;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValueTaxTest {

    @SuppressWarnings("SameParameterValue")
    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void defaultMbTaxAppliesWhenNoOverride() {
        var v = new Value();
        assertEquals(0.05, v.mbTaxFor(7), 1e-9, "Light DC without override falls back to the global 5 % rate");
    }

    @Test
    void overrideMapReturnsPerDcRate() {
        var v = new Value();
        setField(v, "mbTaxByDataCenter", Map.of(7, 0.07, 6, 0.03));
        assertEquals(0.07, v.mbTaxFor(7), 1e-9);
        assertEquals(0.03, v.mbTaxFor(6), 1e-9);
        assertEquals(0.05, v.mbTaxFor(99), 1e-9, "DC not in the map still uses the global fallback");
    }

    @Test
    void globalMbTaxHonoured() {
        var v = new Value();
        setField(v, "mbTax", 0.10);
        assertEquals(0.10, v.mbTaxFor(7), 1e-9);
    }

    @Test
    void overrideOfZeroIsRespected() {
        // A DC could legitimately drop tax to 0 (event); the override map is authoritative.
        var v = new Value();
        setField(v, "mbTaxByDataCenter", Map.of(7, 0.0));
        assertEquals(0.0, v.mbTaxFor(7), 1e-9);
    }
}
