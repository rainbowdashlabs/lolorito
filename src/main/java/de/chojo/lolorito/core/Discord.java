package de.chojo.lolorito.core;

import de.chojo.jdautil.interactions.dispatching.InteractionHub;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lolorito.config.Configuration;
import de.chojo.lolorito.discord.commands.check.Check;
import de.chojo.lolorito.discord.commands.offers.Offers;
import de.chojo.lolorito.discord.commands.top.Top;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.slf4j.Logger;

import java.util.Collections;

import static org.slf4j.LoggerFactory.getLogger;

public class Discord {
    private static final Logger log = getLogger(Discord.class);
    private final Data data;
    private final Threading threading;
    private final Configuration configuration;
    private ShardManager shardManager;

    private Discord(Data data, Threading threading, Configuration configuration) {
        this.data = data;
        this.threading = threading;
        this.configuration = configuration;
    }

    public static Discord create(Data data, Threading threading, Configuration configuration) {
        Discord discord = new Discord(data, threading, configuration);
        discord.init();
        return discord;
    }

    private void init() {
        initShardManager();
        initInteractions();
    }

    private void initShardManager() {
        shardManager = DefaultShardManagerBuilder
                .createDefault(configuration.baseSettings().token())
                .enableIntents(GatewayIntent.DIRECT_MESSAGES)
                .setEnableShutdownHook(false)
                .setThreadFactory(Threading.createThreadFactory(threading.jdaGroup()))
                .setEventPool(threading.jdaWorker())
                .build();
    }

    private void initInteractions() {
        InteractionHub.builder(shardManager)
                .testMode("true".equals(System.getProperty("bot.testmode", "false")))
                .cleanGuildCommands("true".equals(System.getProperty("bot.cleancommand", "false")))
                .withCommandErrorHandler((context, throwable) -> {
                    log.error(LogNotify.NOTIFY_ADMIN, "Command execution of {} failed\n{}",
                            context.interaction().meta().name(), context.args(), throwable);
                })
                .withGuildCommandMapper(cmd -> Collections.singletonList(configuration.baseSettings().botGuild()))
                .withDefaultMenuService()
                .withPagination(builder -> builder.previousText("Previous").nextText("Next"))
                .withCommands(new Offers(data), new Top(data), new Check(data))
                .build();
    }
}
