/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.config.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
@OverwritePrefix("LINKS")
public class Links {
    @Overwrite(env = @Env)
    private String tos = "";

    @Overwrite(env = @Env)
    private String invite =
            "https://discord.com/oauth2/authorize?client_id=868279025251012639&scope=bot&permissions=1342532672";

    @Overwrite(env = @Env)
    private String support = "";

    @Overwrite(env = @Env)
    private String website = "https://rainbowdashlabs.github.io/lolorito/";

    @Overwrite(env = @Env)
    private String faq = "https://rainbowdashlabs.github.io/lolorito/faq";

    public String tos() {
        return tos;
    }

    public String invite() {
        return invite;
    }

    public String support() {
        return support;
    }

    public String website() {
        return website;
    }

    public String faq() {
        return faq;
    }
}
