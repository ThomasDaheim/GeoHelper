package tf.geo.exiftool;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExifToolTest {
    private static ExifTool exifTool;

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

    @Test
    void versionIsReported() {
        assertTrue(exifTool.version().matches("\\d+\\.\\d+.*"), exifTool.version());
    }

    @Test
    void processIsReusedForSeveralCommands() {
        exifTool.version();
        final Process first = exifTool.process();
        exifTool.version();
        assertSame(first, exifTool.process());
    }

    @Test
    void crashedProcessIsRestarted() throws InterruptedException {
        exifTool.version();
        exifTool.process().destroyForcibly().waitFor();
        assertTrue(exifTool.version().matches("\\d+\\.\\d+.*"));
    }

    @Test
    void errorsAreReportedInStderr() {
        final ExecResult result = exifTool.execute(List.of("C:/does/not/exist.jpg"));
        assertTrue(result.hasErrors(), result.toString());
    }

    @Test
    void commandWithoutErrorsHasNone() {
        final ExecResult result = exifTool.execute(List.of("-ver"));
        assertFalse(result.hasErrors(), result.toString());
    }
}
