DROP MATERIALIZED VIEW lolorito.world_item_sales CASCADE;
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
                lolorito.sales s
            WHERE s.sold > now() - INTERVAL '7 DAYS'
        ) s
    GROUP BY world, item, hq;

CREATE INDEX world_item_sales_world_item_hq_index
    ON lolorito.world_item_sales (world, item, hq);

CREATE INDEX world_item_sales_world_item_index
    ON lolorito.world_item_sales (world, item);

CREATE INDEX world_item_sales_item_index
    ON lolorito.world_item_sales (item);

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
