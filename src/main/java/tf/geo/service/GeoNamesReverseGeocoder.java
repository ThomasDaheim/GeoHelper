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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tf.geo.LatLon;

/**
 * Reverse geocoding with GeoNames' findNearbyPlaceName - see https://www.geonames.org/export/web-services.html.
 * Needs a (free) GeoNames account enabled for the web services. A district of a city (feature code PPLX) becomes the
 * sublocation, if it is the nearest place. Historical, abandoned and destroyed places (PPLCH, PPLH, PPLQ, PPLW) are
 * ignored. An answer with districts only gets the town from a second request for towns only.
 */
public final class GeoNamesReverseGeocoder implements ReverseGeocoder {
    private static final String SERVICE_URL = "https://secure.geonames.org/findNearbyPlaceNameJSON";
    // the free account has 1000 credits per hour - the pause keeps a run from using them up in seconds
    private static final Duration MIN_REQUEST_INTERVAL = Duration.ofMillis(250);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // the nearest places asked for - enough to get past the districts of a big city to the city itself (in the centre
    // of Barcelona the city is the 6th)
    private static final int MAX_ROWS = 30;
    // places within this distance in km
    private static final int RADIUS_KM = 10;
    // feature code of a district: "section of populated place"
    private static final String DISTRICT = "PPLX";
    // places nobody can be in today - historical capital (e.g. "Roman Catholic Diocese of Como"), historical populated
    // place (e.g. "Contea di Bergamo (medievale)"), abandoned, destroyed; GeoNames lists them among the nearby places
    // (comparison with GeoSetter 19.11.2026)
    private static final Set<String> HISTORICAL = Set.of("PPLCH", "PPLH", "PPLQ", "PPLW");
    // filter of findNearbyPlaceName: populated places with more than 1000 inhabitants - towns, no districts
    private static final String TOWNS = "cities1000";

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
        return nearby(position).stream().findFirst().map(NearbyPlace::place);
    }

    @Override
    public List<NearbyPlace> nearby(LatLon position) throws GeoServiceException {
        List<JsonNode> places = ask(requestUri(position, true));
        if (places.isEmpty()) {
            // nothing within the radius (desert, sea): the nearest place at all - one more request in such rare cases
            places = ask(requestUri(position, false));
        }
        if (!places.isEmpty() && places.stream().allMatch(GeoNamesReverseGeocoder::isDistrict)) {
            // only districts, e.g. at Milano Centrale the 30 nearest places are districts of Milan: the towns nearby
            // in a request of their own (test folder 19.11.2026)
            final List<JsonNode> withTowns = new ArrayList<>(places);
            withTowns.addAll(ask(townsUri(position)));
            places = withTowns;
        }
        return build(places);
    }

    private List<JsonNode> ask(URI uri) throws GeoServiceException {
        final HttpTransport.Response response;
        try {
            response = transport.get(uri);
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
        return places(response.body());
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
     * @return the request for the places within the radius around this position
     */
    URI requestUri(LatLon position) {
        return requestUri(position, true);
    }

    /**
     * @param position position to look up
     * @param withRadius true for the places within the radius, false for the nearest place only
     * @return the request for this position
     */
    URI requestUri(LatLon position, boolean withRadius) {
        final StringBuilder uri = new StringBuilder(SERVICE_URL)
                .append("?lat=").append(number(position.latitude()))
                .append("&lng=").append(number(position.longitude()))
                .append("&style=FULL");
        if (withRadius) {
            // several places: the nearest are often districts (PPLX), the city comes later - without radius GeoNames
            // answers with the nearest place only, whatever maxRows says (checked 16.11.2026)
            uri.append("&radius=").append(RADIUS_KM).append("&maxRows=").append(MAX_ROWS);
        }
        return withUserAndLanguage(uri);
    }

    /**
     * @param position position to look up
     * @return the request for the towns (more than 1000 inhabitants, no districts) within the radius
     */
    URI townsUri(LatLon position) {
        final StringBuilder uri = new StringBuilder(SERVICE_URL)
                .append("?lat=").append(number(position.latitude()))
                .append("&lng=").append(number(position.longitude()))
                .append("&style=FULL")
                .append("&radius=").append(RADIUS_KM).append("&maxRows=").append(MAX_ROWS)
                .append("&cities=").append(TOWNS);
        return withUserAndLanguage(uri);
    }

    private URI withUserAndLanguage(StringBuilder uri) {
        uri.append("&username=").append(encode(username));
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
        return parseNearby(json).stream().findFirst().map(NearbyPlace::place);
    }

    /**
     * All places of the answer to choose from: first the best answer (see parse()), then each district with the
     * nearest city and each city without district, by distance - without duplicates.
     *
     * @param json answer of GeoNames with the nearest places, nearest first
     * @return the places, empty if GeoNames found none
     * @throws GeoServiceException if GeoNames reports an error or the answer isn't JSON
     */
    static List<NearbyPlace> parseNearby(String json) throws GeoServiceException {
        return build(places(json));
    }

    // the places of the answer, nearest first - without historical, abandoned and destroyed ones
    private static List<JsonNode> places(String json) throws GeoServiceException {
        final JsonNode root = parseStatus(json);
        final List<JsonNode> places = new ArrayList<>();
        if (root == null) {
            return places;
        }
        root.path("geonames").forEach(place -> {
            final String code = text(place, "fcode");
            // without a feature code the place counts - Set.of() doesn't allow contains(null)
            if (code == null || !HISTORICAL.contains(code)) {
                places.add(place);
            }
        });
        return places;
    }

    // see parseNearby()
    private static List<NearbyPlace> build(List<JsonNode> places) {
        if (places.isEmpty()) {
            return List.of();
        }
        final JsonNode nearest = places.get(0);
        JsonNode city = null;
        for (JsonNode place : places) {
            if (!isDistrict(place)) {
                city = place;
                break;
            }
        }
        final String cityName = city == null ? null : text(city, "name");
        // only districts nearby: country and state from the nearest one, no city
        final JsonNode area = city != null ? city : nearest;

        final List<NearbyPlace> nearby = new ArrayList<>();
        final Place best = new Place(text(area, "countryCode"), text(area, "countryName"), text(area, "adminName1"), cityName,
                isDistrict(nearest) ? text(nearest, "name") : null);
        if (!best.isEmpty()) {
            nearby.add(new NearbyPlace(best, distance(isDistrict(nearest) || city == null ? nearest : city)));
        }
        for (JsonNode place : places) {
            final Place candidate = isDistrict(place)
                    ? new Place(text(area, "countryCode"), text(area, "countryName"), text(area, "adminName1"), cityName,
                            text(place, "name"))
                    : new Place(text(place, "countryCode"), text(place, "countryName"), text(place, "adminName1"),
                            text(place, "name"), null);
            if (!candidate.isEmpty() && nearby.stream().noneMatch(known -> known.place().equals(candidate))) {
                nearby.add(new NearbyPlace(candidate, distance(place)));
            }
        }
        return List.copyOf(nearby);
    }

    private static boolean isDistrict(JsonNode place) {
        return DISTRICT.equals(text(place, "fcode"));
    }

    // GeoNames sends the distance in km as text
    private static double distance(JsonNode place) {
        try {
            return Double.parseDouble(Objects.requireNonNullElse(text(place, "distance"), "0"));
        } catch (NumberFormatException ex) {
            return 0;
        }
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
