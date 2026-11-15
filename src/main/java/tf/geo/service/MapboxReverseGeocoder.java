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
 * Reverse geocoding with the Mapbox Geocoding API v6 - see https://docs.mapbox.com/api/search/geocoding/.
 * Unlike GPXEditor no feature types are requested: Mapbox then answers with the most precise feature it has, also far
 * away from any address (e.g. a national park).
 */
public final class MapboxReverseGeocoder implements ReverseGeocoder {
    private static final String SERVICE_URL = "https://api.mapbox.com/search/geocode/v6/reverse";
    // Mapbox allows 1000 requests per minute
    private static final Duration MIN_REQUEST_INTERVAL = Duration.ofMillis(100);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String accessToken;
    private final String language;
    private final HttpTransport transport;

    /**
     * @param accessToken Mapbox access token
     * @param language IETF language code of the names, e.g. "de" - empty or null for the default of Mapbox
     * @param transport HTTP access
     */
    public MapboxReverseGeocoder(String accessToken, String language, HttpTransport transport) {
        this.accessToken = Objects.requireNonNull(accessToken);
        this.language = language == null ? "" : language.trim();
        this.transport = Objects.requireNonNull(transport);
    }

    @Override
    public Optional<Place> reverse(LatLon position) throws GeoServiceException {
        final HttpTransport.Response response;
        try {
            response = transport.get(requestUri(position));
        } catch (IOException ex) {
            throw new GeoServiceException(GeoServiceException.Kind.RETRYABLE, "Mapbox not reachable: " + ex.getMessage(), ex);
        }
        final int status = response.status();
        if (status == 401 || status == 403) {
            throw new GeoServiceException(GeoServiceException.Kind.CONFIGURATION, "Mapbox access token rejected (HTTP " + status + ")");
        }
        if (status == 429 || status >= 500) {
            throw new GeoServiceException(GeoServiceException.Kind.RETRYABLE, "Mapbox busy (HTTP " + status + ")");
        }
        if (status != 200) {
            throw new GeoServiceException(GeoServiceException.Kind.FAILED, "Mapbox request failed (HTTP " + status + ")");
        }
        try {
            return parse(response.body());
        } catch (IllegalArgumentException ex) {
            throw new GeoServiceException(GeoServiceException.Kind.FAILED, ex.getMessage(), ex);
        }
    }

    @Override
    public Duration minRequestInterval() {
        return MIN_REQUEST_INTERVAL;
    }

    @Override
    public String name() {
        return "Mapbox";
    }

    /**
     * @param position position to look up
     * @return the request for this position
     */
    URI requestUri(LatLon position) {
        final StringBuilder uri = new StringBuilder(SERVICE_URL)
                .append("?longitude=").append(number(position.longitude()))
                .append("&latitude=").append(number(position.latitude()))
                .append("&access_token=").append(encode(accessToken));
        if (!language.isEmpty()) {
            uri.append("&language=").append(encode(language));
        }
        return URI.create(uri.toString());
    }

    /**
     * Reads the place from the first feature: its context, and the feature itself if it is a country, region, place,
     * locality or neighborhood.
     *
     * @param json answer of Mapbox
     * @return the place, empty if Mapbox found no feature
     * @throws IllegalArgumentException if the answer isn't JSON
     */
    static Optional<Place> parse(String json) {
        final JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Mapbox answered with invalid JSON: " + ex.getOriginalMessage(), ex);
        }
        final JsonNode features = root.path("features");
        if (!features.isArray() || features.isEmpty()) {
            return Optional.empty();
        }
        final JsonNode properties = features.get(0).path("properties");
        final JsonNode context = properties.path("context");
        String countryCode = text(context.path("country").path("country_code"));
        String country = text(context.path("country").path("name"));
        String state = text(context.path("region").path("name"));
        String city = text(context.path("place").path("name"));
        String sublocation = text(context.path("locality").path("name"));
        if (sublocation == null) {
            sublocation = text(context.path("neighborhood").path("name"));
        }
        // the feature itself is not part of its context
        final String name = text(properties.path("name"));
        switch (Objects.requireNonNullElse(text(properties.path("feature_type")), "")) {
            case "country" -> country = country == null ? name : country;
            case "region" -> state = state == null ? name : state;
            case "place" -> city = city == null ? name : city;
            case "locality", "neighborhood" -> sublocation = sublocation == null ? name : sublocation;
            default -> {
                // address, street, postcode, ...: only the context counts
            }
        }
        final Place place = new Place(countryCode, country, state, city, sublocation);
        return place.isEmpty() ? Optional.empty() : Optional.of(place);
    }

    private static String text(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    // never in exponent notation like 1.0E-4
    private static String number(double value) {
        return BigDecimal.valueOf(value).toPlainString();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
