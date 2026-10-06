/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.repository.Sessions;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Background sweeper. Every {@code http.sweeperIntervalMinutes} minutes it
 * grabs sessions whose guild membership hasn't been re-checked in over
 * {@code http.guildRecheckTtlBackgroundMinutes} minutes and runs them
 * through {@link AuthService#revalidate}. Users who were kicked from the
 * guild lose access even while their browser sits idle.
 *
 * <p>Also opportunistically deletes expired session rows.
 *
 * <p>Wired as an eager singleton — the constructor itself schedules the
 * sweep loop with {@link Threading#botWorker()}.
 */
@Singleton
public class SessionRevalidator {
    private static final Logger log = getLogger(SessionRevalidator.class);
    private static final int SWEEP_BATCH = 200;

    private final File config;
    private final Sessions sessions;
    private final AuthService authService;

    @Inject
    public SessionRevalidator(Threading threading, File config, Sessions sessions, AuthService authService) {
        this.config = config;
        this.sessions = sessions;
        this.authService = authService;
        long interval = Math.max(1, config.http().sweeperIntervalMinutes());
        threading.botWorker().scheduleWithFixedDelay(this::sweep, interval, interval, TimeUnit.MINUTES);
        log.info("Session revalidator scheduled every {} minutes", interval);
    }

    void sweep() {
        try {
            int expired = sessions.deleteExpired(Instant.now());
            if (expired > 0) log.info("Session sweeper: deleted {} expired rows", expired);

            var threshold = Instant.now().minus(Duration.ofMinutes(config.http().guildRecheckTtlBackgroundMinutes()));
            var due = sessions.dueForBackgroundRecheck(threshold, SWEEP_BATCH);
            if (due.isEmpty()) return;

            int invalidated = 0;
            for (var s : due) {
                if (authService.revalidate(s).isEmpty()) invalidated++;
            }
            log.info("Session sweeper: revalidated {} sessions ({} invalidated)", due.size(), invalidated);
        } catch (Exception e) {
            log.warn("Session sweeper run failed", e);
        }
    }
}
