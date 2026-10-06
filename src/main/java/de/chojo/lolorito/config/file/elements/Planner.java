/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.config.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

/**
 * Defaults for the joint route-basket solver. Every value is overridable per
 * request (the SPA sends its own params) but these back-stop the API when a
 * field is missing.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
@OverwritePrefix("PLANNER")
public class Planner {
    /**
     * Cost of one wall-clock second of travel, in gil. Turns hops into EV.
     *
     * <p>A single world-to-world hop is roughly a minute of wall clock,
     * so this multiplied by 60 is the effective "gil per hop" a stop has
     * to earn before it's worth taking. The default (100 g/s ≈ 6 kg per
     * hop) is intentionally gentle so plans don't collapse to
     * home-world-only when only modest profits are available.
     */
    @Overwrite(env = @Env)
    private int hopWeightGilPerSecond = 100;

    /**
     * Seconds paid to hop between two worlds inside the same data center.
     */
    @Overwrite(env = @Env)
    private int tDcSeconds = 15;

    /**
     * Seconds paid to hop between two data centers in the same region.
     */
    @Overwrite(env = @Env)
    private int tRegionSeconds = 45;

    /**
     * Cap on the number of source worlds the solver may visit in one plan.
     */
    @Overwrite(env = @Env)
    private int maxWorlds = 5;

    /**
     * Coarse-pass row cap fed into the ILP after scoring.
     */
    @Overwrite(env = @Env)
    private int candidateTopK = 250;

    /**
     * Default attention budget when the request omits one, in hours.
     */
    @Overwrite(env = @Env)
    private double attentionBudgetHours = 8.0;

    /**
     * Default attention fraction when the request omits one.
     */
    @Overwrite(env = @Env)
    private double attentionFraction = 0.25;

    /**
     * Default inventory slot cap when the request omits one.
     */
    @Overwrite(env = @Env)
    private int inventorySlots = 140;

    /**
     * Default gil-in-pocket cap when the request omits one.
     */
    @Overwrite(env = @Env)
    private long budget = 1_000_000L;

    /**
     * Safety cap on ILP branch-and-bound iterations before we fall back to greedy.
     */
    @Overwrite(env = @Env)
    private int ilpMaxNodes = 200_000;

    /**
     * How many distinct picks the retainer partition tries to allocate.
     * Not the same as sell-side listing capacity — see
     * {@link #retainerListingSlots}. A pick that's split into several
     * stacks still counts as one pick, so this cap governs candidate
     * diversity rather than raw listing count.
     */
    @Overwrite(env = @Env)
    private int retainerSlots = 20;

    /**
     * Total sell-side listings available across all retainers. FFXIV's
     * default is 40 (two retainers × 20 marketable slots). A pick with
     * {@code qty} units of a stackable item is expected to be split into
     * {@code ceil(qty / 20)} listings at sell time — the retainer
     * partition caps against this budget so the engine only picks what
     * the retainers can actually list.
     */
    @Overwrite(env = @Env)
    private int retainerListingSlots = 40;

    /**
     * Target stack size when splitting a retainer pick into individual
     * listings. Small stacks clear faster because more buyers fit inside
     * their own carry budget — 20 is the classic "10× 20 sells before
     * 1× 200" heuristic.
     */
    @Overwrite(env = @Env)
    private int retainerListingStackTarget = 20;

    /**
     * Attention fraction applied to retainer picks. Small — retainer
     * listings only need occasional glances between sessions.
     */
    @Overwrite(env = @Env)
    private double retainerAttentionFraction = 0.05;

    /**
     * Expected shelf-time cutoff (hours) above which a candidate is
     * eligible for the retainer pool. Fast movers stay in the hop
     * basket; slow movers go to retainers.
     */
    @Overwrite(env = @Env)
    private double retainerShelfHoursThreshold = 6.0;

    /**
     * Cap on how much of the total budget + inventory the retainer
     * partition is allowed to eat before the hop plan gets its turn.
     * Without this the greedy loop happily allocates the full budget to
     * high-EV retainer picks and starves the hop planner — the SPA then
     * shows "no plan today" even though the retainer basket is full.
     * 0.40 = up to 40 % goes to retainers, the rest stays available for
     * hop-run purchases.
     */
    @Overwrite(env = @Env)
    private double retainerBudgetFraction = 0.4;

    /**
     * How deep the craft-chain planner walks recipe DAGs before it
     * gives up and treats the intermediate as buy-only. 3 catches the
     * common "flour → dough → bread" chains; going higher hits the
     * combinatorial wall on tiered crafts.
     * Runtime override via env {@code BOT_PLANNER_CRAFT_CHAIN_MAX_DEPTH}.
     */
    @Overwrite(env = @Env)
    private int craftChainMaxDepth = 3;

    public int craftChainMaxDepth() {
        return craftChainMaxDepth;
    }

    public int hopWeightGilPerSecond() {
        return hopWeightGilPerSecond;
    }

    public int tDcSeconds() {
        return tDcSeconds;
    }

    public int tRegionSeconds() {
        return tRegionSeconds;
    }

    public int maxWorlds() {
        return maxWorlds;
    }

    public int candidateTopK() {
        return candidateTopK;
    }

    public double attentionBudgetHours() {
        return attentionBudgetHours;
    }

    public double attentionFraction() {
        return attentionFraction;
    }

    public int inventorySlots() {
        return inventorySlots;
    }

    public long budget() {
        return budget;
    }

    public int ilpMaxNodes() {
        return ilpMaxNodes;
    }

    public int retainerSlots() {
        return retainerSlots;
    }

    public int retainerListingSlots() {
        return retainerListingSlots;
    }

    public int retainerListingStackTarget() {
        return retainerListingStackTarget;
    }

    public double retainerAttentionFraction() {
        return retainerAttentionFraction;
    }

    public double retainerBudgetFraction() {
        return retainerBudgetFraction;
    }

    public double retainerShelfHoursThreshold() {
        return retainerShelfHoursThreshold;
    }
}
