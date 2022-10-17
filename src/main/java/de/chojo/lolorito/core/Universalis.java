package de.chojo.lolorito.core;

import de.chojo.lolorito.universalis.UniversalisEventListener;
import de.chojo.universalis.provider.items.Items;
import de.chojo.universalis.websocket.UniversalisWs;
import de.chojo.universalis.websocket.subscriber.Subscriptions;
import de.chojo.universalis.worlds.Worlds;

import java.io.IOException;

public class Universalis {
    private final Threading threading;
    private final Data data;
    private UniversalisWs universalisWs;

    private Universalis(Threading threading, Data data) {
        this.threading = threading;
        this.data = data;
    }

    public static Universalis create(Threading threading, Data data) throws IOException, InterruptedException {
        Universalis universalis = new Universalis(threading, data);
        universalis.init();
        return universalis;
    }

    public void init() throws IOException, InterruptedException {
        Items items = Items.create();
        universalisWs = UniversalisWs.getDefault()
                                     .eventThreadPool(threading.websocketWorker())
                                     .itemNameSupplier(items)
                                     .subscribe(Subscriptions.listingAdd().forRegion(Worlds.europe()))
                                     .subscribe(Subscriptions.salesAdd().forRegion(Worlds.europe()))
                                     //.subscribe(Subscriptions.listingRemove().forRegion(Worlds.europe()))
                                     .registerListener(new UniversalisEventListener(data.listings(), data.sales()))
                                     .build();
    }
}
