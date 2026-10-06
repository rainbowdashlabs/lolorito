/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.web.auth.DiscordOAuthClient;

/**
 * Deterministic Discord client for tests. Everything is fixed so we can
 * drive the OAuth flow without touching the network.
 *
 * <p>Guild membership is controlled via {@link #setInGuild(boolean)} — the
 * revalidation ladder honours the switch on the next call.
 */
public class StubDiscordOAuthClient extends DiscordOAuthClient {
    public static final long DEFAULT_USER_ID = 1_234L;
    public static final long DEFAULT_GUILD_ID = 42L;
    public static final String STUB_ACCESS = "stub-access";
    public static final String STUB_REFRESH = "stub-refresh";

    private boolean inGuild = true;
    private DiscordUser user = new DiscordUser(DEFAULT_USER_ID, "tester", "Tester", null);
    private boolean throwOnFetchUser = false;
    private long guildIdOverride = DEFAULT_GUILD_ID;

    public StubDiscordOAuthClient(File config) {
        super(config);
    }

    public void setInGuild(boolean inGuild) {
        this.inGuild = inGuild;
    }

    public void setUser(DiscordUser user) {
        this.user = user;
    }

    public void setThrowOnFetchUser(boolean value) {
        this.throwOnFetchUser = value;
    }

    public void setGuildIdOverride(long guildId) {
        this.guildIdOverride = guildId;
    }

    @Override
    public String authorizeUrl(String state) {
        return "https://discord.example/authorize?state=" + state;
    }

    @Override
    public String redirectUri() {
        return "https://lolorito.test/auth/callback";
    }

    @Override
    public Token exchangeCode(String code) {
        return new Token(STUB_ACCESS, STUB_REFRESH, 3600);
    }

    @Override
    public Token refresh(String refreshToken) {
        return new Token(STUB_ACCESS, STUB_REFRESH, 3600);
    }

    @Override
    public void revoke(String token) {
        // No-op: nothing to revoke in tests.
    }

    @Override
    public DiscordUser fetchUser(String accessToken) {
        if (throwOnFetchUser) throw new DiscordOAuthException("stubbed failure");
        return user;
    }

    @Override
    public long[] fetchGuildIds(String accessToken) {
        return inGuild ? new long[] {guildIdOverride} : new long[0];
    }
}
