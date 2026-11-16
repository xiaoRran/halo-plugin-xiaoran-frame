package run.halo.starter;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.*;

/** Stores only a one-way MCP credential hash. No model credentials or model service. */
@Component
public class McpConfigService {
    private final ReactiveExtensionClient client;
    public McpConfigService(ReactiveExtensionClient client){this.client=client;}
    private static Metadata metadata(){var m=new Metadata();m.setName("xiaoran-frame-mcp");return m;}
    public Mono<McpSettings> settings(){return client.fetch(McpSettings.class,"xiaoran-frame-mcp").switchIfEmpty(Mono.fromSupplier(()->{var s=new McpSettings();s.setMetadata(metadata());return s;}));}
    private Mono<Secret> secret(){return client.fetch(Secret.class,"xiaoran-frame-mcp").switchIfEmpty(Mono.fromSupplier(()->{var s=new Secret();s.setMetadata(metadata());s.setType("Opaque");s.setData(new HashMap<>());return s;}));}
    static String value(Secret s,String key){byte[] v=s.getData()==null?null:s.getData().get(key);return v==null?"":new String(v,StandardCharsets.UTF_8);}
    public Mono<Map<String,Object>> view(){return Mono.zip(settings(),secret()).map(pair->{var s=pair.getT1();return Map.<String,Object>of("enabled",s.isEnabled(),"allowedOrigin",Objects.toString(s.getAllowedOrigin(),""),"hasMcpToken",!value(pair.getT2(),"mcpHash").isBlank());});}
    public Mono<Map<String,Object>> save(Input input){
        if(input==null||input.allowedOrigin()==null||input.allowedOrigin().length()>2048)return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"配置格式不正确"));
        if(!input.allowedOrigin().isBlank()){try{var u=URI.create(input.allowedOrigin());if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!u.getPath().isEmpty())throw new IllegalArgumentException();}catch(Exception e){return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"允许来源应为 HTTPS 根域名"));}}
        return settings().flatMap(s->{s.setEnabled(input.enabled());s.setAllowedOrigin(input.allowedOrigin().trim());return s.getMetadata().getVersion()==null?client.create(s):client.update(s);}).then(view());
    }
    private Mono<Secret> saveSecret(String key,String value){return secret().flatMap(s->{var data=new HashMap<String,byte[]>(s.getData()==null?Map.of():s.getData());data.put(key,value.getBytes(StandardCharsets.UTF_8));s.setData(data);return s.getMetadata().getVersion()==null?client.create(s):client.update(s);});}
    static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public Mono<Map<String,String>> issueMcpToken(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);return saveSecret("mcpHash",hash(token)).thenReturn(Map.of("token",token));}
    private static List<Agent> agents(Secret secret){String raw=value(secret,"agents");if(raw.isBlank())return List.of();try{return run.halo.app.extension.Unstructured.OBJECT_MAPPER.readValue(raw,new com.fasterxml.jackson.core.type.TypeReference<List<Agent>>(){});}catch(Exception e){throw new IllegalStateException("Agent连接配置读取失败");}}
    public Mono<List<Map<String,Object>>> listAgents(){return secret().map(s->agents(s).stream().map(a->Map.<String,Object>of("id",a.id(),"name",a.name(),"createdAt",a.createdAt(),"writeEnabled",Boolean.TRUE.equals(a.writeEnabled()))).toList());}
    public Mono<Map<String,String>> enrollAgent(String name){
        String label=name==null||name.isBlank()?"我的 Agent":name.strip();
        if(label.length()>60||label.chars().anyMatch(Character::isISOControl))return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"Agent名称不正确"));
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),id=UUID.randomUUID().toString();
        return secret().flatMap(s->{var list=new ArrayList<>(agents(s));if(list.size()>=8)return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"最多连接8个Agent，请先断开一个"));list.add(new Agent(id,label,hash(token),java.time.Instant.now().toString(),false));return saveAgents(s,list);}).thenReturn(Map.of("connectionId",id,"token",token,"headerName","X-Frame-MCP-Token","protocolVersion",McpEndpoint.PROTOCOL));
    }
    private Mono<Secret> saveAgents(Secret s,List<Agent> list){var data=new HashMap<String,byte[]>(s.getData()==null?Map.of():s.getData());data.put("agents",McpEndpoint.serialize(list).getBytes(StandardCharsets.UTF_8));s.setData(data);return s.getMetadata().getVersion()==null?client.create(s):client.update(s);}
    public Mono<Void> revokeAgent(String id){return secret().flatMap(s->saveAgents(s,agents(s).stream().filter(a->!a.id().equals(id)).toList())).then();}
    public record Agent(String id,String name,String tokenHash,String createdAt,Boolean writeEnabled){}
    public record AgentAccess(String agentId,boolean canWrite){}
    public Mono<AgentAccess> agentAccess(String token){if(token==null||token.length()!=43)return Mono.just(new AgentAccess("",false));byte[] supplied=hash(token).getBytes(StandardCharsets.UTF_8);return secret().map(s->{for(var a:agents(s))if(MessageDigest.isEqual(a.tokenHash().getBytes(StandardCharsets.UTF_8),supplied))return new AgentAccess(a.id(),Boolean.TRUE.equals(a.writeEnabled()));return new AgentAccess("",false);});}
    public Mono<Boolean> agentWritable(String id){return secret().map(s->agents(s).stream().anyMatch(a->a.id().equals(id)&&Boolean.TRUE.equals(a.writeEnabled())));}
    public Mono<Void> requireWriter(String id){return agentWritable(id).flatMap(ok->ok?Mono.empty():Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,"此Agent没有上传和写瞬间权限")));}
    public Mono<Void> setAgentWrite(String id,boolean enabled){return secret().flatMap(s->{var list=agents(s);if(list.stream().noneMatch(a->a.id().equals(id)))return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,"连接不存在"));return saveAgents(s,list.stream().map(a->a.id().equals(id)?new Agent(a.id(),a.name(),a.tokenHash(),a.createdAt(),enabled):a).toList());}).then();}
    public Mono<Void> revokeMcpToken(){return saveSecret("mcpHash","").then();}
    public Mono<Boolean> validMcpToken(String token){if(token==null||token.length()!=43)return Mono.just(false);return secret().map(s->{byte[] supplied=hash(token).getBytes(StandardCharsets.UTF_8);boolean valid=MessageDigest.isEqual(value(s,"mcpHash").getBytes(StandardCharsets.UTF_8),supplied);for(var agent:agents(s))valid|=MessageDigest.isEqual(agent.tokenHash().getBytes(StandardCharsets.UTF_8),supplied);return valid;});}
    public record Input(boolean enabled,String allowedOrigin){}
}
