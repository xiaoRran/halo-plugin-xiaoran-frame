package run.halo.starter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.*;
@Component
public class OfficialAccountConfig {
 static final String NAME="xiaoran-frame-official-account";
 private final ReactiveExtensionClient client;
 public OfficialAccountConfig(ReactiveExtensionClient client){this.client=client;}
 static Metadata metadata(String name){var m=new Metadata();m.setName(name);return m;}
 public Mono<OfficialAccountSettings> settings(){return client.fetch(OfficialAccountSettings.class,NAME).defaultIfEmpty(defaults());}
 static OfficialAccountSettings defaults(){var s=new OfficialAccountSettings();s.setMetadata(metadata(NAME));return s;}
 private Mono<Secret> secret(){return client.fetch(Secret.class,NAME).defaultIfEmpty(newSecret());}
 private static Secret newSecret(){var s=new Secret();s.setMetadata(metadata(NAME));s.setType("Opaque");s.setData(new HashMap<>());return s;}
 private static String value(Secret s){var bytes=s.getData()==null?null:s.getData().get("appSecret");return bytes==null?"":new String(bytes,StandardCharsets.UTF_8);}
 public Mono<Map<String,Object>> view(){return Mono.zip(settings(),secret()).map(pair->{var s=pair.getT1();var result=new LinkedHashMap<String,Object>();result.put("enabled",s.isEnabled());result.put("autoSync",s.isAutoSync());result.put("autoPublish",s.isAutoPublish());result.put("appId",s.getAppId());result.put("author",s.getAuthor());result.put("defaultCoverUrl",s.getDefaultCoverUrl());result.put("defaultThumbMediaId",s.getDefaultThumbMediaId());result.put("hasSecret",!value(pair.getT2()).isBlank());return result;});}
 public Mono<Credentials> credentials(String expected){return Mono.zip(settings(),secret()).map(pair->{var s=pair.getT1();String secret=value(pair.getT2());if(!s.isEnabled()||!Objects.equals(expected,s.getAppId())||secret.isBlank())throw new IllegalArgumentException("请先启用并配置对应的公众号凭据");return new Credentials(s.getAppId(),secret);});}
 static String text(String s){return s==null?"":s.strip();}
 static void validate(Input input){
  if(input==null)throw new IllegalArgumentException("请填写公众号配置");
  if(input.enabled()&&text(input.appId()).isBlank())throw new IllegalArgumentException("请填写公众号AppID");
  if(!text(input.appId()).isEmpty()&&!text(input.appId()).matches("wx[a-zA-Z0-9]{16}"))throw new IllegalArgumentException("公众号AppID格式不正确：应为wx开头共18位，当前"+text(input.appId()).length()+"位，请从公众号后台完整复制");
  if(text(input.appSecret()).length()>256)throw new IllegalArgumentException("公众号AppSecret长度异常，请重新复制，不要附带说明文字");
  if(text(input.author()).codePointCount(0,text(input.author()).length())>8)throw new IllegalArgumentException("署名超过8字，请缩短署名");
  if(text(input.defaultThumbMediaId()).length()>256)throw new IllegalArgumentException("默认封面media_id过长，请重新复制素材编号");
  if(!text(input.defaultCoverUrl()).isBlank()){try{McpImageService.source(text(input.defaultCoverUrl()));}catch(IllegalArgumentException e){throw new IllegalArgumentException("默认封面图片地址须为完整的公网HTTPS地址");}}
  if(input.autoPublish()&&(!input.enabled()||!input.autoSync()||!input.confirmAutoPublish()))throw new IllegalArgumentException("自动发表需启用同步并确认公开发表");
 }
 public Mono<Map<String,Object>> save(Input input){return Mono.defer(()->{validate(input);return Mono.zip(settings(),secret()).flatMap(pair->{var s=pair.getT1();var sec=pair.getT2();String incoming=text(input.appSecret());if(!Objects.equals(s.getAppId(),text(input.appId()))&&incoming.isBlank()&&!value(sec).isBlank())return Mono.error(new IllegalArgumentException("切换公众号时请同时填写其AppSecret"));if(input.enabled()&&incoming.isBlank()&&value(sec).isBlank())return Mono.error(new IllegalArgumentException("请填写公众号AppSecret"));Mono<?> saveSecret=Mono.empty();if(!incoming.isBlank()){sec.setData(new HashMap<>(Map.of("appSecret",incoming.getBytes(StandardCharsets.UTF_8))));saveSecret=sec.getMetadata().getVersion()==null?client.create(sec):client.update(sec);}s.setEnabled(input.enabled());s.setAutoSync(input.autoSync());s.setAutoPublish(input.autoPublish());s.setAppId(text(input.appId()));s.setAuthor(text(input.author()));s.setDefaultCoverUrl(text(input.defaultCoverUrl()));s.setDefaultThumbMediaId(text(input.defaultThumbMediaId()));return saveSecret.then(Mono.defer(()->s.getMetadata().getVersion()==null?client.create(s):client.update(s))).then(view());});});}
 public record Credentials(String appId,String secret){@Override public String toString(){return "OfficialAccountCredentials[redacted]";}}
 public record Input(boolean enabled,boolean autoSync,boolean autoPublish,boolean confirmAutoPublish,String appId,String appSecret,String author,String defaultCoverUrl,String defaultThumbMediaId){@Override public String toString(){return "OfficialAccountInput[secret redacted]";}}
}
