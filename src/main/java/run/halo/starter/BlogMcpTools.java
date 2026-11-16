package run.halo.starter;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Mono;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.*;
@Component
public class BlogMcpTools {
    private final ReactiveExtensionClient client;
    private final PostContentService contents;
    private final FrameConnectionService profile;
    @org.springframework.beans.factory.annotation.Autowired
    private run.halo.app.infra.ExternalUrlSupplier externalUrl;
    public BlogMcpTools(ReactiveExtensionClient client,PostContentService contents,FrameConnectionService profile){this.client=client;this.contents=contents;this.profile=profile;}
    static Map<String,Object> schema(Map<String,Object> properties,List<String> required){return Map.of("type","object","properties",properties,"required",required,"additionalProperties",false);}
    public List<Map<String,Object>> list(){return List.of(
        tool("site_info","查看站点信息","读取当前博客公开的名称与介绍",schema(Map.of(),List.of())),
        tool("search_articles","搜索公开文章","按标题与摘要搜索最多1000篇最新公开文章，最多返回10篇",schema(Map.of("query",Map.of("type","string","maxLength",100),"limit",Map.of("type","integer","minimum",1,"maximum",10)),List.of("query"))),
        tool("read_article","读取公开文章","按文章编号读取已发布的公开正文，最多12000字，不读取草稿",schema(Map.of("id",Map.of("type","string","maxLength",253)),List.of("id"))));}
    private static Map<String,Object> tool(String name,String title,String description,Map<String,Object> schema){return Map.of("name",name,"title",title,"description",description,"inputSchema",schema,"annotations",Map.of("readOnlyHint",true,"destructiveHint",false,"openWorldHint",false));}
    static boolean visible(Post p){return p.getMetadata()!=null&&p.getMetadata().getDeletionTimestamp()==null&&p.getSpec()!=null&&Boolean.TRUE.equals(p.getSpec().getPublish())&&!Boolean.TRUE.equals(p.getSpec().getDeleted())&&p.getSpec().getVisible()==Post.VisibleEnum.PUBLIC&&p.getSpec().getReleaseSnapshot()!=null&&!p.getSpec().getReleaseSnapshot().isBlank()&&p.getSpec().getPublishTime()!=null&&!p.getSpec().getPublishTime().isAfter(Instant.now())&&p.getStatus()!=null&&!Boolean.TRUE.equals(p.getStatus().getHideFromList());}
    static String plain(String html,int max){String s=Objects.toString(html,"").replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>","").replaceAll("<[^>]*>"," ").replace("&nbsp;"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replaceAll("\\s+"," ").trim();return s.length()>max?s.substring(0,max):s;}
    private Map<String,Object> post(Post p){return Map.of("id",p.getMetadata().getName(),"title",Objects.toString(p.getSpec().getTitle(),""),"excerpt",plain(p.getStatus().getExcerpt(),500),"url",Objects.toString(p.getStatus().getPermalink(),""));}
    public Mono<Map<String,Object>> call(String name,Map<String,Object> args){
        if(args==null)args=Map.of();final var input=args;
        return Mono.defer(()->switch(name){
            case "site_info" -> {if(!input.isEmpty())throw new IllegalArgumentException("站点工具不需要参数");yield profile.profile().map(p->{var info=new LinkedHashMap<String,Object>();String fallback=externalUrl==null||externalUrl.getRaw()==null?"当前博客":externalUrl.getRaw().getHost();for(String key:List.of("siteName","author","description","tagline"))info.put(key,p.getOrDefault(key,key.equals("siteName")?fallback:""));if(externalUrl!=null)info.put("siteUrl",externalUrl.getRaw()==null?externalUrl.get().toString():externalUrl.getRaw().toString());return (Map<String,Object>)info;});}
            case "search_articles" -> {if(input.keySet().stream().anyMatch(k->!Set.of("query","limit").contains(k))||!(input.get("query") instanceof String q)||q.length()>100)throw new IllegalArgumentException("搜索参数不正确");Object raw=input.getOrDefault("limit",5);if(!(raw instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<1||n.intValue()>10)throw new IllegalArgumentException("条数须为1到10");String query=q.strip().toLowerCase(Locale.ROOT);yield client.listAll(Post.class,new ListOptions(),Sort.by(Sort.Direction.DESC,"spec.publishTime")).filter(BlogMcpTools::visible).take(1000).filter(p->(Objects.toString(p.getSpec().getTitle(),"")+" "+Objects.toString(p.getStatus().getExcerpt(),"")).toLowerCase(Locale.ROOT).contains(query)).take(n.intValue()).map(this::post).collectList().map(items->Map.of("items",items,"scope","最新1000篇公开文章的标题和摘要"));}
            case "read_article" -> {if(input.size()!=1||!(input.get("id") instanceof String id)||!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,252}"))throw new IllegalArgumentException("文章编号不正确");yield client.fetch(Post.class,id).filter(BlogMcpTools::visible).switchIfEmpty(Mono.error(new IllegalArgumentException("文章不存在或不是公开文章"))).flatMap(p->contents.getReleaseContent(id).map(content->{var result=new LinkedHashMap<String,Object>(post(p));result.put("text",plain(content.getContent(),12000));result.put("truncated",Objects.toString(content.getContent(),"").length()>12000);return (Map<String,Object>)result;}));}
            default -> Mono.error(new IllegalArgumentException("未知或未开放的工具"));
        });
    }
}
