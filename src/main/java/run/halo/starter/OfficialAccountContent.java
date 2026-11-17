package run.halo.starter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
@Component
public class OfficialAccountContent {
 private final OfficialAccountApi api;
 public OfficialAccountContent(OfficialAccountApi api){this.api=api;}
 static String absolute(String value,String base){try{var u=URI.create(base).resolve(value);return McpImageService.source(u.toString()).toString();}catch(Exception e){throw new IllegalArgumentException("文章图片或原文链接必须是公网HTTPS地址");}}
 static Document clean(String html,String base){
  if(html==null||html.isBlank()||html.length()>300000)throw new IllegalArgumentException("文章正文为空或过大");
  var source=Jsoup.parseBodyFragment(html,base);source.select("script,style,iframe,form,input,button,svg,canvas").remove();
  source.select("video,audio").forEach(e->e.replaceWith(new org.jsoup.nodes.Element("p").text("音视频内容请点击阅读原文查看。")));
  for(var e:source.select("img")){String src=e.attr("src");if(src.isBlank())src=e.attr("data-src");if(src.isBlank())throw new IllegalArgumentException("文章存在缺少地址的图片");e.attr("src",absolute(src,base));e.removeAttr("srcset");}
  for(var e:source.select("a[href]")){try{var uri=URI.create(base).resolve(e.attr("href"));if(!Set.of("https","http").contains(uri.getScheme()))e.removeAttr("href");else e.attr("href",uri.toString());}catch(Exception error){e.removeAttr("href");}}
  return new Cleaner(Safelist.relaxed().removeTags("table","thead","tbody","tfoot","tr","td","th")).clean(source);
 }
 public Mono<Prepared> prepare(String appId,String html,String base,String cover,String defaultCover,String defaultThumb){return Mono.defer(()->{
  var doc=clean(html,base);var urls=new LinkedHashSet<String>();for(var img:doc.select("img"))urls.add(img.attr("src"));if(urls.size()>30)throw new IllegalArgumentException("单篇最多同步30张正文图片，请精简后再试");
  String selected=cover==null?"":cover;if(selected.isBlank()&&!urls.isEmpty())selected=urls.iterator().next();if(selected.isBlank())selected=defaultCover;
  Mono<String> thumb;if(selected!=null&&!selected.isBlank())thumb=upload(appId,absolute(selected,base),true,base);else if(defaultThumb!=null&&!defaultThumb.isBlank())thumb=Mono.just(defaultThumb);else throw new IllegalArgumentException("请给文章设置封面，或在公众号设置中配置默认封面/永久素材ID");
  return Flux.fromIterable(urls).concatMap(url->upload(appId,url,false,base).map(replacement->Map.entry(url,replacement))).collectMap(Map.Entry::getKey,Map.Entry::getValue,LinkedHashMap::new).zipWith(thumb).map(pair->{for(var img:doc.select("img"))img.attr("src",pair.getT1().get(img.attr("src")));style(doc);String content=doc.body().html();if(content.length()>20000||content.getBytes(StandardCharsets.UTF_8).length>=1024*1024)throw new IllegalArgumentException("公众号正文超过接口长度限制，请精简内容");return new Prepared(content,pair.getT2());});
 });}
 private Mono<String> upload(String appId,String url,boolean cover,String sourceUrl){return api.image(url,sourceUrl).flatMap(bytes->Mono.fromCallable(()->jpeg(bytes)).subscribeOn(Schedulers.boundedElastic())).flatMap(bytes->api.upload(appId,bytes,cover)).map(data->OfficialAccountApi.required(data,cover?"media_id":"url")).map(value->{if(!cover){URI u=URI.create(value);if(!Set.of("https","http").contains(u.getScheme())||u.getHost()==null||!u.getHost().endsWith(".qpic.cn"))throw new IllegalArgumentException("微信返回了未知的正文图片域名");}return value;});}
 static void style(Document doc){for(var e:doc.body().getAllElements()){String style=switch(e.normalName()){case "img"->"max-width:100%;height:auto;display:block;margin:16px auto";case "p"->"font-size:16px;line-height:1.8;margin:0 0 16px;color:#333";case "h1","h2","h3","h4"->"font-size:20px;font-weight:600;line-height:1.5;margin:24px 0 12px";case "blockquote"->"border-left:3px solid #ef7099;padding:8px 14px;color:#666;margin:16px 0";case "pre","code"->"font-size:13px;white-space:pre-wrap;word-break:break-all;background:#f6f6f6";default->"";};if(!style.isBlank())e.attr("style",style);}}
 static byte[] jpeg(byte[] bytes)throws IOException{
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){boolean webp=bytes.length>=12&&bytes[0]==82&&bytes[1]==73&&bytes[2]==70&&bytes[3]==70&&bytes[8]==87&&bytes[9]==69&&bytes[10]==66&&bytes[11]==80;javax.imageio.ImageReader reader;if(webp)reader=new com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi().createReaderInstance();else{var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("图片格式无法转换，请改用JPEG、PNG或WebP");reader=readers.next();}try{reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||(long)w*h>16000000)throw new IllegalArgumentException("图片尺寸过大，最多1600万像素");var image=reader.read(0);int edge=1600;for(int i=0;i<5;i++){double ratio=Math.min(1,(double)edge/Math.max(w,h));var resized=new BufferedImage(Math.max(1,(int)(w*ratio)),Math.max(1,(int)(h*ratio)),BufferedImage.TYPE_INT_RGB);var g=resized.createGraphics();try{g.setColor(Color.WHITE);g.fillRect(0,0,resized.getWidth(),resized.getHeight());g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(image,0,0,resized.getWidth(),resized.getHeight(),null);}finally{g.dispose();}var out=new ByteArrayOutputStream();ImageIO.write(resized,"jpeg",out);if(out.size()<1024*1024)return out.toByteArray();edge=(int)(edge*.7);}throw new IllegalArgumentException("图片压缩后仍超过微信限制");}finally{reader.dispose();}}
 }
 public record Prepared(String html,String thumbMediaId){}
}
