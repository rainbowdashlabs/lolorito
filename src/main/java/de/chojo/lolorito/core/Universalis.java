/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.core;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.repository.Listings;
import de.chojo.lolorito.repository.Sales;
import de.chojo.lolorito.service.ListingDiffer;
import de.chojo.lolorito.service.ResidualRecorder;
import de.chojo.lolorito.universalis.UniversalisEventListener;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.websocket.UniversalisWs;
import de.chojo.universalis.websocket.subscriber.Subscriptions;
import de.chojo.universalis.worlds.Region;
import de.chojo.universalis.worlds.Worlds;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Universalis websocket bootstrap. Guice constructs this as an eager
 * singleton at boot; the constructor opens the subscription for every
 * region listed in {@code universalis.regions} and wires the listener
 * that persists listings, sales, and residuals.
 */
@Singleton
public class Universalis {
    private static final Logger log = getLogger(Universalis.class);

    private final File config;

    @SuppressWarnings("unused") // held so the websocket stays alive
    private final UniversalisWs universalisWs;

    @Inject
    public Universalis(
            Threading threading,
            File config,
            NameSupplier itemNames,
            Listings listings,
            Sales sales,
            ResidualRecorder residualRecorder,
            ListingDiffer listingDiffer)
            throws IOException, InterruptedException {
        this.config = config;
        var listing = Subscriptions.listingAdd();
        var salesSub = Subscriptions.salesAdd();
        for (Region region : resolveRegions()) {
            listing.forRegion(region);
            salesSub.forRegion(region);
            log.info("Subscribing to Universalis region {}", region.name());
        }
        this.universalisWs = UniversalisWs.getDefault()
                .eventThreadPool(threading.websocketWorker())
                .itemNameSupplier(itemNames)
                .subscribe(listing)
                .subscribe(salesSub)
                .registerListener(new UniversalisEventListener(listings, sales, residualRecorder, listingDiffer))
                .build();
    }

    private static List<String> knownRegions() {
        return Worlds.regions().stream().map(Region::name).toList();
    }

    /**
     * Resolve the configured region names to {@link Region} objects; unknown names are logged and skipped.
     */
    private List<Region> resolveRegions() {
        var configured = config.universalis().regions();
        if (configured.isEmpty()) {
            log.warn("universalis.regions is empty — no Universalis subscription will be made");
            return List.of();
        }
        var out = new ArrayList<Region>();
        for (String name : configured) {
            var region = Worlds.regionByName(name);
            if (region == null) {
                log.warn("Unknown Universalis region '{}' — skipping. Known regions: {}", name, knownRegions());
                continue;
            }
            out.add(region);
        }
        return out;
    }
}
