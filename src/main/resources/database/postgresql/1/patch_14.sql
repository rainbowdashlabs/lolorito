ALTER TABLE lolorito.alert_rule
    RENAME COLUMN threshold_price TO threshold;

ALTER TABLE lolorito.alert_rule
    DROP CONSTRAINT IF EXISTS alert_rule_trigger_kind_check,
    DROP CONSTRAINT IF EXISTS alert_rule_threshold_price_check;

ALTER TABLE lolorito.alert_rule
    ADD CONSTRAINT alert_rule_trigger_kind_check
        CHECK (trigger_kind IN ('price_below', 'price_above', 'sale_volume_spike', 'listing_count_drop')),
    ADD CONSTRAINT alert_rule_threshold_check
        CHECK (threshold >= 0);
