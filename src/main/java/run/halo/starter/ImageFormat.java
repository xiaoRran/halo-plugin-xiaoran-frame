package run.halo.starter;

import java.nio.charset.StandardCharsets;

/** Allow only recognized raster images; never trust filename or supplied MIME type. */
public final class ImageFormat {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private ImageFormat() {}
    public static String detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("图片为空、格式无效或超过10MB");
        }
        if ((bytes[0]&255)==0xff && (bytes[1]&255)==0xd8 && (bytes[2]&255)==0xff) return "image/jpeg";
        if ((bytes[0]&255)==137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71
            && bytes[4]==13 && bytes[5]==10 && bytes[6]==26 && bytes[7]==10) return "image/png";
        String prefix = new String(bytes, 0, 12, StandardCharsets.ISO_8859_1);
        if (prefix.startsWith("GIF87a") || prefix.startsWith("GIF89a")) return "image/gif";
        if (prefix.startsWith("RIFF") && prefix.substring(8).equals("WEBP")) return "image/webp";
        throw new IllegalArgumentException("仅支持JPEG、PNG、GIF和WebP图片");
    }
}
