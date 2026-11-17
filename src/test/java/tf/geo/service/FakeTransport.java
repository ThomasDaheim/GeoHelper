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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP without network for the tests: always the same answer or the same exception, the requested URIs are kept.
 */
final class FakeTransport implements HttpTransport {
    private final List<Response> responses;
    private final IOException failure;
    private final List<URI> requests = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private Map<String, String> lastHeaders = Map.of();

    private FakeTransport(List<Response> responses, IOException failure) {
        this.responses = responses;
        this.failure = failure;
    }

    /**
     * @param bodies bodies of the answers with status 200, in this order - the last one repeats
     * @return a transport answering with these
     */
    static FakeTransport answeringInTurn(String... bodies) {
        return new FakeTransport(List.of(bodies).stream().map(body -> new Response(200, body)).toList(), null);
    }

    /**
     * @param status HTTP status of every answer
     * @param body body of every answer
     * @return a transport answering always with these
     */
    static FakeTransport answering(int status, String body) {
        return new FakeTransport(List.of(new Response(status, body)), null);
    }

    /**
     * @return a transport failing always with an IOException, like a lost connection
     */
    static FakeTransport failing() {
        return new FakeTransport(List.of(), new IOException("connection reset"));
    }

    /**
     * @return the URIs requested so far
     */
    List<URI> requests() {
        return requests;
    }

    /**
     * @return the bodies of the POST requests so far
     */
    List<String> bodies() {
        return bodies;
    }

    /**
     * @return the headers of the last POST request
     */
    Map<String, String> lastHeaders() {
        return lastHeaders;
    }

    @Override
    public Response get(URI uri) throws IOException {
        requests.add(uri);
        if (failure != null) {
            throw failure;
        }
        return responses.get(Math.min(requests.size(), responses.size()) - 1);
    }

    @Override
    public Response post(URI uri, String json, Map<String, String> headers) throws IOException {
        bodies.add(json);
        lastHeaders = Map.copyOf(headers);
        return get(uri);
    }
}
