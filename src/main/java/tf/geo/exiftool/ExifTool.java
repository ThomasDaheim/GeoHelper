/*
 * Copyright (c) 2014ff Thomas Feuster
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package tf.geo.exiftool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * Reads the given tags of all files with "-json -n -G1": numbers are numeric, keys are "Group1:Tag".
     * Files ExifTool can't read are missing in the result or only carry an error entry.
     */
    public List<Map<String, Object>> readJson(List<Path> files, List<String> tags) {
        if (files.isEmpty()) {
            return List.of();
        }
        final List<String> args = new ArrayList<>(List.of("-json", "-n", "-G1"));
        tags.forEach(tag -> args.add("-" + tag));
        files.forEach(file -> args.add(file.toAbsolutePath().toString()));
        final String json = execute(args).stdoutText().trim();
        if (json.isEmpty()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException ex) {
            throw new ExifToolException("Can't parse ExifTool output", ex);
        }
    }

    /**
     * Writes a binary tag (e.g. PreviewImage, JpgFromRaw, ThumbnailImage) to target.
     * Returns false if the file doesn't contain the tag.
     */
    public boolean extractBinary(Path file, String tag, Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        // -W! writes the output to the file named by the format string ("%" needs escaping there)
        final String format = target.toAbsolutePath().toString().replace("%", "%%");
        final ExecResult result = execute(List.of("-b", "-" + tag, "-W!", format, file.toAbsolutePath().toString()));
        return !result.hasErrors() && Files.isRegularFile(target);
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
