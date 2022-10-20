DROP VIEW lolorito.world_item_popularity;
DROP VIEW lolorito.world_item_listings;
DROP VIEW lolorito.world_sales;
DROP VIEW lolorito.world_views;
DROP VIEW lolorito.world_item_sales;
DROP VIEW lolorito.world_item_views;

CREATE MATERIALIZED VIEW lolorito.world_item_views AS
SELECT world,
       item,
       SUM(count) OVER (PARTITION BY world, item) AS views
FROM lolorito.listings_viewed v
WHERE v.day > NOW() - INTERVAL '7 DAYS'
GROUP BY world, item, count;

CREATE INDEX world_item_views_world_item_index
    ON lolorito.world_item_views (world, item);

CREATE INDEX world_item_views_item_index
    ON lolorito.world_item_views (item);

CREATE MATERIALIZED VIEW lolorito.world_item_listings AS
WITH ranked_listings AS (SELECT RANK() OVER (PARTITION BY world,item, hq ORDER BY unit_price) AS unit_rank,
                                COUNT(1) OVER (PARTITION BY world,item, hq)                   AS listings,
                                *
                         FROM lolorito.listings),
     computed_listings AS (SELECT l.world,
                                  l.item,
                                  hq,
                                  MIN(unit_price)        AS min_price,
                                  ROUND(AVG(unit_price)) AS avg_price,
                                  MAX(listings)          AS listings
                           FROM ranked_listings l
                           WHERE unit_rank <= 5
                           GROUP BY l.world, l.item, hq
                           ORDER BY world, item)
SELECT l.world, l.item, hq, min_price, avg_price, listings, u.updated
FROM computed_listings l
         LEFT JOIN lolorito.listings_updated u ON l.world = u.world AND l.item = u.item;

CREATE INDEX world_item_listings_world_item_hq_index
    ON lolorito.world_item_listings (world, item, hq);

CREATE INDEX world_item_listings_world_item_index
    ON lolorito.world_item_listings (world, item);

CREATE INDEX world_item_listings_item_index
    ON lolorito.world_item_listings (item);

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
      FROM lolorito.sales) s
WHERE s.sold > NOW() - INTERVAL '7 DAYS'
GROUP BY world, item, hq;

CREATE INDEX world_item_sales_world_item_hq_index
    ON lolorito.world_item_sales (world, item, hq);

CREATE INDEX world_item_sales_world_item_index
    ON lolorito.world_item_sales (world, item);

CREATE INDEX world_item_sales_item_index
    ON lolorito.world_item_sales (item);

CREATE MATERIALIZED VIEW lolorito.world_views AS
SELECT world,
       MAX(v.views) AS max_viewed,
       SUM(v.views) AS total_views
FROM lolorito.world_item_views v
GROUP BY world;

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
