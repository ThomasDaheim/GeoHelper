package tf.geo.exiftool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * One long running ExifTool process ("-stay_open True -@ -") that executes commands one after the other.
 *
 * Protocol: the arguments of a command are written to stdin one per line, followed by "-execute<n>".
 * ExifTool ends its stdout output with "{ready<n>}". "-echo4 {ready<n>}" prints the same marker to stderr after
 * the command, so both streams can be read up to the end of the command.
 *
 * If the process crashed or doesn't answer within the timeout, it is restarted and the command is retried once.
 */
public class ExifTool implements AutoCloseable {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    // put into the line queues when a stream ends - can't occur in ExifTool output
    private static final String END_OF_STREAM = "\u0000EOS";

    private final Path executable;
    private final Duration timeout;

    private Process process;
    private BufferedWriter stdin;
    private BlockingQueue<String> stdoutLines;
    private BlockingQueue<String> stderrLines;
    private int commandCounter = 0;

    public ExifTool(Path executable) {
        this(executable, DEFAULT_TIMEOUT);
    }

    public ExifTool(Path executable, Duration timeout) {
        this.executable = Objects.requireNonNull(executable);
        this.timeout = Objects.requireNonNull(timeout);
    }

    public synchronized ExecResult execute(List<String> args) {
        try {
            return executeOnce(args);
        } catch (ExifToolException ex) {
            // process crashed or hangs: restart and retry once
            stopQuietly();
            return executeOnce(args);
        }
    }

    public String version() {
        final ExecResult result = execute(List.of("-ver"));
        if (result.stdout().isEmpty()) {
            throw new ExifToolException("ExifTool didn't report a version: " + result);
        }
        return result.stdout().get(0).trim();
    }

    @Override
    public synchronized void close() {
        if (process == null) {
            return;
        }
        if (process.isAlive()) {
            try {
                stdin.write("-stay_open\nFalse\n");
                stdin.flush();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (IOException ex) {
                process.destroyForcibly();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        process = null;
    }

    // for tests only
    synchronized Process process() {
        return process;
    }

    private ExecResult executeOnce(List<String> args) {
        ensureStarted();
        final int id = ++commandCounter;
        final String marker = "{ready" + id + "}";
        try {
            for (String arg : args) {
                if (arg.indexOf('\n') >= 0 || arg.indexOf('\r') >= 0) {
                    throw new IllegalArgumentException("ExifTool arguments must not contain line breaks: " + arg);
                }
                stdin.write(arg);
                stdin.write('\n');
            }
            stdin.write("-echo4\n" + marker + "\n-execute" + id + "\n");
            stdin.flush();
        } catch (IOException ex) {
            throw new ExifToolException("Can't send command to ExifTool", ex);
        }
        final long deadline = System.nanoTime() + timeout.toNanos();
        final List<String> out = readUntil(stdoutLines, marker, deadline);
        final List<String> err = readUntil(stderrLines, marker, deadline);
        return new ExecResult(out, err);
    }

    private List<String> readUntil(BlockingQueue<String> lines, String marker, long deadline) {
        final List<String> result = new ArrayList<>();
        while (true) {
            final String line;
            try {
                line = lines.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ExifToolException("Interrupted while waiting for ExifTool", ex);
            }
            if (line == null) {
                throw new ExifToolException("ExifTool didn't answer within " + timeout);
            }
            if (END_OF_STREAM.equals(line)) {
                throw new ExifToolException("ExifTool terminated unexpectedly");
            }
            if (line.equals(marker)) {
                return result;
            }
            result.add(line);
        }
    }

    private void ensureStarted() {
        if (process != null && process.isAlive()) {
            return;
        }
        stopQuietly();
        // -common_args: filenames are passed as UTF-8 (umlauts in Windows paths)
        final List<String> command = List.of(executable.toString(),
                "-stay_open", "True", "-@", "-", "-common_args", "-charset", "filename=utf8");
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException ex) {
            process = null;
            throw new ExifToolException("Can't start ExifTool " + executable, ex);
        }
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        stdoutLines = pump(process.getInputStream(), "stdout");
        stderrLines = pump(process.getErrorStream(), "stderr");
    }

    private void stopQuietly() {
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
    }

    private static BlockingQueue<String> pump(InputStream stream, String name) {
        final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        final Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    queue.add(line);
                }
            } catch (IOException ex) {
                // stream closed - process ended
            } finally {
                queue.add(END_OF_STREAM);
            }
        }, "ExifTool-" + name);
        thread.setDaemon(true);
        thread.start();
        return queue;
    }
}
