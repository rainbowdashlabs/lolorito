DROP VIEW lolorito.world_item_popularity;
DROP VIEW lolorito.world_item_listings;
DROP VIEW lolorito.world_sales;
DROP VIEW lolorito.world_views;
DROP VIEW lolorito.world_item_sales;
DROP VIEW lolorito.world_item_views;

CREATE MATERIALIZED VIEW lolorito.world_item_views AS
    SELECT
        world,
        item,
        sum(count) OVER (PARTITION BY world, item) AS views
    FROM
        lolorito.listings_viewed v
    WHERE v.day > now() - INTERVAL '7 DAYS'
    GROUP BY world, item, count;

CREATE INDEX world_item_views_world_item_index
    ON lolorito.world_item_views (world, item);

CREATE INDEX world_item_views_item_index
    ON lolorito.world_item_views (item);

CREATE MATERIALIZED VIEW lolorito.world_item_listings AS
    WITH
        ranked_listings AS (
            SELECT
                rank() OVER (PARTITION BY world,item, hq ORDER BY unit_price) AS unit_rank,
                count(1) OVER (PARTITION BY world,item, hq)                   AS listings,
                *
            FROM
                lolorito.listings
                           ),
        computed_listings AS (
            SELECT
                l.world,
                l.item,
                hq,
                min(unit_price)        AS min_price,
                round(avg(unit_price)) AS avg_price,
                max(listings)          AS listings
            FROM
                ranked_listings l
            WHERE unit_rank <= 5
            GROUP BY l.world, l.item, hq
            ORDER BY world, item
                           )
    SELECT
        l.world,
        l.item,
        hq,
        min_price,
        avg_price,
        listings,
        u.updated
    FROM
        computed_listings l
            LEFT JOIN lolorito.listings_updated u
            ON l.world = u.world AND l.item = u.item;

CREATE INDEX world_item_listings_world_item_hq_index
    ON lolorito.world_item_listings (world, item, hq);

CREATE INDEX world_item_listings_world_item_index
    ON lolorito.world_item_listings (world, item);

CREATE INDEX world_item_listings_item_index
    ON lolorito.world_item_listings (item);

CREATE MATERIALIZED VIEW lolorito.world_item_sales AS
    SELECT
        world,
        item,
        hq,
        sum(s.quantity)                                        AS quantity,
        round(avg(s.unit_price) FILTER ( WHERE recent <= 15 )) AS avg_sales,
        min(s.unit_price) FILTER ( WHERE recent <= 15 )        AS min_sales,
        max(s.unit_price) FILTER ( WHERE recent <= 15 )        AS max_sales
    FROM
        (
            SELECT *,
                   row_number() OVER (PARTITION BY world, item, hq ORDER BY sold DESC) AS recent
            FROM
                lolorito.sales
        ) s
    WHERE s.sold > now() - INTERVAL '7 DAYS'
    GROUP BY world, item, hq;

CREATE INDEX world_item_sales_world_item_hq_index
    ON lolorito.world_item_sales (world, item, hq);

CREATE INDEX world_item_sales_world_item_index
    ON lolorito.world_item_sales (world, item);

CREATE INDEX world_item_sales_item_index
    ON lolorito.world_item_sales (item);

CREATE MATERIALIZED VIEW lolorito.world_views AS
    SELECT
        world,
        max(v.views) AS max_viewed,
        sum(v.views) AS total_views
    FROM
        lolorito.world_item_views v
    GROUP BY world;

CREATE MATERIALIZED VIEW lolorito.world_sales AS
    SELECT
        world,
        sum(quantity) AS total_sales,
        max(quantity) AS max_sales
    FROM
        lolorito.world_item_sales s
    GROUP BY world;

CREATE MATERIALIZED VIEW lolorito.world_item_popularity AS
    SELECT
        world,
        item,
        hq,
        market_volume,
        interest,
        round(( greatest(market_volume, interest) + ( ( market_volume + interest ) / 2 ) ) / 2, 4) AS popularity,
        sales,
        views,
        min_price,
        avg_price,
        listings,
        updated,
        avg_sales,
        min_sales,
        max_sales
    FROM
        (
            SELECT
                wil.world,
                wil.hq,
                wil.item,
                round(wis.quantity / ws.max_sales::NUMERIC * 100, 4)            AS market_volume,
                coalesce(round(wiv.views / wv.max_viewed::NUMERIC * 100, 4), 0) AS interest,
                wis.quantity                                                    AS sales,
                wiv.views,
                wil.min_price,
                wil.avg_price,
                wil.listings,
                wil.updated,
                coalesce(wis.avg_sales, 0)                                      AS avg_sales,
                coalesce(wis.min_sales, 0)                                      AS min_sales,
                coalesce(wis.max_sales, 0)                                      AS max_sales
            FROM
                lolorito.world_item_listings wil
                    LEFT JOIN lolorito.world_sales ws
                    ON wil.world = ws.world
                    LEFT JOIN lolorito.world_views wv
                    ON wv.world = wil.world
                    LEFT JOIN lolorito.world_item_views wiv
                    ON wil.world = wiv.world AND wil.item = wiv.item
                    LEFT JOIN lolorito.world_item_sales wis
                    ON wil.world = wis.world AND wil.item = wis.item AND wil.hq = wis.hq
        ) pre;

CREATE INDEX world_item_popularity_world_item_hq_index
    ON lolorito.world_item_popularity (world, item, hq);

CREATE INDEX world_item_popularity_world_item_index
    ON lolorito.world_item_popularity (world, item);

CREATE INDEX world_item_popularity_item_index
    ON lolorito.world_item_popularity (item);
