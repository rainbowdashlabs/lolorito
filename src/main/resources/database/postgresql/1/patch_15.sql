CREATE TEMP TABLE listings_viewed_recent AS
SELECT world, item, day, count
  FROM lolorito.listings_viewed
 WHERE day >= current_date - 8;

TRUNCATE lolorito.listings_viewed;

INSERT INTO lolorito.listings_viewed (world, item, day, count)
SELECT world, item, day, count
  FROM listings_viewed_recent;

DROP TABLE listings_viewed_recent;

DROP INDEX IF EXISTS lolorito.sales_world_item_index;
DROP INDEX IF EXISTS lolorito.sales_world_index;
DROP INDEX IF EXISTS lolorito.sales_world_item_hq_index;
