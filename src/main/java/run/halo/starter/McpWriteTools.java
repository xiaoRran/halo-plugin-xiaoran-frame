package run.halo.starter;
import java.util.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.infra.ExternalUrlSupplier;
@Component
public class McpWriteTools {
    private final McpConfigService config;
    private final McpWriteConfig writeConfig;
    private final McpImageService images;
    private final McpMomentService moments;
    private final ExternalUrlSupplier external;
    public McpWriteTools(McpConfigService config,McpWriteConfig writeConfig,McpImageService images,McpMomentService moments,ExternalUrlSupplier external){this.config=config;this.writeConfig=writeConfig;this.images=images;this.moments=moments;this.external=external;}
    private String link(String path){return path;}
    public boolean supports(String name){return Set.of("upload_image","publish_moment","moment_status").contains(name);}
    public List<Map<String,Object>> list(){return List.of(
        tool("upload_image","上传图片到本博客","公网图片传source_url和request_id；本地文件先不传参数获取multipart上传方式。图片上传后可公开访问，最多10MB，不接受服务器文件路径。",BlogMcpTools.schema(Map.of("source_url",Map.of("type","string","maxLength",4096),"request_id",Map.of("type","string","minLength",16,"maxLength",80)),List.of()),true),
        tool("publish_moment","确认后发布瞬间","先在Agent对话中预览图文并获得用户明确确认，再以confirm_public=true直接发布到博客。上传图片后传attachmentId数组；唯一request_id用于防重，重试复用原ID及内容。",BlogMcpTools.schema(Map.of("content",Map.of("type","string","minLength",1,"maxLength",10000),"images",Map.of("type","array","maxItems",9,"uniqueItems",true,"items",Map.of("type","string")),"request_id",Map.of("type","string","minLength",16,"maxLength",80),"confirm_public",Map.of("type","boolean","const",true)),List.of("content","request_id","confirm_public")),false),
        Map.of("name","moment_status","title","查看本Agent瞬间状态","description","仅查看当前Agent提交的瞬间发布状态","inputSchema",BlogMcpTools.schema(Map.of("id",Map.of("type","string","maxLength",253)),List.of("id")),"annotations",Map.of("readOnlyHint",true,"destructiveHint",false,"openWorldHint",false)));
    }
    private static Map<String,Object> tool(String name,String title,String description,Map<String,Object> schema,boolean open){return Map.of("name",name,"title",title,"description",description,"inputSchema",schema,"annotations",Map.of("readOnlyHint",false,"destructiveHint",false,"openWorldHint",open));}
    public Mono<Map<String,Object>> call(String agentId,String name,Map<String,Object> args){if(args==null)args=Map.of();final var input=args;return config.requireWriter(agentId).then(writeConfig.ready()).flatMap(settings->{
        return switch(name){
            case "upload_image" -> {if(input.keySet().stream().anyMatch(k->!Set.of("source_url","request_id").contains(k)))throw new IllegalArgumentException("图片参数不正确");if(!input.containsKey("source_url"))yield Mono.just(Map.of("uploadUrl",link("/apis/api.space.onoe.cn/v1alpha1/mcpimages"),"method","POST","fileField","file","requestIdField","request_id","headerName","X-Frame-MCP-Token","maxBytes",ImageFormat.MAX_BYTES,"instructions","用HTTP工具上传本地文件。相对uploadUrl应基于当前MCP地址解析，确认HTTPS同源后再携带当前Agent凭证；multipart字段file与request_id(UUID)。不要把本地路径交给服务器，不要打印凭证。返回attachmentId用于publish_moment。"));if(!(input.get("source_url") instanceof String url)||!(input.get("request_id") instanceof String request))throw new IllegalArgumentException("图片URL和request_id须为字符串");yield images.fromUrl(agentId,url,request);}
            case "publish_moment" -> {if(input.keySet().stream().anyMatch(k->!Set.of("content","images","request_id","confirm_public").contains(k))||!(input.get("content") instanceof String text)||!(input.get("request_id") instanceof String request))throw new IllegalArgumentException("瞬间参数不正确");var raw=input.getOrDefault("images",List.of());if(!(raw instanceof List<?> list)||list.stream().anyMatch(x->!(x instanceof String)))throw new IllegalArgumentException("images须为attachmentId数组");yield moments.publishConfirmed(agentId,text,(List<String>)list,request,Boolean.TRUE.equals(input.get("confirm_public"))).map(result->{var response=new LinkedHashMap<>(result);response.put("message",switch(result.get("state").toString()){case "PUBLISHED"->"瞬间已确认发布";case "REJECTED"->"该次提交已取消，未公开；如需重新发布请使用新的request_id";case "PUBLISHING"->"发布正在处理中，请查询moment_status或使用原request_id重试，尚不能报告已公开";default->"发布尚未完成，请使用原request_id重试或查询moment_status";});return response;});}
            case "moment_status" -> {if(input.size()!=1||!(input.get("id") instanceof String id))throw new IllegalArgumentException("请提供发布回执id");yield moments.status(agentId,id);}
            default -> Mono.error(new IllegalArgumentException("未开放此工具"));
        };
    });}
}
