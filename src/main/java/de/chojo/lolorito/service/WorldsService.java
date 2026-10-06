/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Singleton;
import de.chojo.universalis.worlds.DataCenter;
import de.chojo.universalis.worlds.Region;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Static enumeration of Universalis' region / DC / world hierarchy for the
 * SPA's WorldSelector. No DB — the shape is baked into the Universalis
 * library and doesn't change between deploys.
 */
@Singleton
public class WorldsService {

    public List<RegionDto> hierarchy() {
        var out = new ArrayList<RegionDto>();
        for (Region region : Worlds.regions()) {
            var byDc = new LinkedHashMap<Integer, DataCenterAccum>();
            for (World w : region.worlds()) {
                DataCenter dc = w.dataCenter();
                if (dc == null) continue;
                byDc.computeIfAbsent(dc.id(), id -> new DataCenterAccum(dc.name()))
                        .worlds
                        .add(new WorldDto(w.id(), w.name()));
            }
            var dcs = byDc.entrySet().stream()
                    .map(e -> new DataCenterDto(e.getKey(), e.getValue().name, e.getValue().worlds))
                    .toList();
            out.add(new RegionDto(region.name(), dcs));
        }
        return out;
    }

    private static final class DataCenterAccum {
        final String name;
        final List<WorldDto> worlds = new ArrayList<>();

        DataCenterAccum(String name) {
            this.name = name;
        }
    }

    public record RegionDto(String name, List<DataCenterDto> dataCenters) {}

    public record DataCenterDto(int id, String name, List<WorldDto> worlds) {}

    public record WorldDto(int id, String name) {}
}
