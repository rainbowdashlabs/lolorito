/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.entity;

/** Distinguishes craft skill from desynth skill; both share the class-name axis. */
public enum SkillKind {
    CRAFT("craft"),
    DESYNTH("desynth");

    private final String wire;

    SkillKind(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static SkillKind fromWire(String s) {
        if (s == null) throw new IllegalArgumentException("kind is required");
        return switch (s.toLowerCase()) {
            case "craft" -> CRAFT;
            case "desynth" -> DESYNTH;
            default -> throw new IllegalArgumentException("unknown skill kind: " + s);
        };
    }
}
