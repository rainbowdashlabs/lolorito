package de.chojo.lolorito.dao;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.dv8tion.jda.api.entities.User;

import javax.sql.DataSource;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class Users {
    private final Cache<Long, BotUser> users = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES)
                                                           .build();
    private final DataSource dataSource;

    public Users(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public BotUser user(User user) {
        try {
            return users.get(user.getIdLong(), () -> new BotUser(user, dataSource));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
