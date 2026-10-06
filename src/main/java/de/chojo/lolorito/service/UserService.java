/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.BotUser;
import de.chojo.lolorito.repository.Users;
import net.dv8tion.jda.api.entities.User;

import java.util.concurrent.TimeUnit;

/**
 * Domain-level user access. Wraps the (currently thin) {@link Users} repo
 * plus an in-memory cache to keep JDA-driven lookups cheap.
 */
@Singleton
public class UserService {
    private final Cache<Long, BotUser> users =
            CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build();

    @Inject
    public UserService(Users unused) {
        // repository kept in the signature so callers thread through the layering
    }

    /**
     * {@link BotUser} for {@code user.getIdLong()}, cached for 10 min of idle access.
     */
    public BotUser of(User user) {
        return of(user.getIdLong());
    }

    /**
     * {@link BotUser} for a raw discord user id, cached like {@link #of(User)}.
     */
    public BotUser of(long userId) {
        var cached = users.getIfPresent(userId);
        if (cached != null) return cached;
        var bot = new BotUser(userId);
        users.put(userId, bot);
        return bot;
    }
}
