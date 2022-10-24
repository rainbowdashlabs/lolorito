package de.chojo.lolorito.service;

import de.chojo.lolorito.core.Threading;
import de.chojo.sadu.base.QueryFactory;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class DataRefreshWorker extends QueryFactory implements Runnable {
    private static final Logger log = getLogger(DataRefreshWorker.class);
    private final List<String> views = List.of(
            "world_item_listings",
            "world_item_sales",
            "world_item_views",
            "world_sales", // depends on world_item_sales
            "world_views", // depends on world_item_views
            "world_item_popularity", // depends on all other views
            "world_items" // depends on all other views
    );

    private DataRefreshWorker(DataSource dataSource) {
        super(dataSource);
    }

    public static DataRefreshWorker create(Threading threading, DataSource dataSource) {
        var worker = new DataRefreshWorker(dataSource);
        threading.botWorker().scheduleAtFixedRate(worker, 1, 5, TimeUnit.MINUTES);
        return worker;
    }

    @Override
    public void run() {
        log.debug("Refreshing views");
        for (String view : views) {
            long start = System.currentTimeMillis();
            builder().query("REFRESH MATERIALIZED VIEW %s", view)
                     .emptyParams()
                     .update()
                     .sendSync();
            log.debug("View {} refreshed. Took {} ms", view, System.currentTimeMillis() - start);
        }
    }
}
