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
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import tf.geo.LatLon;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRouteServiceElevationProviderTest {
    private static final LatLon MERAN = new LatLon(46.67, 11.16);
    private static final LatLon BOZEN = new LatLon(46.5, 11.35);

    private static OpenRouteServiceElevationProvider ors(FakeTransport transport) {
        return new OpenRouteServiceElevationProvider("key", transport);
    }

    @Test
    void postsOnePointAndReadsItsElevation() throws GeoServiceException {
        final FakeTransport transport = FakeTransport.answering(200,
                "{\"geometry\": {\"coordinates\": [11.16, 46.67, 325], \"type\": \"Point\"}}");

        assertEquals(List.of(OptionalDouble.of(325)), ors(transport).elevations(List.of(MERAN)));

        assertEquals(URI.create("https://api.openrouteservice.org/elevation/point"), transport.requests().get(0));
        assertEquals("{\"format_in\":\"point\",\"format_out\":\"geojson\",\"geometry\":[11.16,46.67]}", transport.bodies().get(0));
        assertEquals("key", transport.lastHeaders().get("Authorization"));
    }

    @Test
    void postsALineForSeveralPoints() throws GeoServiceException {
        final FakeTransport transport = FakeTransport.answering(200,
                "{\"geometry\": {\"coordinates\": [[11.16, 46.67, 325], [11.35, 46.5, 262.5]], \"type\": \"LineString\"}}");

        assertEquals(List.of(OptionalDouble.of(325), OptionalDouble.of(262.5)), ors(transport).elevations(List.of(MERAN, BOZEN)));

        assertEquals(URI.create("https://api.openrouteservice.org/elevation/line"), transport.requests().get(0));
        assertEquals("{\"format_in\":\"polyline\",\"format_out\":\"geojson\",\"geometry\":[[11.16,46.67],[11.35,46.5]]}",
                transport.bodies().get(0));
    }

    @Test
    void answerNotMatchingThePointsFails() {
        // final review 5b: matched by index - one point less would give each following file its neighbour's altitude
        assertEquals(GeoServiceException.Kind.FAILED, assertThrows(GeoServiceException.class,
                () -> ors(FakeTransport.answering(200, "{\"geometry\": {\"coordinates\": [[11.35, 46.5, 262.5]]}}"))
                        .elevations(List.of(MERAN, BOZEN))).kind());
        assertEquals(GeoServiceException.Kind.FAILED, assertThrows(GeoServiceException.class,
                () -> ors(FakeTransport.answering(200,
                        "{\"geometry\": {\"coordinates\": [[11.35, 46.5, 262.5], [11.16, 46.67, 325]]}}"))
                        .elevations(List.of(MERAN, BOZEN))).kind());
    }

    @Test
    void moreThanABlockIsRefused() {
        final List<LatLon> positions = new ArrayList<>();
        for (int i = 0; i <= OpenRouteServiceElevationProvider.BLOCK_SIZE; i++) {
            positions.add(MERAN);
        }
        assertThrows(IllegalArgumentException.class, () -> ors(FakeTransport.answering(200, "{}")).elevations(positions));
    }

    @Test
    void quotaAndKeyErrors() {
        assertEquals(GeoServiceException.Kind.QUOTA_EXCEEDED, assertThrows(GeoServiceException.class,
                () -> ors(FakeTransport.answering(403, "{\"error\": \"Quota exceeded\"}")).elevations(List.of(MERAN))).kind());
        assertEquals(GeoServiceException.Kind.CONFIGURATION, assertThrows(GeoServiceException.class,
                () -> ors(FakeTransport.answering(401, "{\"error\": \"Access to this API has been disallowed\"}"))
                        .elevations(List.of(MERAN))).kind());
        assertEquals(GeoServiceException.Kind.RETRYABLE, assertThrows(GeoServiceException.class,
                () -> ors(FakeTransport.answering(429, "{}")).elevations(List.of(MERAN))).kind());
        assertEquals(GeoServiceException.Kind.RETRYABLE, assertThrows(GeoServiceException.class,
                () -> ors(FakeTransport.failing()).elevations(List.of(MERAN))).kind());
    }

    @Test
    void outsideTheDataIsEmpty() throws GeoServiceException {
        final List<OptionalDouble> elevations = ors(FakeTransport.answering(404, "{\"error\": \"no data\"}"))
                .elevations(List.of(MERAN, BOZEN));
        assertEquals(2, elevations.size());
        assertTrue(elevations.stream().noneMatch(OptionalDouble::isPresent));
    }
}
