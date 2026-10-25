/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.config;

import de.chojo.lolorito.config.file.File;
import dev.chojo.ocular.Configurations;
import dev.chojo.ocular.dataformats.YamlDataFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A config file written by an older version still holds keys that were removed since. Loading it must keep the
 * remaining values: if Ocular rejected the file, it would back it up and start from defaults, dropping the token
 * and every other setting.
 */
class RemovedConfigKeysTest {

    @TempDir
    Path dir;

    @Test
    void oldFileWithRemovedKeysStillLoads() throws IOException {
        Files.writeString(dir.resolve("config.yaml"), """
                baseSettings:
                  botGuild: 1234
                links:
                  invite: "https://example.com/invite"
                planner:
                  retainerBudgetFraction: 0.4
                value:
                  undercutBotThresholdSeconds: 60
                """);

        var conf = new Configurations<File>(
                dir, Conf.CONFIG, List.of(new YamlDataFormat()), getClass().getClassLoader(), null);

        assertEquals(1234L, conf.main().baseSettings().botGuild());
        try (var files = Files.list(dir)) {
            assertFalse(files.anyMatch(f -> f.getFileName().toString().startsWith("backup_")));
        }
    }
}
