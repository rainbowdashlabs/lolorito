/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.config.file.elements;

import dev.chojo.ocular.override.OverwritePrefix;

import java.util.Arrays;
import java.util.List;

/**
 * Which Universalis regions this instance mirrors. Every region name here
 * gets its own listing + sales subscription on boot. Empty list = don't
 * subscribe at all.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
@OverwritePrefix("UNIVERSALIS")
public class Universalis {

    /**
     * Region names to subscribe to — must match Universalis' region ids
     * ({@code Europe}, {@code North-America}, {@code Oceania},
     * {@code Japan}, {@code 中国}). Default: all five.
     */
    private List<String> regions = Arrays.asList("Europe", "North-America", "Oceania", "Japan", "中国");

    public List<String> regions() {
        return regions;
    }
}
