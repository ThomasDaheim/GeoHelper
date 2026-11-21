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
import java.util.List;
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
        // without radius GeoNames answers with the nearest place only, whatever maxRows says (checked 16.11.2026)
        assertEquals(URI.create("https://secure.geonames.org/findNearbyPlaceNameJSON?lat=46.67&lng=11.16&style=FULL"
                + "&radius=10&maxRows=30&username=demo&lang=de"), geocoder(FakeTransport.answering(200, "")).requestUri(MERAN));
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

    // nearest places around Plaça de Catalunya with their distance in km, nearest first, shortened
    private static final String BARCELONA = """
            {"geonames": [
              {"name": "Eixample", "fcode": "PPLX", "distance": "0.65", "countryCode": "ES", "countryName": "Spanien", "adminName1": "Katalonien"},
              {"name": "el Raval", "fcode": "PPLX", "distance": "0.74", "countryCode": "ES", "countryName": "Spanien", "adminName1": "Katalonien"},
              {"name": "Barcelona", "fcode": "PPLA", "distance": "0.94", "countryCode": "ES", "countryName": "Spanien", "adminName1": "Katalonien"},
              {"name": "L'Hospitalet de Llobregat", "fcode": "PPLA3", "distance": "3.1", "countryCode": "ES", "countryName": "Spanien",
               "adminName1": "Katalonien"}]}
            """;

    @Test
    void nearbyListsDistrictsAndCitiesByDistance() throws GeoServiceException {
        assertEquals(List.of(
                new NearbyPlace(new Place("ES", "Spanien", "Katalonien", "Barcelona", "Eixample"), 0.65),
                new NearbyPlace(new Place("ES", "Spanien", "Katalonien", "Barcelona", "el Raval"), 0.74),
                new NearbyPlace(new Place("ES", "Spanien", "Katalonien", "Barcelona", null), 0.94),
                new NearbyPlace(new Place("ES", "Spanien", "Katalonien", "L'Hospitalet de Llobregat", null), 3.1)),
                geocoder(FakeTransport.answering(200, BARCELONA)).nearby(MERAN));
    }

    @Test
    void reverseIsTheFirstOfNearby() throws GeoServiceException {
        final GeoNamesReverseGeocoder geocoder = geocoder(FakeTransport.answering(200, BARCELONA));
        assertEquals(Optional.of(geocoder.nearby(MERAN).get(0).place()), geocoder.reverse(MERAN));
    }

    @Test
    void nothingWithinTheRadiusAsksForTheNearestPlace() throws GeoServiceException {
        // e.g. in the desert: no place within 10 km - then the nearest one at all, as GeoNames gives it without radius
        final FakeTransport transport = FakeTransport.answeringInTurn("{\"geonames\": []}", """
                {"geonames": [{"name": "Baker", "fcode": "PPL", "distance": "23.4", "countryCode": "US", "countryName": "USA",
                  "adminName1": "Kalifornien"}]}
                """);

        final List<NearbyPlace> nearby = geocoder(transport).nearby(MERAN);

        assertEquals(List.of(new NearbyPlace(new Place("US", "USA", "Kalifornien", "Baker", null), 23.4)), nearby);
        assertEquals(2, transport.requests().size());
        assertFalse(transport.requests().get(1).toString().contains("radius="));
    }

    @Test
    void historicalPlacesAreIgnored() throws GeoServiceException {
        // comparison with GeoSetter (19.11.2026): GeoNames lists dioceses (PPLCH, historical capital) and medieval
        // counties (PPLH, historical populated place) among the places - they were taken as city; abandoned (PPLQ)
        // and destroyed (PPLW) places are no better
        final String json = """
                {"geonames": [
                  {"name": "Roman Catholic Diocese of Como", "fcode": "PPLCH", "distance": "0.2", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"},
                  {"name": "Contea di Bergamo (medievale)", "fcode": "PPLH", "distance": "0.3", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"},
                  {"name": "Borgo Vico", "fcode": "PPLX", "distance": "0.4", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"},
                  {"name": "Rovine", "fcode": "PPLQ", "distance": "0.5", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"},
                  {"name": "Distrutto", "fcode": "PPLW", "distance": "0.6", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"},
                  {"name": "Como", "fcode": "PPLA2", "distance": "0.8", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"}]}
                """;

        final List<NearbyPlace> nearby = geocoder(FakeTransport.answering(200, json)).nearby(MERAN);

        assertEquals(new Place("IT", "Italien", "Lombardei", "Como", "Borgo Vico"), nearby.get(0).place());
        assertEquals(List.of("Como"), nearby.stream().map(place -> place.place().city()).distinct().toList());
        assertTrue(nearby.stream().noneMatch(place -> "Roman Catholic Diocese of Como".equals(place.place().sublocation())));
    }

    @Test
    void onlyDistrictsAsksForTheTownSeparately() throws GeoServiceException {
        // test folder 19.11.2026: at Milano Centrale the 30 nearest places are districts of Milan - Milan itself (at the
        // cathedral, 2 km away) isn't among them; a second request for towns only (cities1000) brings it
        final String districts = """
                {"geonames": [
                  {"name": "Centrale", "fcode": "PPLX", "distance": "0.2", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"},
                  {"name": "Greco", "fcode": "PPLX", "distance": "1.4", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"},
                  {"name": "Roman Catholic Archidiocese of Milan", "fcode": "PPLCH", "distance": "2.0", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"}]}
                """;
        final String towns = """
                {"geonames": [
                  {"name": "Mailand", "fcode": "PPLA", "distance": "2.1", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"},
                  {"name": "Sesto San Giovanni", "fcode": "PPLA3", "distance": "4.8", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"}]}
                """;
        final FakeTransport transport = FakeTransport.answeringInTurn(districts, towns);

        final List<NearbyPlace> nearby = geocoder(transport).nearby(MERAN);

        assertEquals(new Place("IT", "Italien", "Lombardei", "Mailand", "Centrale"), nearby.get(0).place());
        assertEquals(2, transport.requests().size());
        assertTrue(transport.requests().get(1).toString().contains("cities=cities1000"), transport.requests().get(1).toString());
        assertTrue(nearby.stream().anyMatch(place -> new Place("IT", "Italien", "Lombardei", "Sesto San Giovanni", null).equals(place.place())));
    }

    @Test
    void cityInTheAnswerNeedsNoSecondRequest() throws GeoServiceException {
        final FakeTransport transport = FakeTransport.answeringInTurn(BARCELONA);

        geocoder(transport).nearby(MERAN);

        assertEquals(1, transport.requests().size());
    }

    @Test
    void onlyHistoricalPlacesGiveNothing() throws GeoServiceException {
        final String json = """
                {"geonames": [
                  {"name": "Contea di Bergamo (medievale)", "fcode": "PPLH", "distance": "0.3", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"}]}
                """;

        assertEquals(List.of(), geocoder(FakeTransport.answering(200, json)).nearby(MERAN));
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
    void unreachableWithoutMessageNamesTheException() {
        // plan 8: an exception without text ended in "GeoNames not reachable: null"
        final GeoServiceException ex = assertThrows(GeoServiceException.class,
                () -> geocoder(FakeTransport.failingWith(new IOException())).reverse(MERAN));
        assertTrue(ex.getMessage().endsWith("IOException"), ex.getMessage());
    }

    @Test
    void districtBelongsToItsOwnCity() throws GeoServiceException {
        // plan 8: Rondò is a district of Sesto San Giovanni - the city nearest to the photo (Milan) was taken instead
        final String json = """
                {"geonames": [
                  {"name": "Rondò", "fcode": "PPLX", "distance": "0.4", "lat": "45.53", "lng": "9.23", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"},
                  {"name": "Mailand", "fcode": "PPLA", "distance": "1.0", "lat": "45.46", "lng": "9.19", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"},
                  {"name": "Sesto San Giovanni", "fcode": "PPLA3", "distance": "1.5", "lat": "45.53", "lng": "9.23", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"}]}
                """;

        final List<NearbyPlace> nearby = geocoder(FakeTransport.answering(200, json)).nearby(MERAN);

        assertEquals(new Place("IT", "Italien", "Lombardei", "Sesto San Giovanni", "Rondò"), nearby.get(0).place());
    }

    @Test
    void districtWithoutCoordinatesKeepsNearestCity() throws GeoServiceException {
        // without lat/lng of the district there is nothing to measure - the city nearest to the photo as before
        final String json = """
                {"geonames": [
                  {"name": "Rondò", "fcode": "PPLX", "distance": "0.4", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"},
                  {"name": "Mailand", "fcode": "PPLA", "distance": "1.0", "lat": "45.46", "lng": "9.19", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"},
                  {"name": "Sesto San Giovanni", "fcode": "PPLA3", "distance": "1.5", "lat": "45.53", "lng": "9.23", "countryCode": "IT", "countryName": "Italien", "adminName1": "Lombardei"}]}
                """;

        final List<NearbyPlace> nearby = geocoder(FakeTransport.answering(200, json)).nearby(MERAN);

        assertEquals(new Place("IT", "Italien", "Lombardei", "Mailand", "Rondò"), nearby.get(0).place());
    }

    @Test
    void nonJsonErrorShowsTheStatus() {
        // plan 8: an HTML error page of a proxy was reported as "invalid JSON" - the status says more
        assertEquals(GeoServiceException.Kind.RETRYABLE, assertThrows(GeoServiceException.class, () -> geocoder(
                FakeTransport.answering(503, "<html>Service Unavailable</html>")).reverse(MERAN)).kind());
        final GeoServiceException ex = assertThrows(GeoServiceException.class, () -> geocoder(
                FakeTransport.answering(400, "<html>Bad Request</html>")).reverse(MERAN));
        assertEquals(GeoServiceException.Kind.FAILED, ex.kind());
        assertTrue(ex.getMessage().contains("HTTP 400"), ex.getMessage());
    }

    @Test
    void overloadIsRetryable() {
        assertEquals(GeoServiceException.Kind.RETRYABLE, assertThrows(GeoServiceException.class, () -> geocoder(
                FakeTransport.answering(200, status(22, "server overloaded exception"))).reverse(MERAN)).kind());
        assertEquals(GeoServiceException.Kind.RETRYABLE, assertThrows(GeoServiceException.class, () -> geocoder(
                FakeTransport.answering(503, "")).reverse(MERAN)).kind());
    }
}
