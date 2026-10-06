/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.discord.listener;

import de.chojo.lolorito.repository.Sessions;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * JDA listener that evicts all sessions for a user the moment they leave (or
 * are kicked from) the configured guild. Zero-latency counterpart to the
 * per-request {@code guildRecheckTtl}.
 */
public class SessionEvictionListener extends ListenerAdapter {
    private static final Logger log = getLogger(SessionEvictionListener.class);

    private final long guildId;
    private final Sessions sessions;

    public SessionEvictionListener(long guildId, Sessions sessions) {
        this.guildId = guildId;
        this.sessions = sessions;
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        if (event.getGuild().getIdLong() != guildId) return;
        long userId = event.getUser().getIdLong();
        int deleted = sessions.deleteAllForUser(userId);
        if (deleted > 0) {
            log.info("Guild member {} left — evicted {} session(s)", userId, deleted);
        }
    }
}
