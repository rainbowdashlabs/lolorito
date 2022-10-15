CREATE TABLE lolorito.listings
(
    world       INTEGER   NOT NULL,
    item        INTEGER   NOT NULL,
    hq          BOOLEAN   NOT NULL,
    review_time TIMESTAMP NOT NULL,
    unit_price  INTEGER   NOT NULL,
    quantity    INTEGER   NOT NULL,
    total       INTEGER   NOT NULL
);

CREATE INDEX listings_world_index
    ON lolorito.listings (world);

CREATE INDEX listings_world_item_index
    ON lolorito.listings (world, item);

CREATE TABLE lolorito.sales
(
    world      INTEGER   NOT NULL,
    item       INTEGER   NOT NULL,
    hq         BOOLEAN   NOT NULL,
    sold       TIMESTAMP NOT NULL,
    unit_price INTEGER   NOT NULL,
    quantity   INTEGER   NOT NULL,
    total      INTEGER   NOT NULL
);

CREATE INDEX sales_timestamp_index
    ON lolorito.sales (sold);

CREATE INDEX sales_world_index
    ON lolorito.sales (world);

CREATE INDEX sales_world_item_index
    ON lolorito.sales (world, item);

CREATE INDEX sales_world_item_timestamp_index
    ON lolorito.sales (world, item, sold);

CREATE TABLE lolorito.listings_updated
(
    world   INTEGER   NOT NULL,
    item    INTEGER   NOT NULL,
    updated TIMESTAMP NOT NULL
);

CREATE INDEX listings_updated_world_index
    ON lolorito.listings_updated (world);

CREATE UNIQUE INDEX listings_updated_world_item_index
    ON lolorito.listings_updated (world, item);

CREATE TABLE lolorito.sales_updated
(
    world   INTEGER   NOT NULL,
    item    INTEGER   NOT NULL,
    updated TIMESTAMP NOT NULL
);

CREATE INDEX sales_updated_world_index
    ON lolorito.sales_updated (world);

CREATE UNIQUE INDEX sales_updated_world_item_index
    ON lolorito.sales_updated (world, item);

CREATE OR REPLACE FUNCTION lolorito.listings_updated()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$BODY$
BEGIN
    INSERT INTO lolorito.listings_updated AS l (item, world, updated)
    VALUES (new.item, new.world, NOW())
    ON CONFLICT(item,world) DO UPDATE SET updated = NOW();
    RETURN new;
END;
$BODY$;

CREATE OR REPLACE FUNCTION lolorito.sales_updated()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$BODY$
BEGIN
    INSERT INTO lolorito.sales_updated AS l (item, world, updated)
    VALUES (new.item, new.world, NOW())
    ON CONFLICT(item,world) DO UPDATE SET updated = NOW();
    RETURN new;
END;
$BODY$;

CREATE OR REPLACE TRIGGER listings_insert
    AFTER INSERT
    ON lolorito.listings
    FOR EACH ROW
EXECUTE FUNCTION lolorito.listings_updated();

CREATE OR REPLACE TRIGGER listings_update
    AFTER UPDATE
    ON lolorito.listings
    FOR EACH ROW
EXECUTE FUNCTION lolorito.listings_updated();

CREATE OR REPLACE TRIGGER sales_insert
    AFTER INSERT
    ON lolorito.sales
    FOR EACH ROW
EXECUTE FUNCTION lolorito.sales_updated();

CREATE OR REPLACE TRIGGER sales_update
    AFTER UPDATE
    ON lolorito.sales
    FOR EACH ROW
EXECUTE FUNCTION lolorito.sales_updated();

CREATE TABLE lolorito.listings_viewed
(
    world INTEGER NOT NULL,
    item  INTEGER NOT NULL,
    day   DATE    NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    count INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT listings_viewed_pk
        PRIMARY KEY (world, item, day)
);

CREATE OR REPLACE VIEW lolorito.world_item_views AS
SELECT world,
       item,
       SUM(count) OVER (PARTITION BY world, item) AS views
FROM lolorito.listings_viewed v
WHERE v.day > NOW() - INTERVAL '7 DAYS'
GROUP BY world, item, count;

CREATE OR REPLACE VIEW lolorito.world_views AS
SELECT world,
       MAX(v.views) AS max_viewed,
       SUM(v.views) AS total_views
FROM lolorito.world_item_views v
GROUP BY world;

CREATE OR REPLACE VIEW lolorito.world_item_sales AS
SELECT world,
       item,
       hq,
       SUM(s.quantity) AS quantity
FROM lolorito.sales s
WHERE s.sold > NOW() - INTERVAL '7 DAYS'
GROUP BY world, item, hq;

CREATE OR REPLACE VIEW lolorito.world_sales AS
SELECT world,
       SUM(quantity) AS total_sales,
       MAX(quantity) AS max_sales
FROM lolorito.world_item_sales s
GROUP BY world;

CREATE OR REPLACE VIEW lolorito.world_item_popularity AS
SELECT world,
       item,
       hq,
       market_volume,
       interest,
       ROUND((market_volume + interest) / 2, 4) AS popularity,
       sales,
       views
FROM (SELECT wis.world,
             wis.hq,
             wis.item,
             ROUND(wis.quantity / ws.max_sales::NUMERIC * 100, 4)            AS market_volume,
             COALESCE(ROUND(wiv.views / wv.max_viewed::NUMERIC * 100, 4), 0) AS interest,
             wis.quantity as sales,
             wiv.views
      FROM lolorito.world_item_sales wis
               LEFT JOIN lolorito.world_sales ws ON wis.world = ws.world
               LEFT JOIN lolorito.world_views wv ON wv.world = wis.world
               LEFT JOIN lolorito.world_item_views wiv ON wis.world = wiv.world AND wis.item = wiv.item) pre;
