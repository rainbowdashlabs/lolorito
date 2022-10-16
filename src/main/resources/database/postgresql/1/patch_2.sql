ALTER TABLE lolorito.offer_filter
    ALTER COLUMN min_popularity TYPE NUMERIC USING min_popularity::NUMERIC;

ALTER TABLE lolorito.offer_filter
    ALTER COLUMN min_market_volume TYPE NUMERIC USING min_market_volume::NUMERIC;

ALTER TABLE lolorito.offer_filter
    ALTER COLUMN min_interest TYPE NUMERIC USING min_interest::NUMERIC;
