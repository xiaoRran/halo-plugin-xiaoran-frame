package run.halo.starter;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import java.time.Duration;

/** Credentials are server environment only, never supplied by the mini program. */
@Component
public class WechatIdentity {
    private final WebClient client = WebClient.create("https://api.weixin.qq.com");

    public Mono<String> exchange(String code) {
        String appId = System.getenv("XIAORAN_WECHAT_APP_ID");
        String secret = System.getenv("XIAORAN_WECHAT_APP_SECRET");
        if (blank(appId) || blank(secret)) {
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "管理员登录尚未配置"));
        }
        if (blank(code) || code.length() > 256) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "登录凭证无效"));
        }
        return client.get().uri(builder -> builder.path("/sns/jscode2session")
                .queryParam("appid", appId).queryParam("secret", secret)
                .queryParam("js_code", code).queryParam("grant_type", "authorization_code").build())
            .retrieve().bodyToMono(String.class).map(body -> {
                try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, WechatSession.class); }
                catch (java.io.IOException error) { throw new IllegalStateException("微信响应格式异常"); }
            }).timeout(Duration.ofSeconds(10))
            .onErrorMap(error -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "微信登录暂时不可用"))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "微信登录失败")))
            .flatMap(session -> {
                if (session.errcode != null && session.errcode != 0 || blank(session.openid)) {
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "微信登录凭证失效，请重新登录"));
                }
                return Mono.just(session.openid);
            });
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public static class WechatSession {
        public String openid;
        public Integer errcode;
        public String errmsg;
        public String session_key;
        public String unionid;
    }
}
