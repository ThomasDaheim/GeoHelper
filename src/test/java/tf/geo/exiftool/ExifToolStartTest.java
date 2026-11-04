package tf.geo.exiftool;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExifToolStartTest {
    @Test
    void missingExecutableFails() {
        try (ExifTool broken = new ExifTool(Path.of("C:/does/not/exist/exiftool.exe"))) {
            assertThrows(ExifToolException.class, broken::version);
        }
    }
}
