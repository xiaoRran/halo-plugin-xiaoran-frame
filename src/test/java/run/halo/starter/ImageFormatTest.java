package run.halo.starter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ImageFormatTest {
    @Test void rejectsExecutableMarkupAndOversizeFiles() {
        assertThrows(IllegalArgumentException.class,()->ImageFormat.detect("<svg onload='alert(1)'>".getBytes()));
        assertThrows(IllegalArgumentException.class,()->ImageFormat.detect(new byte[ImageFormat.MAX_BYTES+1]));
        assertThrows(IllegalArgumentException.class,()->ImageFormat.detect(null));
    }
    @Test void detectsMagicRatherThanExtension() {
        byte[] jpeg=new byte[12];jpeg[0]=(byte)255;jpeg[1]=(byte)216;jpeg[2]=(byte)255;
        assertEquals("image/jpeg",ImageFormat.detect(jpeg));
        assertEquals("image/webp",ImageFormat.detect("RIFF1234WEBPdata".getBytes()));
    }
}
