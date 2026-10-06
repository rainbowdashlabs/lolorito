/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.config;

import de.chojo.lolorito.config.file.File;
import dev.chojo.ocular.Configurations;
import dev.chojo.ocular.dataformats.YamlDataFormat;
import dev.chojo.ocular.key.Key;

import java.nio.file.Path;
import java.util.List;

public class Conf extends Configurations<File> {
    public static final Key<File> CONFIG =
            Key.builder(Path.of("config.yaml"), File::new).build();

    public Conf() {
        super(Path.of("config"), CONFIG, List.of(new YamlDataFormat()), Conf.class.getClassLoader(), null);
    }
}
