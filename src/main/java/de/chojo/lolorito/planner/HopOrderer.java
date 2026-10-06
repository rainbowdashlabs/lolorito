/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hop ordering — brute-force over permutations of the winning world
 * subset. With ≤ 5 stops that's at most 120 orderings, so we don't need a
 * TSP heuristic. Ties are broken by "biggest gil spend first inside a DC",
 * so if the player cuts the run short they harvested the top of the value.
 */
public final class HopOrderer {

    private HopOrderer() {}

    /**
     * Pick the permutation of {@code stops} that minimises the total wall-
     * clock loop (home → s1 → … → sn → home). Same-DC ties are broken by
     * total gil spend at that stop, biggest first.
     */
    public static Ordering order(
            WorldNode home, List<WorldNode> stops, Map<Integer, Long> spendByWorld, PlannerParams params) {
        if (stops.isEmpty()) {
            return new Ordering(List.of(), new int[0], 0, 0);
        }
        int n = stops.size();
        int[] best = null;
        // long math so unreachable (Integer.MAX_VALUE) hops don't overflow into
        // negative "cheap" totals.
        long bestSeconds = Long.MAX_VALUE;
        long bestFirstSpend = Long.MIN_VALUE;
        var perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        do {
            long total = 0;
            var prev = home;
            for (int i = 0; i < n; i++) {
                total += HopCoster.hopSeconds(prev, stops.get(perm[i]), params);
                prev = stops.get(perm[i]);
            }
            total += HopCoster.hopSeconds(prev, home, params);
            long firstSpend = spendByWorld.getOrDefault(stops.get(perm[0]).worldId(), 0L);
            if (total < bestSeconds || (total == bestSeconds && firstSpend > bestFirstSpend)) {
                bestSeconds = total;
                best = perm.clone();
                bestFirstSpend = firstSpend;
            }
        } while (nextPermutation(perm));

        var ordered = new ArrayList<WorldNode>(n);
        var hops = new int[n];
        var prev = home;
        long cumulative = 0;
        for (int i = 0; i < n; i++) {
            var next = stops.get(best[i]);
            hops[i] = HopCoster.hopSeconds(prev, next, params);
            cumulative += hops[i];
            ordered.add(next);
            prev = next;
        }
        int toHome = HopCoster.hopSeconds(prev, home, params);
        int cumulativeInt = cumulative > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cumulative;
        return new Ordering(ordered, hops, cumulativeInt, toHome);
    }

    /**
     * Lexicographic next-permutation. Returns false when the last one runs.
     */
    private static boolean nextPermutation(int[] a) {
        int n = a.length;
        int i = n - 2;
        while (i >= 0 && a[i] >= a[i + 1]) i--;
        if (i < 0) return false;
        int j = n - 1;
        while (a[j] <= a[i]) j--;
        int tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
        for (int lo = i + 1, hi = n - 1; lo < hi; lo++, hi--) {
            tmp = a[lo];
            a[lo] = a[hi];
            a[hi] = tmp;
        }
        return true;
    }

    /**
     * Ordering output: winning permutation and its total wall-clock hops.
     */
    public record Ordering(
            List<WorldNode> ordered, int[] hopSecondsFromPrev, int totalHopSeconds, int hopSecondsToHome) {}
}
