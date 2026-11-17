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
package tf.geo.service;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Downloads from a local server - no internet.
 */
class JdkFileDownloadTest {
    @TempDir
    Path temp;

    private HttpServer server;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", exchange -> {
            final byte[] body = {1, 2, 3, 4};
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        // headers and a few bytes, then nothing - like a connection that stalls in a hotel WiFi
        server.createContext("/stall", exchange -> {
            exchange.sendResponseHeaders(200, 1_000_000);
            final OutputStream out = exchange.getResponseBody();
            out.write(new byte[10]);
            out.flush();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    @Test
    void downloadsTheBody() throws IOException {
        final Path target = temp.resolve("ok.zip");
        new JdkFileDownload(Duration.ofSeconds(5)).download(uri("/ok"), target);
        assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(target));
    }

    @Test
    void stalledDownloadFails() {
        // final review 5b: the request timeout of the JDK ends with the headers - without an own check the window
        // stayed busy for good and couldn't be closed
        final Path target = temp.resolve("stall.zip");
        final IOException ex = assertTimeoutPreemptively(Duration.ofSeconds(15), () -> assertThrows(IOException.class,
                () -> new JdkFileDownload(Duration.ofSeconds(1)).download(uri("/stall"), target)));
        assertTrue(ex.getMessage().contains("stalled"), ex.getMessage());
    }
}
