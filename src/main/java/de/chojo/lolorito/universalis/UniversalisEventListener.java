/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.universalis;

import de.chojo.lolorito.repository.Listings;
import de.chojo.lolorito.repository.Sales;
import de.chojo.lolorito.service.ListingDiffer;
import de.chojo.lolorito.service.ResidualRecorder;
import de.chojo.universalis.events.listings.impl.ListingAddEvent;
import de.chojo.universalis.events.sales.impl.SalesAddEvent;
import de.chojo.universalis.listener.ListenerAdapter;
import org.slf4j.Logger;

import java.time.Instant;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Ingest bridge for the Universalis websocket. The library's own
 * dispatcher wraps our handlers with a {@code throws Exception} contract
 * — anything we let escape gets forwarded to the WS error path, which
 * only logs at {@code error} level via a marker most log configs won't
 * surface. Wrap each callback in a try/catch that logs loudly so the
 * next time an ingest bug hits (schema drift, JSON mapping change, DB
 * migration mismatch) we actually notice.
 */
public class UniversalisEventListener extends ListenerAdapter {
    private static final Logger log = getLogger(UniversalisEventListener.class);

    private final Listings listings;
    private final Sales sales;
    private final ResidualRecorder residualRecorder;
    private final ListingDiffer listingDiffer;

    public UniversalisEventListener(
            Listings listings, Sales sales, ResidualRecorder residualRecorder, ListingDiffer listingDiffer) {
        this.listings = listings;
        this.sales = sales;
        this.residualRecorder = residualRecorder;
        this.listingDiffer = listingDiffer;
    }

    @Override
    public void onListingAdd(ListingAddEvent event) {
        try {
            listings.clearListings(event.item(), event.world());
            listings.addListings(event.item(), event.world(), event.listings());
        } catch (Exception e) {
            log.warn(
                    "Failed to ingest listings for item={} world={}: {}",
                    event.item().id(),
                    event.world().id(),
                    e.getMessage(),
                    e);
        }
        // Episode diffing is separate from the raw-listings mirror: a differ
        // bug must never cost us the price surface, and vice versa.
        try {
            listingDiffer.onSnapshot(event.world(), event.item(), event.listings(), Instant.now());
        } catch (Exception e) {
            log.warn(
                    "Listing diff failed for item={} world={}: {}",
                    event.item().id(),
                    event.world().id(),
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public void onSalesAdd(SalesAddEvent event) {
        try {
            sales.addSales(event.world(), event.item(), event.sales());
            residualRecorder.record(event.world(), event.item().id(), event.sales());
        } catch (Exception e) {
            log.warn(
                    "Failed to ingest sales for item={} world={}: {}",
                    event.item().id(),
                    event.world().id(),
                    e.getMessage(),
                    e);
        }
        try {
            listingDiffer.onSales(event.world(), event.item().id(), event.sales());
        } catch (Exception e) {
            log.warn(
                    "Episode sale reclassify failed for item={} world={}: {}",
                    event.item().id(),
                    event.world().id(),
                    e.getMessage(),
                    e);
        }
    }
}
