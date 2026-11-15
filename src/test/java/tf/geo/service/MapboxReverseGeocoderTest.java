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

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tf.geo.LatLon;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapboxReverseGeocoderTest {
    // shaped like the example of the Mapbox Geocoding v6 documentation, reduced to what is read
    private static final String ADDRESS_IN_MERAN = """
            {"type": "FeatureCollection", "features": [{"type": "Feature", "properties": {
              "feature_type": "address", "name": "Laurinstraße 5",
              "context": {
                "neighborhood": {"name": "Maria Trost"},
                "locality": {"name": "Obermais"},
                "place": {"name": "Meran"},
                "region": {"name": "Trentino-Südtirol", "region_code": "BZ"},
                "country": {"name": "Italien", "country_code": "it"}}}}]}
            """;
    private static final String NEIGHBORHOOD_ONLY = """
            {"type": "FeatureCollection", "features": [{"type": "Feature", "properties": {
              "feature_type": "street", "name": "Laurinstraße",
              "context": {
                "neighborhood": {"name": "Maria Trost"},
                "place": {"name": "Meran"},
                "region": {"name": "Trentino-Südtirol"},
                "country": {"name": "Italien", "country_code": "IT"}}}}]}
            """;
    private static final String PLACE_MOAB = """
            {"type": "FeatureCollection", "features": [{"type": "Feature", "properties": {
              "feature_type": "place", "name": "Moab",
              "context": {
                "region": {"name": "Utah", "region_code": "UT"},
                "country": {"name": "United States", "country_code": "US"}}}}]}
            """;

    private static MapboxReverseGeocoder geocoder(String language, HttpTransport transport) {
        return new MapboxReverseGeocoder("tok", language, transport);
    }

    @Test
    void buildsTheReverseRequest() {
        assertEquals(URI.create("https://api.mapbox.com/search/geocode/v6/reverse?longitude=11.16&latitude=46.67"
                + "&access_token=tok&language=de"),
                geocoder("de", FakeTransport.answering(200, "")).requestUri(new LatLon(46.67, 11.16)));
    }

    @Test
    void emptyLanguageSendsNoLanguage() {
        final URI uri = geocoder("", FakeTransport.answering(200, "")).requestUri(new LatLon(-33.875, -151.2));
        assertEquals(URI.create("https://api.mapbox.com/search/geocode/v6/reverse?longitude=-151.2&latitude=-33.875"
                + "&access_token=tok"), uri);
    }

    @Test
    void readsPlaceFromTheContext() throws GeoServiceException {
        assertEquals(Optional.of(new Place("IT", "Italien", "Trentino-Südtirol", "Meran", "Obermais")),
                geocoder("de", FakeTransport.answering(200, ADDRESS_IN_MERAN)).reverse(new LatLon(46.67, 11.16)));
    }

    @Test
    void neighborhoodWhenThereIsNoLocality() {
        assertEquals(Optional.of(new Place("IT", "Italien", "Trentino-Südtirol", "Meran", "Maria Trost")),
                MapboxReverseGeocoder.parse(NEIGHBORHOOD_ONLY));
    }

    @Test
    void placeFeatureIsTheCityItself() {
        assertEquals(Optional.of(new Place("US", "United States", "Utah", "Moab", null)), MapboxReverseGeocoder.parse(PLACE_MOAB));
    }

    @Test
    void noFeaturesMeansNoPlace() {
        assertFalse(MapboxReverseGeocoder.parse("{\"type\": \"FeatureCollection\", \"features\": []}").isPresent());
    }

    @Test
    void httpProblemsBecomeKinds() {
        final LatLon position = new LatLon(46.67, 11.16);
        assertEquals(GeoServiceException.Kind.CONFIGURATION, assertThrows(GeoServiceException.class,
                () -> geocoder("de", FakeTransport.answering(401, "{}")).reverse(position)).kind());
        assertEquals(GeoServiceException.Kind.RETRYABLE, assertThrows(GeoServiceException.class,
                () -> geocoder("de", FakeTransport.answering(429, "{}")).reverse(position)).kind());
        assertEquals(GeoServiceException.Kind.RETRYABLE, assertThrows(GeoServiceException.class,
                () -> geocoder("de", FakeTransport.answering(503, "{}")).reverse(position)).kind());
        assertEquals(GeoServiceException.Kind.FAILED, assertThrows(GeoServiceException.class,
                () -> geocoder("de", FakeTransport.answering(422, "{}")).reverse(position)).kind());
        assertEquals(GeoServiceException.Kind.RETRYABLE, assertThrows(GeoServiceException.class,
                () -> geocoder("de", FakeTransport.failing()).reverse(position)).kind());
    }
}
