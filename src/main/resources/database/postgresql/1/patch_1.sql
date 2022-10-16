CREATE OR REPLACE VIEW lolorito.world_item_listings AS
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

CREATE OR REPLACE VIEW lolorito.world_item_popularity AS
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
       updated
FROM (SELECT wis.world,
             wis.hq,
             wis.item,
             ROUND(wis.quantity / ws.max_sales::NUMERIC * 100, 4)            AS market_volume,
             COALESCE(ROUND(wiv.views / wv.max_viewed::NUMERIC * 100, 4), 0) AS interest,
             wis.quantity                                                    AS sales,
             wiv.views,
             wil.min_price,
             wil.avg_price,
             wil.listings,
             wil.updated
      FROM lolorito.world_item_sales wis
               LEFT JOIN lolorito.world_sales ws ON wis.world = ws.world
               LEFT JOIN lolorito.world_views wv ON wv.world = wis.world
               LEFT JOIN lolorito.world_item_views wiv ON wis.world = wiv.world AND wis.item = wiv.item
               LEFT JOIN lolorito.world_item_listings wil
                         ON wis.world = wil.world AND wis.item = wil.item AND wis.hq = wil.hq) pre;

SELECT *
FROM lolorito.world_item_popularity
WHERE world = 66
ORDER BY popularity DESC;

CREATE TABLE lolorito.offer_filter
(
    user_id               BIGINT                              NOT NULL
        CONSTRAINT offer_filter_pk
            PRIMARY KEY,
    world                 INTEGER DEFAULT '-1'::INTEGER       NOT NULL,
    offer_limit           INTEGER DEFAULT 1000                NOT NULL,
    min_unit_price        INTEGER DEFAULT 1000                NOT NULL,
    min_profit_percentage INTEGER DEFAULT 10                  NOT NULL,
    min_refresh_hours     INTEGER DEFAULT 24                  NOT NULL,
    min_popularity        INTEGER DEFAULT 0                   NOT NULL,
    min_market_volume     INTEGER DEFAULT 0                   NOT NULL,
    min_interest          INTEGER DEFAULT 0                   NOT NULL,
    min_sales             INTEGER DEFAULT 0                   NOT NULL,
    min_views             INTEGER DEFAULT 0                   NOT NULL,
    target                TEXT    DEFAULT 'DATA_CENTER'::TEXT NOT NULL,
    min_profit            INTEGER DEFAULT 1000                NOT NULL
);
