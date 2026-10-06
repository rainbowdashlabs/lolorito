/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.core;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.jdautil.interactions.dispatching.InteractionHub;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.discord.commands.check.Check;
import de.chojo.lolorito.discord.commands.offers.Offers;
import de.chojo.lolorito.discord.commands.top.Top;
import de.chojo.lolorito.discord.listener.SessionEvictionListener;
import de.chojo.lolorito.repository.Sessions;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;

import java.util.Collections;

import static org.slf4j.LoggerFactory.getLogger;

@Singleton
public class Discord {
    private static final Logger log = getLogger(Discord.class);

    private final Threading threading;
    private final File config;
    private final Sessions sessions;
    private final Offers offers;
    private final Top top;
    private final Check check;

    private ShardManager shardManager;

    @Inject
    public Discord(Threading threading, File config, Sessions sessions, Offers offers, Top top, Check check) {
        this.threading = threading;
        this.config = config;
        this.sessions = sessions;
        this.offers = offers;
        this.top = top;
        this.check = check;
        initShardManager();
        initInteractions();
    }

    /** Exposed so other services (e.g. the alert DM dispatcher) can send messages. */
    public ShardManager shardManager() {
        return shardManager;
    }

    private void initShardManager() {
        // GUILD_MEMBERS is a privileged intent — it must be enabled in the
        // Discord developer portal. Required so we can evict sessions the
        // moment a user leaves the guild.
        // Cache the entire member roster of every guild we're in. Default
        // policy only keeps online users, so lookups for a legitimate but
        // offline user return null and the session revalidator wrongly
        // decides they "left the guild". Since we operate against a single
        // configured guild, the memory footprint is negligible.
        shardManager = DefaultShardManagerBuilder.createDefault(
                        config.baseSettings().token())
                .enableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .enableCache(CacheFlag.MEMBER_OVERRIDES)
                .setEnableShutdownHook(false)
                .setThreadFactory(Threading.createThreadFactory(threading.jdaGroup()))
                .setEventPool(threading.jdaWorker())
                .addEventListeners(
                        new SessionEvictionListener(config.baseSettings().botGuild(), sessions))
                .build();
    }

    private void initInteractions() {
        InteractionHub.builder(shardManager)
                .testMode("true".equals(System.getProperty("bot.testmode", "false")))
                .cleanGuildCommands("true".equals(System.getProperty("bot.cleancommand", "false")))
                .withCommandErrorHandler((context, throwable) -> log.error(
                        LogNotify.NOTIFY_ADMIN,
                        "Command execution of {} failed\n{}",
                        context.interaction().meta().name(),
                        context.args(),
                        throwable))
                .withGuildCommandMapper(
                        cmd -> Collections.singletonList(config.baseSettings().botGuild()))
                .withDefaultMenuService()
                .withPagination(builder -> builder.previousText("Previous").nextText("Next"))
                .withCommands(offers, top, check)
                .build();
    }
}
