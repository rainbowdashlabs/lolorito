CREATE MATERIALIZED VIEW lolorito.world_items AS
SELECT l.world,
       l.item,
       l.hq,
       l.min_price,
       LEAST(l.min_price, COALESCE(wis.avg_sales, 0)) AS unit_price,
       popularity,
       market_volume,
       interest,
       sales,
       views,
       lu.updated
FROM lolorito.world_item_listings l
         LEFT JOIN lolorito.listings_updated lu
                   ON l.world = lu.world AND l.item = lu.item
         LEFT JOIN lolorito.world_item_popularity wip
                   ON l.world = wip.world AND l.item = wip.item AND l.hq = wip.hq
         LEFT JOIN lolorito.world_item_sales wis
                   ON l.world = wis.world AND l.item = wis.item AND l.hq = wis.hq
WHERE wip.world IS NOT NULL;

CREATE INDEX world_items_world_item_hq_index
    ON lolorito.world_items (world, item, hq);

CREATE INDEX world_items_world_item_index
    ON lolorito.world_items (world, item);

CREATE INDEX world_items_item_index
    ON lolorito.world_items (item);
