package run.halo.starter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnerSessionsTest {
    @Test void tokensAreDistinctRevocableAndBounded() {
        var sessions = new OwnerSessions();
        String first = sessions.issue();
        String second = sessions.issue();
        assertNotEquals(first, second);
        assertTrue(sessions.valid(first));
        sessions.revoke(first);
        assertFalse(sessions.valid(first));
        assertTrue(sessions.valid(second));
        assertFalse(sessions.valid(null));
        assertFalse(sessions.valid("invented"));
        for (int i = 0; i < 6; i++) sessions.issue();
        assertFalse(sessions.valid(second));
    }
    @Test void expiredTokensAreRejected() {
        var sessions = new OwnerSessions(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), Duration.ZERO);
        assertFalse(sessions.valid(sessions.issue()));
    }
}
