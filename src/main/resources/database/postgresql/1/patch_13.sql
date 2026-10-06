ALTER TABLE lolorito.offer_filter
    ADD budget INTEGER DEFAULT 0 NOT NULL,
    ADD inventory_slots INTEGER DEFAULT 0 NOT NULL;
