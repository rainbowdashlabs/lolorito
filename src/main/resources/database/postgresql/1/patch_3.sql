CREATE OR REPLACE VIEW lolorito.world_item_sales AS
    SELECT
        world,
        item,
        hq,
        sum(s.quantity)                                        AS quantity,
        round(avg(s.unit_price) FILTER ( WHERE recent <= 10 )) AS avg_sales,
        min(s.unit_price) FILTER ( WHERE recent <= 10 )        AS min_sales,
        max(s.unit_price) FILTER ( WHERE recent <= 10 )        AS max_sales
    FROM
        (
            SELECT *,
                   row_number() OVER (PARTITION BY world, item, hq ORDER BY sold DESC) AS recent
            FROM
                lolorito.sales
        ) s
    WHERE s.sold > now() - INTERVAL '7 DAYS'
    GROUP BY world, item, hq;

CREATE OR REPLACE VIEW lolorito.world_item_popularity AS
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
                wis.world,
                wis.hq,
                wis.item,
                round(wis.quantity / ws.max_sales::NUMERIC * 100, 4)            AS market_volume,
                coalesce(round(wiv.views / wv.max_viewed::NUMERIC * 100, 4), 0) AS interest,
                wis.quantity                                                    AS sales,
                wiv.views,
                wil.min_price,
                wil.avg_price,
                wil.listings,
                wil.updated,
                wis.avg_sales,
                wis.min_sales,
                wis.max_sales
            FROM
                lolorito.world_item_sales wis
                    LEFT JOIN lolorito.world_sales ws
                    ON wis.world = ws.world
                    LEFT JOIN lolorito.world_views wv
                    ON wv.world = wis.world
                    LEFT JOIN lolorito.world_item_views wiv
                    ON wis.world = wiv.world AND wis.item = wiv.item
                    LEFT JOIN lolorito.world_item_listings wil
                    ON wis.world = wil.world AND wis.item = wil.item AND wis.hq = wil.hq
        ) pre;

ALTER TABLE lolorito.offer_filter
    RENAME COLUMN min_unit_price TO unit_price;

ALTER TABLE lolorito.offer_filter
    RENAME COLUMN min_profit_percentage TO factor;

ALTER TABLE lolorito.offer_filter
    RENAME COLUMN min_refresh_hours TO refresh_hours;

ALTER TABLE lolorito.offer_filter
    RENAME COLUMN min_popularity TO popularity;

ALTER TABLE lolorito.offer_filter
    RENAME COLUMN min_market_volume TO market_volume;

ALTER TABLE lolorito.offer_filter
    RENAME COLUMN min_interest TO interest;

ALTER TABLE lolorito.offer_filter
    RENAME COLUMN min_sales TO sales;

ALTER TABLE lolorito.offer_filter
    RENAME COLUMN min_views TO views;

ALTER TABLE lolorito.offer_filter
    RENAME COLUMN min_profit TO profit;

CREATE INDEX sales_world_item_hq_index
    ON lolorito.sales (world, item, hq);

CREATE INDEX listings_world_item_hq_index
    ON lolorito.listings (world, item, hq);
