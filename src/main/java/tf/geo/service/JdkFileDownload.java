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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Downloads with the JDK's HttpClient - large files (SRTM blocks have up to about 40 MB). The timeout of the JDK ends
 * with the response headers, so the body is watched as well: no new data for a while ends the download.
 */
public final class JdkFileDownload implements FileDownload {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    // until the response headers arrive
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(1);
    // no new data in the body for this long: stalled
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(1);
    private static final long CHECK_MILLIS = 250;
    // a generic name - no personal data such as an e-mail address
    private static final String USER_AGENT = "GeoHelper";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Duration idleTimeout;

    /**
     * Downloads that end after a minute without new data.
     */
    public JdkFileDownload() {
        this(IDLE_TIMEOUT);
    }

    /**
     * @param idleTimeout no new data for this long ends the download - shorter in tests
     */
    JdkFileDownload(Duration idleTimeout) {
        this.idleTimeout = Objects.requireNonNull(idleTimeout);
    }

    /**
     * @param uri what to download
     * @param target where to put it
     * @throws IOException if the server isn't reachable, doesn't answer with HTTP 200, or the download stalls
     */
    @Override
    public void download(URI uri, Path target) throws IOException {
        final HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        final CompletableFuture<HttpResponse<Path>> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofFile(target));
        long size = -1;
        long lastProgress = System.nanoTime();
        try {
            while (true) {
                try {
                    final int status = response.get(CHECK_MILLIS, TimeUnit.MILLISECONDS).statusCode();
                    if (status != 200) {
                        throw new IOException("Download of " + uri + " failed (HTTP " + status + ")");
                    }
                    return;
                } catch (TimeoutException ex) {
                    final long current = Files.exists(target) ? Files.size(target) : 0;
                    if (current != size) {
                        size = current;
                        lastProgress = System.nanoTime();
                    } else if (System.nanoTime() - lastProgress > idleTimeout.toNanos()) {
                        response.cancel(true);
                        throw new IOException("Download of " + uri + " stalled - no data for " + idleTimeout.toSeconds() + " s");
                    }
                }
            }
        } catch (ExecutionException ex) {
            final Throwable cause = ex.getCause();
            throw cause instanceof IOException io ? io : new IOException("Download of " + uri + " failed: " + cause, cause);
        } catch (InterruptedException ex) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Download of " + uri + " interrupted", ex);
        }
    }
}
