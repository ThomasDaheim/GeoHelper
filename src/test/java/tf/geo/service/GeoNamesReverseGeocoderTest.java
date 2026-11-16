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
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoNamesReverseGeocoderTest {
    private static final LatLon MERAN = new LatLon(46.67, 11.16);

    private static GeoNamesReverseGeocoder geocoder(HttpTransport transport) {
        return new GeoNamesReverseGeocoder("demo", "de", transport);
    }

    // GeoNames reports errors with HTTP 200 and a status object
    private static String status(int value, String message) {
        return "{\"status\": {\"message\": \"" + message + "\", \"value\": " + value + "}}";
    }

    @Test
    void buildsTheRequest() {
        assertEquals(URI.create("https://secure.geonames.org/findNearbyPlaceNameJSON?lat=46.67&lng=11.16&style=FULL"
                + "&maxRows=10&username=demo&lang=de"), geocoder(FakeTransport.answering(200, "")).requestUri(MERAN));
        // without language GeoNames uses its default
        assertFalse(new GeoNamesReverseGeocoder("demo", " ", FakeTransport.answering(200, "")).requestUri(MERAN)
                .toString().contains("lang="));
    }

    @Test
    void readsPlace() throws GeoServiceException {
        final String json = """
                {"geonames": [{"name": "Meran", "countryCode": "IT", "countryName": "Italien",
                  "adminName1": "Trentino-Südtirol", "adminName2": "Bozen", "lat": "46.66817", "lng": "11.15953"}]}
                """;
        assertEquals(Optional.of(new Place("IT", "Italien", "Trentino-Südtirol", "Meran", null)),
                geocoder(FakeTransport.answering(200, json)).reverse(MERAN));
    }

    @Test
    void sectionOfACityIsTheSublocation() throws GeoServiceException {
        // answer at Plaça de Catalunya, shortened - districts are "sections of populated places" (PPLX)
        final String json = """
                {"geonames": [
                  {"name": "Eixample", "fcode": "PPLX", "countryCode": "ES", "countryName": "Spanien", "adminName1": "Katalonien"},
                  {"name": "el Raval", "fcode": "PPLX", "countryCode": "ES", "countryName": "Spanien", "adminName1": "Katalonien"},
                  {"name": "Barcelona", "fcode": "PPLA", "countryCode": "ES", "countryName": "Spanien", "adminName1": "Katalonien"}]}
                """;
        assertEquals(Optional.of(new Place("ES", "Spanien", "Katalonien", "Barcelona", "Eixample")),
                geocoder(FakeTransport.answering(200, json)).reverse(MERAN));
    }

    @Test
    void cityAloneHasNoSublocation() throws GeoServiceException {
        final String json = """
                {"geonames": [
                  {"name": "Kingman", "fcode": "PPLA2", "countryCode": "US", "countryName": "USA", "adminName1": "Arizona"},
                  {"name": "Butler", "fcode": "PPL", "countryCode": "US", "countryName": "USA", "adminName1": "Arizona"}]}
                """;
        assertEquals(Optional.of(new Place("US", "USA", "Arizona", "Kingman", null)),
                geocoder(FakeTransport.answering(200, json)).reverse(MERAN));
    }

    @Test
    void invalidUserIsConfiguration() {
        // GeoNames answers an unknown user with HTTP 401 and the status in the body (checked 16.11.2026)
        assertEquals(GeoServiceException.Kind.CONFIGURATION, assertThrows(GeoServiceException.class, () -> geocoder(
                FakeTransport.answering(401, status(10, "invalid user"))).reverse(MERAN)).kind());
    }

    @Test
    void noResultIsEmpty() throws GeoServiceException {
        assertFalse(geocoder(FakeTransport.answering(200, "{\"geonames\": []}")).reverse(MERAN).isPresent());
        assertFalse(geocoder(FakeTransport.answering(200, status(15, "no result found"))).reverse(MERAN).isPresent());
    }

    @Test
    void limitsAreQuotaExceeded() {
        for (int value : new int[] {18, 19}) {
            final GeoServiceException ex = assertThrows(GeoServiceException.class, () -> geocoder(FakeTransport.answering(200,
                    status(value, "the hourly limit of 1000 credits for demo has been exceeded"))).reverse(MERAN));
            assertEquals(GeoServiceException.Kind.QUOTA_EXCEEDED, ex.kind());
            assertTrue(ex.getMessage().contains("hourly limit of 1000 credits"), ex.getMessage());
        }
    }

    @Test
    void unknownUserIsConfiguration() {
        assertEquals(GeoServiceException.Kind.CONFIGURATION, assertThrows(GeoServiceException.class, () -> geocoder(
                FakeTransport.answering(200, status(10, "user account not enabled to use the free webservice"))).reverse(MERAN)).kind());
    }

    @Test
    void overloadIsRetryable() {
        assertEquals(GeoServiceException.Kind.RETRYABLE, assertThrows(GeoServiceException.class, () -> geocoder(
                FakeTransport.answering(200, status(22, "server overloaded exception"))).reverse(MERAN)).kind());
        assertEquals(GeoServiceException.Kind.RETRYABLE, assertThrows(GeoServiceException.class, () -> geocoder(
                FakeTransport.answering(503, "")).reverse(MERAN)).kind());
    }
}
