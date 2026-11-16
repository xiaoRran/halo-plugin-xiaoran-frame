package run.halo.starter;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
class SubmissionGuardTest {
    @Test void retriesReturnSameResultAndConflictingPayloadIsRejected() {
        var guard=new SubmissionGuard();var calls=new AtomicInteger();
        var result=guard.run("record-request-123456", "same", ()->Mono.fromSupplier(calls::incrementAndGet));
        assertEquals(1,result.block());
        assertEquals(1,guard.run("record-request-123456","same",()->Mono.just(99)).block());
        assertThrows(Exception.class,()->guard.run("record-request-123456","different",()->Mono.just(2)).block());
        assertEquals(1,calls.get());
    }
    @Test void failedSubmissionCanBeRetried() {
        var guard=new SubmissionGuard();
        assertThrows(Exception.class,()->guard.run("record-request-123456","same",()->Mono.error(new IllegalStateException())).block());
        assertEquals(2,guard.run("record-request-123456","same",()->Mono.just(2)).block());
    }
}
