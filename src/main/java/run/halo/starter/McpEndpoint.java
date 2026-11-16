package run.halo.starter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
@Component
public class McpEndpoint implements CustomEndpoint {
    public static final String PROTOCOL="2025-11-25";
    private final McpConfigService config;
    private final McpAccess access;
    private final OwnerSessions owners;
    private final BlogMcpTools tools;
    @org.springframework.beans.factory.annotation.Autowired
    private McpWriteTools writes;
    public McpEndpoint(McpConfigService config,McpAccess access,OwnerSessions owners,BlogMcpTools tools){this.config=config;this.access=access;this.owners=owners;this.tools=tools;}
    @Override public GroupVersion groupVersion(){return new GroupVersion("api.space.onoe.cn","v1alpha1");}
    @Override public RouterFunction<ServerResponse> endpoint(){return RouterFunctions.route().POST("/mcp",this::handle).GET("/mcp",this::noStream).build();}
    public boolean miniAuthorized(ServerRequest r){return access.valid(r.headers().firstHeader("X-Frame-MCP-Session"))||owners.valid(r.headers().firstHeader("X-Xiaoran-Session"));}
    private Mono<Boolean> authorized(ServerRequest r){if(miniAuthorized(r))return Mono.just(true);return config.validMcpToken(r.headers().firstHeader("X-Frame-MCP-Token"));}
    private boolean originAllowed(ServerRequest r,McpSettings s){String origin=r.headers().firstHeader("Origin");return origin==null||origin.equals(s.getAllowedOrigin())||miniAuthorized(r)&&origin.equals("https://servicewechat.com");}
    private Mono<ServerResponse> guard(ServerRequest r,java.util.function.Supplier<Mono<ServerResponse>> action){return config.settings().flatMap(s->{if(!originAllowed(r,s))return ServerResponse.status(403).build();if(!s.isEnabled())return ServerResponse.status(503).bodyValue(Map.of("detail","博主尚未启用 MCP"));return authorized(r).flatMap(ok->ok?action.get():ServerResponse.status(401).build());});}
    private Mono<List<Map<String,Object>>> catalog(ServerRequest r){if(writes==null)return Mono.just(tools.list());return config.agentAccess(r.headers().firstHeader("X-Frame-MCP-Token")).map(access->{var list=new ArrayList<Map<String,Object>>(tools.list());if(access.canWrite())list.addAll(writes.list());return (List<Map<String,Object>>)list;});}
    private Mono<Map<String,Object>> execute(ServerRequest r,String name,Map<String,Object> args){if(writes==null||!writes.supports(name))return tools.call(name,args);return config.agentAccess(r.headers().firstHeader("X-Frame-MCP-Token")).flatMap(access->access.canWrite()?writes.call(access.agentId(),name,args):Mono.error(new IllegalArgumentException("此连接只读。请在Halo已连接的Agent中开启上传与写瞬间权限，并重新发现工具。")));}
    private Mono<ServerResponse> noStream(ServerRequest r){return guard(r,()->ServerResponse.status(405).header("Allow","POST").build());}
    private Mono<ServerResponse> handle(ServerRequest r){return guard(r,()->r.bodyToMono(String.class).flatMap(raw->{
        JsonNode message;try{message=run.halo.app.extension.Unstructured.OBJECT_MAPPER.readTree(raw);}catch(Exception e){return error(null,-32700,"JSON 格式不正确");}
        if(message==null)return error(null,-32600,"无效请求");
        if(!message.isObject()||!message.path("jsonrpc").asText().equals("2.0")||!message.path("method").isTextual()||message.toString().length()>32000)return error(null,-32600,"无效请求");
        JsonNode id=message.get("id");String method=message.path("method").asText();if(id!=null&&(!id.isTextual()&&!id.isIntegralNumber()))return error(null,-32600,"无效请求编号");
        String version=r.headers().firstHeader("MCP-Protocol-Version");if(!method.equals("initialize")&&!PROTOCOL.equals(version))return ServerResponse.badRequest().bodyValue(Map.of("error","不支持的 MCP 协议版本"));
        if(id==null)return method.startsWith("notifications/")?ServerResponse.accepted().build():error(null,-32600,"请求缺少编号");
        return switch(method){
            case "initialize" -> result(id,Map.of("protocolVersion",PROTOCOL,"capabilities",Map.of("tools",Map.of("listChanged",false)),"serverInfo",Map.of("name","xiaoran-frame-halo","version","0.4.8"),"instructions","工具按连接权限提供。默认只读；写入需一次授权；在Agent对话中展示图文并获得用户明确确认后，调用publish_moment直接发布。只有返回PUBLISHED才能报告已公开。不提供删除或私密读取工具。"));
            case "ping" -> result(id,Map.of());
            case "tools/list" -> catalog(r).flatMap(list->result(id,Map.of("tools",list)));
            case "tools/call" -> {
                JsonNode params=message.get("params");if(params==null||!params.isObject()||!params.path("name").isTextual()||params.has("arguments")&&!params.get("arguments").isObject())yield error(id,-32602,"工具参数格式不正确");
                Map<String,Object> args=params.has("arguments")?run.halo.app.extension.Unstructured.OBJECT_MAPPER.convertValue(params.get("arguments"),Map.class):Map.of();
                yield execute(r,params.get("name").asText(),args).flatMap(data->result(id,Map.of("content",List.of(Map.of("type","text","text",serialize(data))),"structuredContent",data,"isError",false)))
                    .onErrorResume(org.springframework.web.server.ResponseStatusException.class,e->result(id,Map.of("content",List.of(Map.of("type","text","text",Objects.toString(e.getReason(),"操作暂不可用"))),"isError",true)))
                    .onErrorResume(IllegalArgumentException.class,e->result(id,Map.of("content",List.of(Map.of("type","text","text",e.getMessage())),"isError",true)))
                    .onErrorResume(e->result(id,Map.of("content",List.of(Map.of("type","text","text","博客内容暂不可用")),"isError",true)));
            }
            default -> error(id,-32601,"未实现的方法");
        };
    }).switchIfEmpty(error(null,-32600,"请求不能为空")).onErrorResume(org.springframework.core.codec.DecodingException.class,e->error(null,-32700,"JSON 格式不正确")));}
    static String serialize(Object value){try{return run.halo.app.extension.Unstructured.OBJECT_MAPPER.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("响应格式异常");}}
    private Mono<ServerResponse> result(JsonNode id,Object result){return ServerResponse.ok().header("Cache-Control","no-store").bodyValue(Map.of("jsonrpc","2.0","id",run.halo.app.extension.Unstructured.OBJECT_MAPPER.convertValue(id,Object.class),"result",result));}
    private Mono<ServerResponse> error(JsonNode id,int code,String message){var response=new LinkedHashMap<String,Object>();response.put("jsonrpc","2.0");response.put("id",id==null?null:run.halo.app.extension.Unstructured.OBJECT_MAPPER.convertValue(id,Object.class));response.put("error",Map.of("code",code,"message",message));return ServerResponse.ok().header("Cache-Control","no-store").bodyValue(response);}
}
