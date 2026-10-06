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
 * OAuth application credentials for the Discord "identify + guilds" login flow.
 * Redirect URI is derived from {@code http.publicBaseUrl} + {@code /auth/callback}
 * and does not live here.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
@OverwritePrefix("DISCORD_OAUTH")
public class DiscordOauth {
    @Overwrite(env = @Env)
    private String clientId = "";

    @Overwrite(env = @Env)
    private String clientSecret = "";

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }
}
