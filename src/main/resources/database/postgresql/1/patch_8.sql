DROP MATERIALIZED VIEW lolorito.world_item_views CASCADE;

CREATE MATERIALIZED VIEW lolorito.world_item_views AS
SELECT world,
       item,
       SUM(count) AS views
FROM lolorito.listings_viewed v
WHERE v.day > NOW() - INTERVAL '7 DAYS'
GROUP BY world, item;

CREATE INDEX world_item_views_world_item_index
    ON lolorito.world_item_views (world, item);

CREATE INDEX world_item_views_item_index
    ON lolorito.world_item_views (item);

CREATE MATERIALIZED VIEW lolorito.world_views AS
SELECT world,
       MAX(v.views) AS max_viewed,
       SUM(v.views) AS total_views
FROM lolorito.world_item_views v
GROUP BY world;

CREATE MATERIALIZED VIEW lolorito.world_item_popularity AS
SELECT world,
       item,
       hq,
       market_volume,
       interest,
       ROUND((GREATEST(market_volume, interest) + ((market_volume + interest) / 2)) / 2, 4) AS popularity,
       sales,
       views,
       min_price,
       avg_price,
       listings,
       updated,
       avg_sales,
       min_sales,
       max_sales
FROM (SELECT wil.world,
             wil.hq,
             wil.item,
             ROUND(wis.quantity / ws.max_sales::NUMERIC * 100, 4)            AS market_volume,
             COALESCE(ROUND(wiv.views / wv.max_viewed::NUMERIC * 100, 4), 0) AS interest,
             wis.quantity                                                    AS sales,
             wiv.views,
             wil.min_price,
             wil.avg_price,
             wil.listings,
             wil.updated,
             COALESCE(wis.avg_sales, 0)                                      AS avg_sales,
             COALESCE(wis.min_sales, 0)                                      AS min_sales,
             COALESCE(wis.max_sales, 0)                                      AS max_sales
      FROM lolorito.world_item_listings wil
               LEFT JOIN lolorito.world_sales ws ON wil.world = ws.world
               LEFT JOIN lolorito.world_views wv ON wil.world = wv.world
               LEFT JOIN lolorito.world_item_views wiv ON wil.world = wiv.world AND wil.item = wiv.item
               LEFT JOIN lolorito.world_item_sales wis
                         ON wil.world = wis.world AND wil.item = wis.item AND wil.hq = wis.hq) pre;

CREATE INDEX world_item_popularity_world_item_hq_index
    ON lolorito.world_item_popularity (world, item, hq);

CREATE INDEX world_item_popularity_world_item_index
    ON lolorito.world_item_popularity (world, item);

CREATE INDEX world_item_popularity_item_index
    ON lolorito.world_item_popularity (item);

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
WHERE wip.world IS NOT NULL
  AND  l.item = 36213 AND l.world = 66;

CREATE INDEX world_items_world_item_hq_index
    ON lolorito.world_items (world, item, hq);

CREATE INDEX world_items_world_item_index
    ON lolorito.world_items (world, item);

CREATE INDEX world_items_item_index
    ON lolorito.world_items (item);
