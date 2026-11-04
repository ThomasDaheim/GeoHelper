package tf.geo.exiftool;

import java.util.List;

/**
 * Output of one ExifTool command, split into lines - without the {ready} markers.
 */
public record ExecResult(List<String> stdout, List<String> stderr) {
    public ExecResult {
        stdout = List.copyOf(stdout);
        stderr = List.copyOf(stderr);
    }

    public String stdoutText() {
        return String.join("\n", stdout);
    }

    public List<String> errors() {
        return stderr.stream().filter(line -> line.startsWith("Error")).toList();
    }

    public List<String> warnings() {
        return stderr.stream().filter(line -> line.startsWith("Warning")).toList();
    }

    public boolean hasErrors() {
        return !errors().isEmpty();
    }
}
