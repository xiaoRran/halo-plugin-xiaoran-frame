package run.halo.starter;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Short-lived owner sessions; a restart revokes all sessions. */
@Component
public class OwnerSessions {
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private long generation;
    private final Clock clock;
    private final Duration lifetime;

    public OwnerSessions() {
        this(Clock.systemUTC(), Duration.ofHours(12));
    }

    OwnerSessions(Clock clock, Duration lifetime) {
        this.clock = clock;
        this.lifetime = lifetime;
    }

    // Called only after server-side WeChat identity and owner binding verification.
    public synchronized String issue() {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        if (sessions.size() >= 5) {
            String oldest = sessions.entrySet().stream()
                .min(Map.Entry.comparingByValue()).orElseThrow().getKey();
            sessions.remove(oldest);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, now.plus(lifetime));
        return token;
    }

    public boolean valid(String token) {
        if (token == null || token.length() != 43) return false;
        Instant expiry = sessions.get(token);
        if (expiry == null) return false;
        if (!expiry.isAfter(clock.instant())) {
            sessions.remove(token, expiry);
            return false;
        }
        return true;
    }

    public synchronized long generation() { return generation; }
    public synchronized String issue(long expectedGeneration) {
        if (expectedGeneration != generation) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        return issue();
    }
    public synchronized void revokeAll() { generation++; sessions.clear(); }
    public void revoke(String token) {
        if (token != null) sessions.remove(token);
    }
}
