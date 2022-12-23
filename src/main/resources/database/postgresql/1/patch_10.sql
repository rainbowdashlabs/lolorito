DROP MATERIALIZED VIEW lolorito.world_item_sales CASCADE;
CREATE MATERIALIZED VIEW lolorito.world_item_sales AS
SELECT world,
       item,
       hq,
       SUM(s.quantity)                                        AS quantity,
       ROUND(AVG(s.unit_price) FILTER ( WHERE recent <= 15 )) AS avg_sales,
       MIN(s.unit_price) FILTER ( WHERE recent <= 15 )        AS min_sales,
       MAX(s.unit_price) FILTER ( WHERE recent <= 15 )        AS max_sales
FROM (SELECT *,
             ROW_NUMBER() OVER (PARTITION BY world, item, hq ORDER BY sold DESC) AS recent
      FROM lolorito.sales s
      WHERE s.sold > NOW() - INTERVAL '7 DAYS') s
GROUP BY world, item, hq;

CREATE INDEX world_item_sales_world_item_hq_index
    ON lolorito.world_item_sales (world, item, hq);

CREATE INDEX world_item_sales_world_item_index
    ON lolorito.world_item_sales (world, item);

CREATE INDEX world_item_sales_item_index
    ON lolorito.world_item_sales (item);

CREATE MATERIALIZED VIEW lolorito.world_sales AS
SELECT world,
       SUM(quantity) AS total_sales,
       MAX(quantity) AS max_sales
FROM lolorito.world_item_sales s
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
               LEFT JOIN lolorito.world_views wv ON wv.world = wil.world
               LEFT JOIN lolorito.world_item_views wiv ON wil.world = wiv.world AND wil.item = wiv.item
               LEFT JOIN lolorito.world_item_sales wis
                         ON wil.world = wis.world AND wil.item = wis.item AND wil.hq = wis.hq) pre;

CREATE INDEX world_item_popularity_world_item_hq_index
    ON lolorito.world_item_popularity (world, item, hq);

CREATE INDEX world_item_popularity_world_item_index
    ON lolorito.world_item_popularity (world, item);

CREATE INDEX world_item_popularity_item_index
    ON lolorito.world_item_popularity (item);
