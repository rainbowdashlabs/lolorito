package de.chojo.lolorito.universalis;

import de.chojo.lolorito.dao.Listings;
import de.chojo.lolorito.dao.Sales;
import de.chojo.universalis.events.listings.impl.ListingAddEvent;
import de.chojo.universalis.events.sales.impl.SalesAddEvent;
import de.chojo.universalis.listener.ListenerAdapter;

public class UniversalisEventListener extends ListenerAdapter {
    private final Listings listings;
    private final Sales sales;

    public UniversalisEventListener(Listings listings, Sales sales) {
        this.listings = listings;
        this.sales = sales;
    }

    @Override
    public void onListingAdd(ListingAddEvent event) {
        listings.logListingsEvent(event);
    }

    @Override
    public void onSalesAdd(SalesAddEvent event) {
        sales.logSalesEvent(event);
    }
}
