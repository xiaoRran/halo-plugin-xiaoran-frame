package run.halo.starter;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;
import run.halo.app.infra.utils.HttpSecurityUtils;
@Component
public class OfficialAccountApi {
 private final OfficialAccountConfig config;
 private final WebClient api;
 private final WebClient images;
 private String cachedKey="",cachedToken="";
 private Instant expiry=Instant.EPOCH;
 private Mono<String> pending;
 @Autowired public OfficialAccountApi(OfficialAccountConfig config){this(config,WebClient.builder().baseUrl("https://api.weixin.qq.com").codecs(c->c.defaultCodecs().maxInMemorySize(2*1024*1024)).build(),WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpSecurityUtils.secureHttpClient().followRedirect(false).responseTimeout(Duration.ofSeconds(12)))).filter(HttpSecurityUtils.maxResponseSizeFilter(ImageFormat.MAX_BYTES)).codecs(c->c.defaultCodecs().maxInMemorySize(ImageFormat.MAX_BYTES)).build());}
 OfficialAccountApi(OfficialAccountConfig config,WebClient api,WebClient images){this.config=config;this.api=api;this.images=images;}
 public static class Failure extends RuntimeException {
  private final boolean ambiguous;
  Failure(String message,boolean ambiguous){super(message);this.ambiguous=ambiguous;}
  public boolean ambiguous(){return ambiguous;}
 }
 static Failure failure(int code){return new Failure(switch(code){case 40164->"公众号服务器出口IP未加入微信白名单";case 40013->"公众号AppID无效";case 40125,40001->"公众号凭据无效，请检查AppSecret";case 40014,42001->"公众号访问凭证已过期，请重试";case 48001,48004->"公众号未获得此接口权限，请在微信公众平台检查";case 40007->"封面或草稿素材无效，请检查是否属于当前公众号";case 45009->"微信接口调用额度已用完，请稍后重试";default->"微信接口返回错误（"+code+"），请按微信官方错误码处理";},false);}
 private static JsonNode decode(String value){try{return run.halo.app.extension.Unstructured.OBJECT_MAPPER.readTree(value);}catch(Exception e){throw new Failure("微信返回了无法识别的数据",true);}}
 private static JsonNode checked(JsonNode value){if(value==null||!value.isObject())throw new Failure("微信返回了无法识别的数据",true);int code=value.path("errcode").asInt(0);if(code!=0)throw failure(code);return value;}
 private synchronized Mono<String> cached(OfficialAccountConfig.Credentials c){
  String key=McpConfigService.hash(c.appId()+"\n"+c.secret());
  if(!key.equals(cachedKey)){cachedKey=key;cachedToken="";expiry=Instant.EPOCH;pending=null;}
  if(!cachedToken.isBlank()&&expiry.isAfter(Instant.now()))return Mono.just(cachedToken);
  if(pending!=null)return pending;
  pending=api.post().uri("/cgi-bin/stable_token").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("grant_type","client_credential","appid",c.appId(),"secret",c.secret(),"force_refresh",false)).retrieve().bodyToMono(String.class).map(OfficialAccountApi::decode).timeout(Duration.ofSeconds(15)).map(OfficialAccountApi::checked).map(node->{String token=node.path("access_token").asText("");int seconds=node.path("expires_in").asInt(0);if(token.isBlank()||seconds<=0)throw new Failure("微信未返回有效访问凭证",false);synchronized(this){if(cachedKey.equals(key)){cachedToken=token;expiry=Instant.now().plusSeconds(Math.max(0,seconds-120));}}return token;}).onErrorMap(e->e instanceof Failure?e:new Failure("公众号连接失败，请检查网络与微信平台配置",false)).doFinally(signal->{synchronized(this){if(cachedKey.equals(key))pending=null;}}).cache();
  return pending;
 }
 public Mono<String> token(String appId){return config.credentials(appId).flatMap(this::cached);}
 private synchronized JsonNode checkedResponse(JsonNode node){if(node!=null&&Set.of(40001,40014,42001).contains(node.path("errcode").asInt(0))){cachedToken="";expiry=Instant.EPOCH;pending=null;}return checked(node);}
 public Mono<JsonNode> json(String appId,String path,Object body){return token(appId).flatMap(token->api.post().uri(b->b.path(path).queryParam("access_token",token).build()).contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve().bodyToMono(String.class).map(OfficialAccountApi::decode).timeout(Duration.ofSeconds(20)).map(this::checkedResponse).onErrorMap(e->e instanceof Failure?e:new Failure("微信请求结果未确认，请先到公众号后台核对",true)));}
 public Mono<byte[]> image(String url){return image(url,"");}
 public Mono<byte[]> image(String url,String sourceUrl){return Mono.defer(()->{var request=images.get().uri(McpImageService.source(url));if(sourceUrl!=null&&!sourceUrl.isBlank())request.header(HttpHeaders.REFERER,McpImageService.source(sourceUrl).toString());return request.exchangeToMono(response->{if(!response.statusCode().is2xxSuccessful())return Mono.error(new Failure("图片无法读取或发生跳转，请使用可直接访问的公网HTTPS图片",false));return response.bodyToMono(byte[].class);}).timeout(Duration.ofSeconds(15)).onErrorMap(e->e instanceof Failure?e:new Failure("图片读取失败、文件过大或地址不是公网HTTPS",false));});}
 public Mono<JsonNode> upload(String appId,byte[] bytes,boolean cover){return token(appId).flatMap(token->{String boundary="frame-"+UUID.randomUUID();byte[] payload=multipart(bytes,boundary);return api.post().uri(b->{b.path(cover?"/cgi-bin/material/add_material":"/cgi-bin/media/uploadimg").queryParam("access_token",token);if(cover)b.queryParam("type","image");return b.build();}).contentType(MediaType.parseMediaType("multipart/form-data; boundary="+boundary)).contentLength(payload.length).bodyValue(payload).retrieve().bodyToMono(String.class).map(OfficialAccountApi::decode).timeout(Duration.ofSeconds(25)).map(this::checkedResponse).onErrorMap(e->e instanceof Failure?e:new Failure("图片传输失败（"+(e instanceof org.springframework.web.reactive.function.client.WebClientResponseException response?"HTTP "+response.getStatusCode().value():e.getClass().getSimpleName())+(e.getCause()==null?"":"/"+e.getCause().getClass().getSimpleName())+"），请稍后重试",false));});}
 static byte[] multipart(byte[] bytes,String boundary){
  byte[] head=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"media\"; filename=\"frame-image.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  byte[] tail=("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  byte[] body=new byte[head.length+bytes.length+tail.length];System.arraycopy(head,0,body,0,head.length);System.arraycopy(bytes,0,body,head.length,bytes.length);System.arraycopy(tail,0,body,head.length+bytes.length,tail.length);return body;
 }
 static String required(JsonNode data,String key){String value=data.path(key).asText("");if(value.isBlank())throw new Failure("微信响应缺少"+key+"，请到公众号后台核对",true);return value;}
}
