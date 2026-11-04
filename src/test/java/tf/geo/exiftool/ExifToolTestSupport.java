package tf.geo.exiftool;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;

/**
 * Finds the ExifTool executable for tests: environment variable EXIFTOOL_PATH first, then the PATH.
 * Tests call require() and are skipped if no ExifTool is installed.
 */
final class ExifToolTestSupport {
    private ExifToolTestSupport() {
    }

    static Optional<Path> locate() {
        final String configured = System.getenv("EXIFTOOL_PATH");
        if (configured != null && !configured.isBlank()) {
            final Path path = Path.of(configured.trim());
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        }
        final String pathVariable = System.getenv("PATH");
        if (pathVariable == null) {
            return Optional.empty();
        }
        for (String dir : pathVariable.split(File.pathSeparator)) {
            for (String name : List.of("exiftool.exe", "exiftool")) {
                try {
                    final Path candidate = Path.of(dir.trim().replace("\"", ""), name);
                    if (Files.isRegularFile(candidate)) {
                        return Optional.of(candidate);
                    }
                } catch (InvalidPathException ex) {
                    // broken PATH entry - ignore
                }
            }
        }
        return Optional.empty();
    }

    static Path require() {
        final Optional<Path> exifTool = locate();
        Assumptions.assumeTrue(exifTool.isPresent(), "ExifTool not found - set EXIFTOOL_PATH or put exiftool on the PATH");
        return exifTool.get();
    }
}
