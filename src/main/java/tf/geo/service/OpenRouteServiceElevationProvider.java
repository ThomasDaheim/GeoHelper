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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import tf.geo.LatLon;

/**
 * Elevation from openrouteservice (https://openrouteservice.org/dev/#/api-docs/elevation) - like GPXEditor's
 * OpenElevationService: one point via /elevation/point, several via /elevation/line. Needs an API key; the free key
 * allows 40 requests per minute. At most BLOCK_SIZE points per call - the caller splits and waits
 * minRequestInterval() between the calls.
 */
public final class OpenRouteServiceElevationProvider implements ElevationProvider {
    /** points per request at most */
    public static final int BLOCK_SIZE = 1000;

    private static final URI POINT_URL = URI.create("https://api.openrouteservice.org/elevation/point");
    private static final URI LINE_URL = URI.create("https://api.openrouteservice.org/elevation/line");
    // 40 requests per minute with the free key
    private static final Duration MIN_REQUEST_INTERVAL = Duration.ofMillis(1500);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final HttpTransport transport;

    /**
     * @param apiKey openrouteservice API key
     * @param transport HTTP access
     */
    public OpenRouteServiceElevationProvider(String apiKey, HttpTransport transport) {
        this.apiKey = Objects.requireNonNull(apiKey);
        this.transport = Objects.requireNonNull(transport);
    }

    /**
     * @return pause the caller should keep between two calls
     */
    public Duration minRequestInterval() {
        return MIN_REQUEST_INTERVAL;
    }

    /**
     * @param positions at most BLOCK_SIZE positions
     * @return elevation per position - empty where openrouteservice has no data
     * @throws GeoServiceException if the request failed - QUOTA_EXCEEDED, CONFIGURATION (key rejected), RETRYABLE or
     * FAILED
     * @throws IllegalArgumentException for more than BLOCK_SIZE positions
     */
    @Override
    public List<OptionalDouble> elevations(List<LatLon> positions) throws GeoServiceException {
        if (positions.size() > BLOCK_SIZE) {
            throw new IllegalArgumentException("At most " + BLOCK_SIZE + " positions per request, not " + positions.size());
        }
        if (positions.isEmpty()) {
            return List.of();
        }
        final boolean single = positions.size() == 1;
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("format_in", single ? "point" : "polyline");
        body.put("format_out", "geojson");
        if (single) {
            body.set("geometry", coordinates(positions.get(0)));
        } else {
            final ArrayNode line = body.putArray("geometry");
            positions.forEach(position -> line.add(coordinates(position)));
        }

        final HttpTransport.Response response;
        try {
            response = transport.post(single ? POINT_URL : LINE_URL, body.toString(), Map.of("Authorization", apiKey));
        } catch (IOException ex) {
            throw new GeoServiceException(GeoServiceException.Kind.RETRYABLE, "openrouteservice not reachable: " + ex.getMessage(), ex);
        }
        final int status = response.status();
        if (status == 401 || status == 403) {
            if (response.body() != null && response.body().contains("Quota exceeded")) {
                throw new GeoServiceException(GeoServiceException.Kind.QUOTA_EXCEEDED, "openrouteservice: quota exceeded");
            }
            throw new GeoServiceException(GeoServiceException.Kind.CONFIGURATION,
                    "openrouteservice API key rejected (HTTP " + status + ")");
        }
        if (status == 429 || status >= 500) {
            throw new GeoServiceException(GeoServiceException.Kind.RETRYABLE, "openrouteservice busy (HTTP " + status + ")");
        }
        if (status == 404) {
            // outside the data of openrouteservice
            return empty(positions.size());
        }
        if (status != 200 && status != 201) {
            throw new GeoServiceException(GeoServiceException.Kind.FAILED, "openrouteservice request failed (HTTP " + status + ")");
        }
        return parse(response.body(), positions.size(), single);
    }

    @Override
    public String name() {
        return "openrouteservice";
    }

    private static ArrayNode coordinates(LatLon position) {
        return MAPPER.createArrayNode().add(position.longitude()).add(position.latitude());
    }

    // geometry.coordinates: [lon, lat, ele] for a point, a list of those for a line
    private static List<OptionalDouble> parse(String json, int count, boolean single) throws GeoServiceException {
        final JsonNode coordinates;
        try {
            coordinates = MAPPER.readTree(json).path("geometry").path("coordinates");
        } catch (JsonProcessingException ex) {
            throw new GeoServiceException(GeoServiceException.Kind.FAILED,
                    "openrouteservice answered with invalid JSON: " + ex.getOriginalMessage(), ex);
        }
        final List<OptionalDouble> result = new ArrayList<>(count);
        if (single) {
            result.add(elevation(coordinates));
        } else {
            for (int i = 0; i < count; i++) {
                result.add(elevation(coordinates.path(i)));
            }
        }
        return result;
    }

    private static OptionalDouble elevation(JsonNode point) {
        final JsonNode elevation = point.path(2);
        return elevation.isNumber() ? OptionalDouble.of(elevation.asDouble()) : OptionalDouble.empty();
    }

    private static List<OptionalDouble> empty(int count) {
        final List<OptionalDouble> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(OptionalDouble.empty());
        }
        return result;
    }
}
