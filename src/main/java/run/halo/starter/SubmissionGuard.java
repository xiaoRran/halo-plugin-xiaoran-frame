package run.halo.starter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Bounded retry window. Successful results survive retries for this process only. */
@Component
public class SubmissionGuard {
    private record Entry(String fingerprint, Instant expiry, Mono<?> result) {}
    private final Map<String, Entry> entries = new HashMap<>();
    @SuppressWarnings("unchecked")
    public synchronized <T> Mono<T> run(String key, String fingerprint, Supplier<Mono<T>> action) {
        if (key == null) return Mono.defer(action);
        if (!key.matches("[a-zA-Z0-9-]{16,80}")) return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"提交标识无效"));
        Instant now=Instant.now();
        entries.entrySet().removeIf(entry->!entry.getValue().expiry().isAfter(now));
        Entry existing=entries.get(key);
        if(existing!=null){
            if(!existing.fingerprint().equals(fingerprint))return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"提交标识已用于其他内容"));
            return (Mono<T>)existing.result();
        }
        if(entries.size()>=1000)return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"请稍后重试"));
        Mono<T> result=Mono.defer(action).doOnError(error->remove(key)).cache();
        entries.put(key,new Entry(fingerprint,now.plusSeconds(900),result));
        return result;
    }
    private synchronized void remove(String key){entries.remove(key);}
}
