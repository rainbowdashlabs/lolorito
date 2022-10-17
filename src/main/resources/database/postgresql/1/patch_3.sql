CREATE OR REPLACE VIEW lolorito.world_item_sales AS
SELECT world,
       item,
       hq,
       SUM(s.quantity)                                        AS quantity,
       ROUND(AVG(s.unit_price) FILTER ( WHERE recent <= 10 )) AS avg_sales,
       MIN(s.unit_price) FILTER ( WHERE recent <= 10 )        AS min_sales,
       MAX(s.unit_price) FILTER ( WHERE recent <= 10 )        AS max_sales
FROM (SELECT *,
             ROW_NUMBER() OVER (PARTITION BY world, item, hq ORDER BY sold DESC) AS recent
      FROM lolorito.sales) s
WHERE s.sold > NOW() - INTERVAL '7 DAYS'
GROUP BY world, item, hq;

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
       updated,
       avg_sales,
       min_sales,
       max_sales
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
             wil.updated,
             wis.avg_sales,
             wis.min_sales,
             wis.max_sales
      FROM lolorito.world_item_sales wis
               LEFT JOIN lolorito.world_sales ws ON wis.world = ws.world
               LEFT JOIN lolorito.world_views wv ON wv.world = wis.world
               LEFT JOIN lolorito.world_item_views wiv ON wis.world = wiv.world AND wis.item = wiv.item
               LEFT JOIN lolorito.world_item_listings wil
                         ON wis.world = wil.world AND wis.item = wil.item AND wis.hq = wil.hq) pre;

alter table lolorito.offer_filter
    rename column min_unit_price to unit_price;

alter table lolorito.offer_filter
    rename column min_profit_percentage to factor;

alter table lolorito.offer_filter
    rename column min_refresh_hours to refresh_hours;

alter table lolorito.offer_filter
    rename column min_popularity to popularity;

alter table lolorito.offer_filter
    rename column min_market_volume to market_volume;

alter table lolorito.offer_filter
    rename column min_interest to interest;

alter table lolorito.offer_filter
    rename column min_sales to sales;

alter table lolorito.offer_filter
    rename column min_views to views;

alter table lolorito.offer_filter
    rename column min_profit to profit;

CREATE INDEX sales_world_item_hq_index
    ON lolorito.sales (world, item, hq);

CREATE INDEX listings_world_item_hq_index
    ON lolorito.listings (world, item, hq);
