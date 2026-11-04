package tf.geo.exiftool;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExifToolReadTest {
    private static ExifTool exifTool;

    @TempDir
    Path dir;

    @BeforeAll
    static void start() {
        exifTool = new ExifTool(ExifToolTestSupport.require());
    }

    @AfterAll
    static void stop() {
        if (exifTool != null) {
            exifTool.close();
        }
    }

    private static Path jpeg(Path file) throws IOException {
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "jpg", file.toFile());
        return file;
    }

    @Test
    void readsTagsWithUmlautsInValueAndPath() throws IOException {
        final Path file = jpeg(dir.resolve("Bild_ä.jpg"));
        exifTool.execute(List.of("-overwrite_original", "-XMP-photoshop:City=Völs", "-GPS:GPSLatitude#=46.5",
                "-GPS:GPSLatitudeRef#=S", file.toString()));

        final List<Map<String, Object>> entries = exifTool.readJson(List.of(file),
                List.of("XMP-photoshop:City", "Composite:GPSLatitude"));

        assertEquals(1, entries.size());
        final Map<String, Object> entry = entries.get(0);
        assertEquals(file.toAbsolutePath().normalize(), Path.of(entry.get("SourceFile").toString()).toAbsolutePath().normalize());
        assertEquals("Völs", entry.get("XMP-photoshop:City"));
        assertEquals(-46.5, ((Number) entry.get("Composite:GPSLatitude")).doubleValue(), 1e-9);
    }

    @Test
    void emptyFileListGivesEmptyResult() {
        assertTrue(exifTool.readJson(List.of(), List.of("XMP-photoshop:City")).isEmpty());
    }

    @Test
    void extractsBinaryTag() throws IOException {
        final Path thumb = jpeg(dir.resolve("thumb.jpg"));
        final Path file = jpeg(dir.resolve("image.jpg"));
        exifTool.execute(List.of("-overwrite_original", "-ThumbnailImage<=" + thumb, file.toString()));

        final Path target = dir.resolve("extracted.jpg");
        assertTrue(exifTool.extractBinary(file, "ThumbnailImage", target));
        assertArrayEquals(Files.readAllBytes(thumb), Files.readAllBytes(target));
    }

    @Test
    void missingBinaryTagGivesFalse() throws IOException {
        final Path file = jpeg(dir.resolve("plain.jpg"));
        assertFalse(exifTool.extractBinary(file, "PreviewImage", dir.resolve("none.jpg")));
    }
}
