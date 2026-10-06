-- Collapsed pre-release migration: former patches 12-32 squashed
-- into one file before their first commit (2026-07-04).

-- ── formerly patch_12.sql ──────────────────────────────────────────────

CREATE TABLE lolorito.session (
    id                    TEXT        NOT NULL
        CONSTRAINT session_pk PRIMARY KEY,
    discord_user_id       BIGINT      NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at            TIMESTAMPTZ NOT NULL,
    user_agent            TEXT,
    access_token_ct       BYTEA       NOT NULL,
    access_token_iv       BYTEA       NOT NULL,
    access_expires_at     TIMESTAMPTZ NOT NULL,
    refresh_token_ct      BYTEA       NOT NULL,
    refresh_token_iv      BYTEA       NOT NULL,
    membership_ok         BOOLEAN     NOT NULL DEFAULT TRUE,
    membership_checked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX session_expires_at_index
    ON lolorito.session (expires_at);

CREATE INDEX session_discord_user_id_index
    ON lolorito.session (discord_user_id);

CREATE INDEX session_bg_recheck_index
    ON lolorito.session (membership_checked_at)
    WHERE membership_ok = TRUE;

-- ── formerly patch_13.sql ──────────────────────────────────────────────

CREATE TABLE lolorito.market_model (
    item_id         INT            NOT NULL,
    world_id        INT            NOT NULL,
    hq              BOOLEAN        NOT NULL,

    -- Log-normal fit of sale price, weighted by recency.
    price_mu        NUMERIC(14, 4) NOT NULL,
    price_sigma     NUMERIC(14, 4) NOT NULL,

    -- Poisson sale-rate anchors as sales-per-hour at three relative-price
    -- levels. Downstream code log-linear-interpolates between them.
    lambda_p95      NUMERIC(14, 6) NOT NULL,
    lambda_p100     NUMERIC(14, 6) NOT NULL,
    lambda_p105     NUMERIC(14, 6) NOT NULL,

    -- Learned undercut rate (undercuts/hour). 0 until listing-history
    -- tracking lands and the fitter has data to work with.
    lambda_undercut NUMERIC(14, 6) NOT NULL DEFAULT 0,

    -- Fraction of current listing stack whose owner hasn't posted in
    -- ghostListingWindowDays. 0 until listing-history tracking lands.
    ghost_fraction  NUMERIC(4, 3)  NOT NULL DEFAULT 0,

    sample_count    INT            NOT NULL,
    sufficient      BOOLEAN        NOT NULL,
    fitted_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (item_id, world_id, hq)
);

CREATE INDEX market_model_fitted_at_index
    ON lolorito.market_model (fitted_at);

CREATE INDEX market_model_sufficient_index
    ON lolorito.market_model (world_id, item_id)
    WHERE sufficient = TRUE;

-- ── formerly patch_14.sql ──────────────────────────────────────────────

-- Recipes and desynth results — powers the item DAG that the value engine
-- and planner walk. Data is bulk-loaded from curated JSON in
-- resources/{recipe,desynth}/*.json on startup; the tables are just the
-- indexed shape those files live in at runtime.

CREATE TABLE lolorito.recipe (
    id              INT  NOT NULL
        CONSTRAINT recipe_pk PRIMARY KEY,
    product_item_id INT  NOT NULL,
    craft_class     TEXT NOT NULL,
    level           INT  NOT NULL,
    yield_qty       INT  NOT NULL DEFAULT 1
);

CREATE INDEX recipe_product_index
    ON lolorito.recipe (product_item_id);

CREATE TABLE lolorito.recipe_ingredient (
    recipe_id INT NOT NULL
        CONSTRAINT recipe_ingredient_recipe_fk REFERENCES lolorito.recipe (id) ON DELETE CASCADE,
    item_id   INT NOT NULL,
    quantity  INT NOT NULL,
    CONSTRAINT recipe_ingredient_pk PRIMARY KEY (recipe_id, item_id)
);

CREATE INDEX recipe_ingredient_item_index
    ON lolorito.recipe_ingredient (item_id);

CREATE TABLE lolorito.desynth_result (
    source_item_id    INT           NOT NULL,
    component_item_id INT           NOT NULL,
    avg_qty           NUMERIC(6, 3) NOT NULL,
    CONSTRAINT desynth_result_pk PRIMARY KEY (source_item_id, component_item_id)
);

CREATE INDEX desynth_result_component_index
    ON lolorito.desynth_result (component_item_id);

-- ── formerly patch_15.sql ──────────────────────────────────────────────

-- Post-hoc calibration residuals for the market model (§10.7). One row per
-- Universalis-observed sale: we compare the sale's unit price against the
-- market_model's mu-derived expected price at time of sale and store the
-- log ratio. The fitter reads windowed means of log_ratio per key and
-- applies them as an additive Bayesian shrinkage prior on mu the next
-- refit.
CREATE TABLE lolorito.market_model_residuals (
    item_id     INT           NOT NULL,
    world_id    INT           NOT NULL,
    hq          BOOLEAN       NOT NULL,
    observed_at TIMESTAMPTZ   NOT NULL,
    log_ratio   NUMERIC(9, 6) NOT NULL
);

CREATE INDEX market_model_residuals_key_idx
    ON lolorito.market_model_residuals (item_id, world_id, hq, observed_at DESC);

CREATE INDEX market_model_residuals_observed_at_idx
    ON lolorito.market_model_residuals (observed_at);

-- ── formerly patch_16.sql ──────────────────────────────────────────────

CREATE TABLE lolorito.basket (
    id            UUID        NOT NULL PRIMARY KEY,
    owner_user_id BIGINT      NOT NULL,
    share_token   TEXT        NOT NULL UNIQUE,
    name          TEXT        NOT NULL,
    -- 'private': only owner can read
    -- 'authenticated': any signed-in user with the share token
    -- 'public': anyone with the share token
    visibility    TEXT        NOT NULL DEFAULT 'private',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (visibility IN ('private', 'authenticated', 'public'))
);

CREATE INDEX basket_owner_idx ON lolorito.basket (owner_user_id, updated_at DESC);

CREATE TABLE lolorito.basket_item (
    basket_id         UUID             NOT NULL REFERENCES lolorito.basket (id) ON DELETE CASCADE,
    key               TEXT             NOT NULL,
    item_id           INT              NOT NULL,
    item_name         TEXT             NOT NULL,
    hq                BOOLEAN          NOT NULL,
    source_world_id   INT              NOT NULL,
    source_world_name TEXT             NOT NULL,
    quantity          INT              NOT NULL,
    buy_price         INT              NOT NULL,
    action            TEXT             NOT NULL,
    ev_per_hour       DOUBLE PRECISION NOT NULL,
    added_at          TIMESTAMPTZ      NOT NULL,
    position          INT              NOT NULL,
    PRIMARY KEY (basket_id, key)
);

-- ── formerly patch_17.sql ──────────────────────────────────────────────

CREATE TABLE lolorito.alert_rule
(
    id                uuid        NOT NULL PRIMARY KEY,
    user_id           bigint      NOT NULL,
    item_id           int         NOT NULL,
    -- Either world_id XOR data_center_id — enforced by the CHECK below.
    world_id          int         NULL,
    data_center_id    int         NULL,
    -- NULL means "either" — the scanner considers HQ and NQ listings both.
    hq                boolean     NULL,
    -- 'price_below' fires when the cheapest current listing sits at or under
    -- threshold_price; 'price_above' the opposite direction.
    trigger_kind      text        NOT NULL,
    threshold_price   int         NOT NULL,
    enabled           boolean     NOT NULL DEFAULT TRUE,
    -- Minimum minutes between two firings of the same rule.
    cooldown_minutes  int         NOT NULL DEFAULT 60,
    last_triggered_at timestamptz NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CHECK ((world_id IS NULL) <> (data_center_id IS NULL)),
    CHECK (trigger_kind IN ('price_below', 'price_above')),
    CHECK (threshold_price > 0),
    CHECK (cooldown_minutes >= 0)
);

CREATE INDEX alert_rule_user_idx ON lolorito.alert_rule (user_id, created_at DESC);
CREATE INDEX alert_rule_scanner_idx ON lolorito.alert_rule (item_id) WHERE enabled = TRUE;

-- ── formerly patch_18.sql ──────────────────────────────────────────────

CREATE TABLE lolorito.user_preferences
(
    discord_user_id bigint      NOT NULL PRIMARY KEY,
    locale          text        NOT NULL DEFAULT 'en',
    theme           text        NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CHECK (locale IN ('en', 'de', 'fr', 'ja')),
    CHECK (theme IS NULL OR theme IN ('dark', 'light', 'auto'))
);

-- ── formerly patch_19.sql ──────────────────────────────────────────────

-- Migrate the remaining naive TIMESTAMP columns to TIMESTAMPTZ. Everything
-- we write is already UTC via sadu's INSTANT_TIMESTAMP converter, so the
-- reinterpretation below matches how the reader already sees the data.
--
-- Postgres won't let us ALTER a column that a materialised view depends on,
-- so we drop the mv chain first and rebuild it after the type changes.

DROP MATERIALIZED VIEW IF EXISTS lolorito.world_items CASCADE;
DROP MATERIALIZED VIEW IF EXISTS lolorito.world_item_popularity CASCADE;
DROP MATERIALIZED VIEW IF EXISTS lolorito.world_sales CASCADE;
DROP MATERIALIZED VIEW IF EXISTS lolorito.world_views CASCADE;
DROP MATERIALIZED VIEW IF EXISTS lolorito.world_item_listings CASCADE;
DROP MATERIALIZED VIEW IF EXISTS lolorito.world_item_sales CASCADE;
DROP MATERIALIZED VIEW IF EXISTS lolorito.world_item_views CASCADE;

ALTER TABLE lolorito.listings ALTER COLUMN review_time TYPE timestamptz USING review_time AT TIME ZONE 'UTC';
ALTER TABLE lolorito.sales ALTER COLUMN sold TYPE timestamptz USING sold AT TIME ZONE 'UTC';
ALTER TABLE lolorito.listings_updated ALTER COLUMN updated TYPE timestamptz USING updated AT TIME ZONE 'UTC';
ALTER TABLE lolorito.sales_updated ALTER COLUMN updated TYPE timestamptz USING updated AT TIME ZONE 'UTC';

-- Rebuild the mview tower in dependency order. Definitions mirror the
-- shape settled on in patches 8/10/11.

CREATE MATERIALIZED VIEW lolorito.world_item_views AS
SELECT world,
       item,
       sum(count) AS views
  FROM lolorito.listings_viewed v
 WHERE v.day > now() - INTERVAL '7 DAYS'
 GROUP BY world, item;

CREATE INDEX world_item_views_world_item_index ON lolorito.world_item_views (world, item);
CREATE INDEX world_item_views_item_index       ON lolorito.world_item_views (item);

CREATE MATERIALIZED VIEW lolorito.world_item_listings AS
WITH ranked_listings AS (
    SELECT rank() OVER (PARTITION BY world, item, hq ORDER BY unit_price) AS unit_rank,
           count(1) OVER (PARTITION BY world, item, hq)                    AS listings,
           *
      FROM lolorito.listings
),
     computed_listings AS (
         SELECT l.world,
                l.item,
                hq,
                min(unit_price)        AS min_price,
                round(avg(unit_price)) AS avg_price,
                max(listings)          AS listings
           FROM ranked_listings l
          WHERE unit_rank <= 5
          GROUP BY l.world, l.item, hq
          ORDER BY world, item
     )
SELECT l.world,
       l.item,
       hq,
       min_price,
       avg_price,
       listings,
       u.updated
  FROM computed_listings l
       LEFT JOIN lolorito.listings_updated u ON l.world = u.world AND l.item = u.item;

CREATE INDEX world_item_listings_world_item_hq_index ON lolorito.world_item_listings (world, item, hq);
CREATE INDEX world_item_listings_world_item_index    ON lolorito.world_item_listings (world, item);
CREATE INDEX world_item_listings_item_index          ON lolorito.world_item_listings (item);

CREATE MATERIALIZED VIEW lolorito.world_item_sales AS
SELECT world,
       item,
       hq,
       sum(s.quantity)                                        AS quantity,
       round(avg(s.unit_price) FILTER ( WHERE recent <= 15 )) AS avg_sales,
       min(s.unit_price) FILTER ( WHERE recent <= 15 )        AS min_sales,
       max(s.unit_price) FILTER ( WHERE recent <= 15 )        AS max_sales
  FROM (
      SELECT *,
             row_number() OVER (PARTITION BY world, item, hq ORDER BY sold DESC) AS recent
        FROM lolorito.sales s
       WHERE s.sold > now() - INTERVAL '7 DAYS'
  ) s
 GROUP BY world, item, hq;

CREATE INDEX world_item_sales_world_item_hq_index ON lolorito.world_item_sales (world, item, hq);
CREATE INDEX world_item_sales_world_item_index    ON lolorito.world_item_sales (world, item);
CREATE INDEX world_item_sales_item_index          ON lolorito.world_item_sales (item);

CREATE MATERIALIZED VIEW lolorito.world_views AS
SELECT world,
       max(v.views) AS max_viewed,
       sum(v.views) AS total_views
  FROM lolorito.world_item_views v
 GROUP BY world;

CREATE MATERIALIZED VIEW lolorito.world_sales AS
SELECT world,
       sum(quantity) AS total_sales,
       max(quantity) AS max_sales
  FROM lolorito.world_item_sales s
 GROUP BY world;

CREATE MATERIALIZED VIEW lolorito.world_item_popularity AS
SELECT world,
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
  FROM (
      SELECT wil.world,
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
        FROM lolorito.world_item_listings wil
             LEFT JOIN lolorito.world_sales      ws  ON wil.world = ws.world
             LEFT JOIN lolorito.world_views      wv  ON wv.world = wil.world
             LEFT JOIN lolorito.world_item_views wiv ON wil.world = wiv.world AND wil.item = wiv.item
             LEFT JOIN lolorito.world_item_sales wis
                       ON wil.world = wis.world AND wil.item = wis.item AND wil.hq = wis.hq
  ) pre;

CREATE INDEX world_item_popularity_world_item_hq_index ON lolorito.world_item_popularity (world, item, hq);
CREATE INDEX world_item_popularity_world_item_index    ON lolorito.world_item_popularity (world, item);
CREATE INDEX world_item_popularity_item_index          ON lolorito.world_item_popularity (item);

CREATE MATERIALIZED VIEW lolorito.world_items AS
SELECT l.world,
       l.item,
       l.hq,
       l.min_price,
       least(l.min_price, coalesce(wis.avg_sales, 0)) AS unit_price,
       popularity,
       market_volume,
       interest,
       sales,
       views,
       lu.updated
  FROM lolorito.world_item_listings l
       LEFT JOIN lolorito.listings_updated       lu  ON l.world = lu.world AND l.item = lu.item
       LEFT JOIN lolorito.world_item_popularity  wip ON l.world = wip.world AND l.item = wip.item AND l.hq = wip.hq
       LEFT JOIN lolorito.world_item_sales       wis ON l.world = wis.world AND l.item = wis.item AND l.hq = wis.hq
 WHERE wip.world IS NOT NULL;

CREATE INDEX world_items_world_item_hq_index ON lolorito.world_items (world, item, hq);
CREATE INDEX world_items_world_item_index    ON lolorito.world_items (world, item);
CREATE INDEX world_items_item_index          ON lolorito.world_items (item);

-- ── formerly patch_20.sql ──────────────────────────────────────────────

CREATE TABLE lolorito.calibration_snapshot
(
    captured_at    timestamptz NOT NULL PRIMARY KEY,
    window_days    int         NOT NULL,
    sample_count   int         NOT NULL,
    log_ratio_mean numeric(9, 6) NOT NULL,
    log_ratio_sigma numeric(9, 6) NOT NULL
);

CREATE INDEX calibration_snapshot_captured_idx
    ON lolorito.calibration_snapshot (captured_at DESC);

-- Lightweight per-cycle performance metrics — one row per named metric,
-- captured whenever a worker completes a run.
CREATE TABLE lolorito.perf_metric
(
    captured_at timestamptz NOT NULL,
    metric      text        NOT NULL,
    value_ms    bigint      NOT NULL,
    detail      text        NULL
);

CREATE INDEX perf_metric_metric_captured_idx
    ON lolorito.perf_metric (metric, captured_at DESC);

-- ── formerly patch_21.sql ──────────────────────────────────────────────

-- Persist the last-used planner form per user so the UI restores it on
-- the next visit instead of resetting to config defaults. Free-form JSON
-- keeps the schema loose while the planner params are still evolving.
ALTER TABLE lolorito.user_preferences
    ADD COLUMN planner_params jsonb NULL;

-- ── formerly patch_22.sql ──────────────────────────────────────────────

-- Own-retainer sale journal (§new). Users log a completed sale so the
-- app can build a personal history separate from the ambient Universalis
-- feed. Kept as its own table (rather than piggybacking on `sales`) so
-- privacy is trivial — only the owner sees these rows.
CREATE TABLE lolorito.retainer_sale
(
    id              bigserial   NOT NULL PRIMARY KEY,
    discord_user_id bigint      NOT NULL,
    retainer_name   text        NULL,
    item_id         int         NOT NULL,
    world_id        int         NOT NULL,
    hq              boolean     NOT NULL,
    unit_price      bigint      NOT NULL CHECK (unit_price > 0),
    quantity        int         NOT NULL CHECK (quantity > 0),
    sold_at         timestamptz NOT NULL DEFAULT now(),
    note            text        NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX retainer_sale_owner_sold_idx
    ON lolorito.retainer_sale (discord_user_id, sold_at DESC);

CREATE INDEX retainer_sale_owner_item_idx
    ON lolorito.retainer_sale (discord_user_id, item_id);

-- ── formerly patch_23.sql ──────────────────────────────────────────────

-- Named planner presets. `user_preferences.planner_params` still stores the
-- caller's current working form (auto-saved after every /plan), but presets
-- let users park several named configurations (e.g. "quick flip",
-- "retainer overnight", "raid-week grinder") and swap between them.
CREATE TABLE lolorito.planner_preset
(
    id              uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    discord_user_id bigint      NOT NULL,
    name            text        NOT NULL,
    params          jsonb       NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (discord_user_id, name)
);

CREATE INDEX planner_preset_owner_idx
    ON lolorito.planner_preset (discord_user_id, updated_at DESC);

-- ── formerly patch_24.sql ──────────────────────────────────────────────

-- Per-user Lodestone character profile cache. Fetched via HTML scraping
-- against the FFXIV Lodestone (no public JSON API); refresh only on
-- explicit user click or when the row is missing/older than the TTL.
--
-- `profile` is a raw JSONB blob of extracted fields — the SPA renders
-- straight from it and the schema stays flexible while we tune selectors.
CREATE TABLE lolorito.character_profile
(
    discord_user_id bigint      NOT NULL PRIMARY KEY,
    lodestone_id    bigint      NOT NULL,
    profile         jsonb       NOT NULL,
    fetched_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX character_profile_lodestone_idx
    ON lolorito.character_profile (lodestone_id);

-- ── formerly patch_25.sql ──────────────────────────────────────────────

-- Retainer-attribution plumbing.
--
-- (a) Enrich the ambient listings feed with per-listing retainer + seller
--     data straight from Universalis. seller_id is a SHA-256 hash; opaque
--     but stable, so once we've matched one of a user's retainer names to
--     it we own every future listing that seller posts.
-- (b) Track the retainer names a user has declared. Multiple retainer
--     rows per user; the same retainer can only be declared once.
-- (c) Cache the resolved seller_id once we've mapped a user to it so
--     lookups don't have to grep every fresh listing.
ALTER TABLE lolorito.listings
    ADD COLUMN retainer_id   text NULL,
    ADD COLUMN retainer_name text NULL,
    ADD COLUMN seller_id     text NULL;

CREATE INDEX listings_seller_idx
    ON lolorito.listings (seller_id) WHERE seller_id IS NOT NULL;

CREATE INDEX listings_retainer_idx
    ON lolorito.listings (world, retainer_name)
    WHERE retainer_name IS NOT NULL;

CREATE TABLE lolorito.character_retainer
(
    discord_user_id bigint      NOT NULL,
    retainer_name   text        NOT NULL,
    world_id        int         NOT NULL,
    seller_id       text        NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (discord_user_id, world_id, retainer_name)
);

CREATE INDEX character_retainer_seller_idx
    ON lolorito.character_retainer (seller_id) WHERE seller_id IS NOT NULL;

-- ── formerly patch_26.sql ──────────────────────────────────────────────

-- The game no longer exposes the retainer's owner id, so Universalis has
-- stopped shipping the sellerId hash on listings. Attribution is now
-- retainer-name-only: users declare every retainer they own, and each
-- retainer needs to have at least one active listing before Universalis
-- observes it and it appears in ours.
--
-- Drop the columns and the composite indexes that pinned them. The
-- retainer-name index stays — it's the only match we have now.
DROP INDEX IF EXISTS lolorito.listings_seller_idx;
DROP INDEX IF EXISTS lolorito.character_retainer_seller_idx;

ALTER TABLE lolorito.listings DROP COLUMN IF EXISTS seller_id;
ALTER TABLE lolorito.character_retainer DROP COLUMN IF EXISTS seller_id;

-- ── formerly patch_27.sql ──────────────────────────────────────────────

-- Per-class skill levels for the current user. Two rows: one for the
-- crafter classes (carpenter … culinarian), one for desynth on the same
-- classes. Backing table for the Settings > Skill levels form and the
-- planner's "allow crafts" / desynth filters.
--
-- Kind values: 'craft' | 'desynth'. Class values are the canonical FFXIV
-- job names ('carpenter', 'blacksmith', 'armorer', 'goldsmith',
-- 'leatherworker', 'weaver', 'alchemist', 'culinarian'). Level 0 means
-- "not tracked" — the planner treats it as effectively unavailable.
CREATE TABLE IF NOT EXISTS lolorito.user_skill_level
(
    discord_user_id BIGINT      NOT NULL,
    kind            TEXT        NOT NULL,
    class_name      TEXT        NOT NULL,
    level           INT         NOT NULL CHECK (level >= 0 AND level <= 100),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (discord_user_id, kind, class_name)
);

CREATE INDEX IF NOT EXISTS user_skill_level_by_user_idx
    ON lolorito.user_skill_level (discord_user_id);

-- ── formerly patch_28.sql ──────────────────────────────────────────────

-- Per-user alert webhook URL — surfaced in Settings so users who want
-- alerts posted to their own webhook (Discord channel via webhook,
-- Slack, generic HTTP receiver, etc.) can point us at it directly
-- without relying on the global instance-wide alertWebhookUrl.
ALTER TABLE lolorito.user_preferences
    ADD COLUMN IF NOT EXISTS alert_webhook_url text NULL;

-- ── formerly patch_29.sql ──────────────────────────────────────────────

-- Desynth-side skill gating for the /desynth explorer. For every desynth
-- source we know about we now record which crafter class needs to
-- desynth it and at what level, so the explorer can hide candidates the
-- caller can't touch. Both columns are nullable: recipe-derived rows
-- fill them from the recipe's craft_class + level; Teamcraft-fallback
-- rows leave them null (we don't know from that source) and are treated
-- as "always allowed" by the filter.
ALTER TABLE lolorito.desynth_result
    ADD COLUMN IF NOT EXISTS desynth_class TEXT NULL,
    ADD COLUMN IF NOT EXISTS desynth_level INT  NULL;

-- ── formerly patch_30.sql ──────────────────────────────────────────────

-- Market-math groundwork: sales dedup, listing episodes, and model fixes.
--
-- (a) Sales dedup. Universalis websocket replays can re-deliver sale
--     batches; without a natural key every replay inflates sale rates,
--     weighted sample counts, and residuals. There is no upstream sale id,
--     so the full value tuple is the best available identity. Existing
--     duplicates are removed first (keep one arbitrary survivor).
-- (b) Listing episodes. The websocket delivers the FULL current listing
--     set per (world, item) — no diffs. The new ListingDiffer compares
--     each snapshot against the open episodes below and records listing
--     lifecycles: posted → still there → gone (sold or delisted). These
--     episodes are the primitive behind real undercut/ghost detection and
--     shelf-time calibration.
-- (c) Undercut events. One row whenever a fresh listing lands below the
--     previous floor of its (world, item, hq) — the measured basis for
--     market_model.lambda_undercut, which has been a 0.0 placeholder
--     since the table was created.
-- (d) market_model.pooled — true when the row was fitted from DC-pooled
--     sales because the key alone was too thin. Downstream can badge
--     these as lower-confidence.

DELETE FROM lolorito.sales a
 USING lolorito.sales b
 WHERE a.ctid < b.ctid
   AND a.world = b.world
   AND a.item = b.item
   AND a.hq = b.hq
   AND a.sold = b.sold
   AND a.unit_price = b.unit_price
   AND a.quantity = b.quantity;

CREATE UNIQUE INDEX sales_natural_key_idx
    ON lolorito.sales (world, item, hq, sold, unit_price, quantity);

CREATE TABLE lolorito.listing_episode (
    id          bigserial PRIMARY KEY,
    world       int       NOT NULL,
    item        int       NOT NULL,
    hq          boolean   NOT NULL,
    -- listingId when Universalis sends one (rare), otherwise
    -- retainer|hq|price|qty plus an occurrence suffix for identical stacks.
    identity    text      NOT NULL,
    unit_price  int       NOT NULL,
    quantity    int       NOT NULL,
    retainer_id text      NULL,
    first_seen  timestamp NOT NULL,
    last_seen   timestamp NOT NULL,
    ended_at    timestamp NULL,
    end_reason  text      NULL,
    CONSTRAINT listing_episode_end_reason_chk
        CHECK (end_reason IN ('SOLD', 'DELISTED') OR end_reason IS NULL),
    CONSTRAINT listing_episode_ended_consistent_chk
        CHECK ((ended_at IS NULL) = (end_reason IS NULL))
);

-- The differ matches snapshots against open episodes by identity.
CREATE UNIQUE INDEX listing_episode_open_identity_idx
    ON lolorito.listing_episode (world, item, identity)
    WHERE ended_at IS NULL;

-- Ghost detection walks the open stack per key.
CREATE INDEX listing_episode_key_open_idx
    ON lolorito.listing_episode (world, item, hq)
    WHERE ended_at IS NULL;

-- Shelf-time calibration + late sale reclassification walk recent endings.
CREATE INDEX listing_episode_key_ended_idx
    ON lolorito.listing_episode (world, item, hq, ended_at)
    WHERE ended_at IS NOT NULL;

CREATE TABLE lolorito.undercut_event (
    world     int       NOT NULL,
    item      int       NOT NULL,
    hq        boolean   NOT NULL,
    at        timestamp NOT NULL,
    new_price int       NOT NULL,
    old_floor int       NOT NULL
);

CREATE INDEX undercut_event_key_at_idx
    ON lolorito.undercut_event (world, item, hq, at);

ALTER TABLE lolorito.market_model
    ADD COLUMN pooled boolean NOT NULL DEFAULT FALSE;

-- ── formerly patch_31.sql ──────────────────────────────────────────────

-- Market-math follow-ups: weighted sample mass and pricing refinements.
--
-- (a) market_model.weighted_n — the recency-decayed sample mass the fit
--     actually used. Until now readers reconstructed PriceDistribution
--     with the raw sample_count in this slot, which is a different
--     number; persisting it closes the round-trip trap. NULL for rows
--     fitted before this patch — readers fall back to sample_count.
-- (The per-user craft HQ-chance column that briefly lived here was
-- removed pre-release: craft valuation now always assumes the crafter
-- meets HQ thresholds, so the preference had nothing to configure.)

ALTER TABLE lolorito.market_model
    ADD COLUMN weighted_n numeric(14, 4) NULL;

-- ── formerly patch_32.sql ──────────────────────────────────────────────

-- patch_27 hard-capped skill levels at 100 via CHECK — wrong twice over:
-- desynthesis skill rises to the highest item level (720+ today), and any
-- fixed number breaks the moment an expansion raises the job cap or adds
-- higher ilvls. Keep only the non-negativity sanity check; the UI offers
-- dynamic, catalog-derived hints instead of enforcement.
ALTER TABLE lolorito.user_skill_level
    DROP CONSTRAINT user_skill_level_level_check;

ALTER TABLE lolorito.user_skill_level
    ADD CONSTRAINT user_skill_level_level_check CHECK (level >= 0);
