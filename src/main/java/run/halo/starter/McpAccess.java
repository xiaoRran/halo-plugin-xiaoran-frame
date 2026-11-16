package run.halo.starter;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;
@Component
public class McpAccess {
    private final OwnerSessions aiSessions=new OwnerSessions();
    private String code;
    private String agentCode;
    private Instant agentExpiry=Instant.EPOCH;
    private Instant expiry=Instant.EPOCH;
    public synchronized String issueCode(){var bytes=new byte[18];new SecureRandom().nextBytes(bytes);code=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);expiry=Instant.now().plusSeconds(300);return code;}
    public synchronized String bind(String input){if(input==null||code==null||!expiry.isAfter(Instant.now())||!MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8),input.getBytes(StandardCharsets.UTF_8)))return null;code=null;return aiSessions.issue();}
    public synchronized String issueAgentCode(){var bytes=new byte[18];new SecureRandom().nextBytes(bytes);agentCode=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);agentExpiry=Instant.now().plusSeconds(300);return agentCode;}
    public synchronized boolean consumeAgentCode(String input){if(input==null||agentCode==null||!agentExpiry.isAfter(Instant.now())||!MessageDigest.isEqual(agentCode.getBytes(StandardCharsets.UTF_8),input.getBytes(StandardCharsets.UTF_8)))return false;agentCode=null;return true;}
    public boolean valid(String token){return aiSessions.valid(token);}
    public void revoke(String token){aiSessions.revoke(token);}
}
