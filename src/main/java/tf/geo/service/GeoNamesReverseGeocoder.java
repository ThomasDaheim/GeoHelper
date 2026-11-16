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
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import tf.geo.LatLon;

/**
 * Reverse geocoding with GeoNames' findNearbyPlaceName - see https://www.geonames.org/export/web-services.html.
 * Needs a (free) GeoNames account enabled for the web services. A district of a city (feature code PPLX) becomes the
 * sublocation, if it is the nearest place.
 */
public final class GeoNamesReverseGeocoder implements ReverseGeocoder {
    private static final String SERVICE_URL = "https://secure.geonames.org/findNearbyPlaceNameJSON";
    // the free account has 1000 credits per hour - the pause keeps a run from using them up in seconds
    private static final Duration MIN_REQUEST_INTERVAL = Duration.ofMillis(250);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // the nearest places asked for - enough to get past the districts of a big city to the city itself
    private static final int MAX_ROWS = 10;
    // feature code of a district: "section of populated place"
    private static final String DISTRICT = "PPLX";

    // status values of GeoNames, see https://www.geonames.org/export/webservice-exception.html
    private static final int AUTHORIZATION = 10;
    private static final int DATABASE_TIMEOUT = 13;
    private static final int NO_RESULT = 15;
    private static final int DAILY_LIMIT = 18;
    private static final int HOURLY_LIMIT = 19;
    private static final int WEEKLY_LIMIT = 20;
    private static final int SERVER_OVERLOADED = 22;

    private final String username;
    private final String language;
    private final HttpTransport transport;

    /**
     * @param username GeoNames user name
     * @param language ISO 639-1 language code of the names, e.g. "de" - empty or null for the default of GeoNames
     * @param transport HTTP access
     */
    public GeoNamesReverseGeocoder(String username, String language, HttpTransport transport) {
        this.username = Objects.requireNonNull(username);
        this.language = language == null ? "" : language.trim();
        this.transport = Objects.requireNonNull(transport);
    }

    @Override
    public Optional<Place> reverse(LatLon position) throws GeoServiceException {
        final HttpTransport.Response response;
        try {
            response = transport.get(requestUri(position));
        } catch (IOException ex) {
            throw new GeoServiceException(GeoServiceException.Kind.RETRYABLE, "GeoNames not reachable: " + ex.getMessage(), ex);
        }
        final int status = response.status();
        if (status >= 500) {
            throw new GeoServiceException(GeoServiceException.Kind.RETRYABLE, "GeoNames busy (HTTP " + status + ")");
        }
        if (status != 200) {
            // e.g. 401 for an unknown user - GeoNames tells the reason in the body
            parseStatus(response.body());
            throw new GeoServiceException(GeoServiceException.Kind.FAILED, "GeoNames request failed (HTTP " + status + ")");
        }
        return parse(response.body());
    }

    @Override
    public Duration minRequestInterval() {
        return MIN_REQUEST_INTERVAL;
    }

    @Override
    public String name() {
        return "GeoNames";
    }

    /**
     * @param position position to look up
     * @return the request for this position
     */
    URI requestUri(LatLon position) {
        final StringBuilder uri = new StringBuilder(SERVICE_URL)
                .append("?lat=").append(number(position.latitude()))
                .append("&lng=").append(number(position.longitude()))
                // several places: the nearest is often a district (PPLX), the city comes later
                .append("&style=FULL&maxRows=").append(MAX_ROWS)
                .append("&username=").append(encode(username));
        if (!language.isEmpty()) {
            uri.append("&lang=").append(encode(language));
        }
        return URI.create(uri.toString());
    }

    /**
     * The city is the nearest place that isn't a district - a district (feature code PPLX, "section of populated
     * place", e.g. Eixample in Barcelona) is the sublocation if it is the nearest place of all.
     *
     * @param json answer of GeoNames with the nearest places, nearest first
     * @return the place, empty if GeoNames found none
     * @throws GeoServiceException if GeoNames reports an error (limit exceeded, unknown user, ...) or the answer isn't
     * JSON
     */
    static Optional<Place> parse(String json) throws GeoServiceException {
        final JsonNode root = parseStatus(json);
        if (root == null) {
            return Optional.empty();
        }
        final JsonNode places = root.path("geonames");
        if (!places.isArray() || places.isEmpty()) {
            return Optional.empty();
        }
        final JsonNode nearest = places.get(0);
        JsonNode city = null;
        for (JsonNode place : places) {
            if (!DISTRICT.equals(text(place, "fcode"))) {
                city = place;
                break;
            }
        }
        final String sublocation = DISTRICT.equals(text(nearest, "fcode")) ? text(nearest, "name") : null;
        // only districts nearby: country and state from the nearest one, no city
        final JsonNode area = city != null ? city : nearest;
        final Place place = new Place(text(area, "countryCode"), text(area, "countryName"), text(area, "adminName1"),
                city == null ? null : text(city, "name"), sublocation);
        return place.isEmpty() ? Optional.empty() : Optional.of(place);
    }

    /**
     * @param json answer of GeoNames
     * @return the parsed answer - null if GeoNames reports "no result"
     * @throws GeoServiceException if GeoNames reports an error or the answer isn't JSON
     */
    private static JsonNode parseStatus(String json) throws GeoServiceException {
        final JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new GeoServiceException(GeoServiceException.Kind.FAILED,
                    "GeoNames answered with invalid JSON: " + ex.getOriginalMessage(), ex);
        }
        final JsonNode status = root.path("status");
        if (status.isObject()) {
            final int value = status.path("value").asInt();
            if (value == NO_RESULT) {
                return null;
            }
            throw new GeoServiceException(kind(value), "GeoNames: " + status.path("message").asText("error " + value));
        }
        return root;
    }

    private static GeoServiceException.Kind kind(int status) {
        return switch (status) {
            case DAILY_LIMIT, HOURLY_LIMIT, WEEKLY_LIMIT -> GeoServiceException.Kind.QUOTA_EXCEEDED;
            case AUTHORIZATION -> GeoServiceException.Kind.CONFIGURATION;
            case DATABASE_TIMEOUT, SERVER_OVERLOADED -> GeoServiceException.Kind.RETRYABLE;
            default -> GeoServiceException.Kind.FAILED;
        };
    }

    private static String text(JsonNode node, String field) {
        final JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    // never in exponent notation like 1.0E-4
    private static String number(double value) {
        return BigDecimal.valueOf(value).toPlainString();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
