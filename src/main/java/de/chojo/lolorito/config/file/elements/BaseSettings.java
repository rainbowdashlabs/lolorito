/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.config.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal", "MismatchedQueryAndUpdateOfCollection"})
@OverwritePrefix("BOT")
public class BaseSettings {
    @Overwrite(env = @Env)
    private String token = "";

    private List<Long> botOwner = new ArrayList<>();

    @Overwrite(env = @Env)
    private long botGuild = 0L;

    public String token() {
        return token;
    }

    public boolean isOwner(long id) {
        return botOwner.contains(id);
    }

    public long botGuild() {
        return botGuild;
    }
}
