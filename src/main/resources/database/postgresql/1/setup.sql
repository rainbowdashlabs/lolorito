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
    INSERT INTO lolorito.listings_updated as l (item, world, updated)
    VALUES (new.item, new.world, NOW())
    ON CONFLICT(item,world) DO UPDATE SET updated = now();
    RETURN new;
END;
$BODY$;

CREATE OR REPLACE FUNCTION lolorito.sales_updated()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$BODY$
BEGIN
    INSERT INTO lolorito.sales_updated as l (item, world, updated)
    VALUES (new.item, new.world, NOW())
    ON CONFLICT(item,world) DO UPDATE SET updated = now();
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
    day   DATE    NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
    count INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT listings_viewed_pk
        PRIMARY KEY (world, item, day)
);
