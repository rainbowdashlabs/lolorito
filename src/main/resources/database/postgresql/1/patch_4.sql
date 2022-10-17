ALTER TABLE lolorito.offer_filter
    ALTER COLUMN factor TYPE NUMERIC USING factor::NUMERIC;

ALTER TABLE lolorito.offer_filter
    ALTER COLUMN factor SET DEFAULT 2;
