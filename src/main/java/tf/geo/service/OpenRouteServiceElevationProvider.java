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
import java.util.Locale;
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
    // degrees between the position asked for and the one answered
    private static final double POSITION_TOLERANCE = 1e-3;

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
            throw new GeoServiceException(GeoServiceException.Kind.RETRYABLE, "openrouteservice not reachable: " + GeoServiceException.describe(ex), ex);
        }
        final int status = response.status();
        // what openrouteservice says - appended to our messages (plan 7)
        final String serviceText = errorText(response.body());
        // also a plain text answer like "Quota exceeded"
        final boolean quota = response.body() != null && response.body().toLowerCase(Locale.ROOT).contains("quota");
        if (status == 401 || status == 403) {
            if (quota) {
                throw new GeoServiceException(GeoServiceException.Kind.QUOTA_EXCEEDED, "openrouteservice: quota exceeded" + suffix(serviceText));
            }
            throw new GeoServiceException(GeoServiceException.Kind.CONFIGURATION,
                    "openrouteservice API key rejected (HTTP " + status + ")" + suffix(serviceText));
        }
        if (status == 429 && quota) {
            // a used-up daily quota may come as 429 - waiting a few seconds won't help
            throw new GeoServiceException(GeoServiceException.Kind.QUOTA_EXCEEDED, "openrouteservice: quota exceeded" + suffix(serviceText));
        }
        if (status == 429 || status >= 500) {
            throw new GeoServiceException(GeoServiceException.Kind.RETRYABLE,
                    "openrouteservice busy (HTTP " + status + ")" + suffix(serviceText));
        }
        if (status == 404) {
            // outside the data of openrouteservice
            return empty(positions.size());
        }
        if (status != 200 && status != 201) {
            throw new GeoServiceException(GeoServiceException.Kind.FAILED,
                    "openrouteservice request failed (HTTP " + status + ")" + suffix(serviceText));
        }
        return parse(response.body(), positions, single);
    }

    @Override
    public String name() {
        return "openrouteservice";
    }

    // the error text of an answer: {"error": "text"} or {"error": {"code": 2003, "message": "text"}} - empty if none
    private static String errorText(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            final JsonNode error = MAPPER.readTree(body).path("error");
            if (error.isTextual()) {
                return error.asText().trim();
            }
            return error.path("message").isTextual() ? error.path("message").asText().trim() : "";
        } catch (JsonProcessingException ex) {
            // e.g. an HTML page of a proxy
            return "";
        }
    }

    private static String suffix(String text) {
        return text.isEmpty() ? "" : ": " + text;
    }

    private static ArrayNode coordinates(LatLon position) {
        return MAPPER.createArrayNode().add(position.longitude()).add(position.latitude());
    }

    // geometry.coordinates: [lon, lat, ele] for a point, a list of those for a line - matched by index, so each point
    // must be the one asked for (final review 5b): otherwise files would get the altitude of their neighbours
    private static List<OptionalDouble> parse(String json, List<LatLon> positions, boolean single) throws GeoServiceException {
        final JsonNode coordinates;
        try {
            coordinates = MAPPER.readTree(json).path("geometry").path("coordinates");
        } catch (JsonProcessingException ex) {
            throw new GeoServiceException(GeoServiceException.Kind.FAILED,
                    "openrouteservice answered with invalid JSON: " + ex.getOriginalMessage(), ex);
        }
        if (!single && (!coordinates.isArray() || coordinates.size() != positions.size())) {
            throw new GeoServiceException(GeoServiceException.Kind.FAILED, "openrouteservice answered with "
                    + coordinates.size() + " points for " + positions.size());
        }
        final List<OptionalDouble> result = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            final JsonNode point = single ? coordinates : coordinates.path(i);
            if (!matches(point, positions.get(i))) {
                throw new GeoServiceException(GeoServiceException.Kind.FAILED,
                        "openrouteservice answered for another position than " + positions.get(i));
            }
            result.add(elevation(point));
        }
        return result;
    }

    // ORS rounds the coordinates - a thousandth of a degree (about 100 m) is far closer than the next photo usually is
    private static boolean matches(JsonNode point, LatLon position) {
        return point.path(0).isNumber() && point.path(1).isNumber()
                && Math.abs(point.path(0).asDouble() - position.longitude()) < POSITION_TOLERANCE
                && Math.abs(point.path(1).asDouble() - position.latitude()) < POSITION_TOLERANCE;
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
