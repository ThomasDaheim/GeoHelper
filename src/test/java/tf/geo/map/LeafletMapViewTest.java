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
package tf.geo.map;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tf.geo.LatLon;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tf.geo.map.FxTestSupport.js;

class LeafletMapViewTest {
    @BeforeAll
    static void fx() {
        FxTestSupport.startFx();
    }

    private static MapMarker marker(String id, double lat, boolean selected) {
        return new MapMarker(id, new LatLon(lat, 11.5), id, MarkerColor.BLUE, selected);
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    @Test
    void mapBecomesReadyWithClusterSupport() {
        final LeafletMapView map = FxTestSupport.readyMap();
        assertTrue(map.isReady());
        assertEquals("function", js(map, "typeof L.markerClusterGroup"));
        assertEquals("object", js(map, "typeof blueIcon"));
    }

    @Test
    void markersAreClusteredUnlessSelected() {
        final LeafletMapView map = FxTestSupport.readyMap();
        FxTestSupport.onFx(() -> {
            map.showMarkers(List.of(marker("a", 46, false), marker("b", 46.1, false), marker("c", 46.2, true)));
            return null;
        });
        assertEquals(3, number(js(map, "geoMarkerCount()")));
        assertEquals(2, number(js(map, "geoClusteredCount()")));
        assertEquals(Boolean.TRUE, js(map, "geoMarkers['c'].options.draggable"));
    }

    @Test
    void showMarkersReplacesOnlyWhatChanged() {
        final LeafletMapView map = FxTestSupport.readyMap();
        FxTestSupport.onFx(() -> {
            map.showMarkers(List.of(marker("a", 46, false), marker("b", 47, false)));
            return null;
        });
        FxTestSupport.onFx(() -> {
            map.showMarkers(List.of(marker("a", 48, false), marker("c", 49, false)));
            return null;
        });
        assertEquals(2, number(js(map, "geoMarkerCount()")));
        assertEquals(48.0, ((Number) js(map, "geoMarkers['a'].getLatLng().lat")).doubleValue(), 1e-9);
        assertEquals("undefined", js(map, "typeof geoMarkers['b']"));
    }

    @Test
    void titlesWithQuotesAreSafe() {
        final LeafletMapView map = FxTestSupport.readyMap();
        final String title = "O'Brien \"x\" \\ </script> Völs";
        FxTestSupport.onFx(() -> {
            map.showMarkers(List.of(new MapMarker("q", new LatLon(46, 11), title, MarkerColor.RED, false)));
            return null;
        });
        assertEquals(title, js(map, "geoMarkers['q'].options.title"));
    }

    @Test
    void callsBeforeReadyAreApplied() throws Exception {
        final LeafletMapView map = FxTestSupport.onFx(LeafletMapView::new);
        final CompletableFuture<Void> ready = FxTestSupport.onFx(() -> {
            new Scene(map, 800, 600);
            map.showMarkers(List.of(marker("early", 46, false)));
            map.showTrack("t", List.of(new LatLon(46, 11), new LatLon(46.1, 11.1)), "#ff0000");
            return map.displayMap(MapConfig.defaults());
        });
        ready.get(30, TimeUnit.SECONDS);
        assertEquals(1, number(js(map, "geoMarkerCount()")));
        assertEquals(1, number(js(map, "geoTrackCount()")));
    }

    @Test
    void tracksCanBeHiddenAndRemoved() {
        final LeafletMapView map = FxTestSupport.readyMap();
        FxTestSupport.onFx(() -> {
            map.showTrack("t", List.of(new LatLon(46, 11), new LatLon(46.1, 11.1)), "#ff0000");
            return null;
        });
        assertEquals(1, number(js(map, "geoVisibleTrackCount()")));
        FxTestSupport.onFx(() -> {
            map.setTrackVisible("t", false);
            return null;
        });
        assertEquals(0, number(js(map, "geoVisibleTrackCount()")));
        FxTestSupport.onFx(() -> {
            map.setTrackVisible("t", true);
            return null;
        });
        assertEquals(1, number(js(map, "geoVisibleTrackCount()")));
        FxTestSupport.onFx(() -> {
            map.removeTrack("t");
            return null;
        });
        assertEquals(0, number(js(map, "geoTrackCount()")));
    }

    @Test
    void fitBoundsWithoutPositionsKeepsTheView() {
        final LeafletMapView map = FxTestSupport.readyMap();
        final String view = "myMap.getCenter().lat + ',' + myMap.getCenter().lng + ',' + myMap.getZoom()";
        final Object before = js(map, view);
        FxTestSupport.onFx(() -> {
            map.fitBounds(List.of());
            return null;
        });
        assertEquals(before, js(map, view));
        FxTestSupport.onFx(() -> {
            map.fitBounds(List.of(new LatLon(46.5, 11.5)));
            return null;
        });
        assertEquals(46.5, ((Number) js(map, "myMap.getCenter().lat")).doubleValue(), 1e-6);
        assertTrue(number(js(map, "myMap.getZoom()")) >= 14);
    }

    @Test
    void panToIsImmediate() {
        final LeafletMapView map = FxTestSupport.readyMap();
        FxTestSupport.onFx(() -> {
            map.panTo(new LatLon(45.0, 12.0));
            return null;
        });
        assertEquals(45.0, ((Number) js(map, "myMap.getCenter().lat")).doubleValue(), 1e-6);
        assertEquals(12.0, ((Number) js(map, "myMap.getCenter().lng")).doubleValue(), 1e-6);
    }

    @Test
    void latLonAtRoundTrips() {
        final LeafletMapView map = FxTestSupport.readyMap();
        final LatLon position = FxTestSupport.onFx(() -> map.latLonAt(100, 80)).orElseThrow();
        final String point = (String) js(map,
                "getPointForLatLng(" + position.latitude() + ", " + position.longitude() + ").join(',')");
        final String[] xy = point.split(",");
        assertEquals(100.0, Double.parseDouble(xy[0]), 0.5);
        assertEquals(80.0, Double.parseDouble(xy[1]), 0.5);
    }

    @Test
    void markerEventsReachTheListener() {
        final LeafletMapView map = FxTestSupport.readyMap();
        final List<String> events = new ArrayList<>();
        FxTestSupport.onFx(() -> {
            map.setMapListener(new MapListener() {
                @Override
                public void markerClicked(String id, boolean shiftDown) {
                    events.add("click " + id + " " + shiftDown);
                }

                @Override
                public void markerMoved(String id, LatLon position) {
                    events.add("moved " + id + " " + position.latitude() + " " + position.longitude());
                }
            });
            map.showMarkers(List.of(marker("s", 46, true)));
            return null;
        });
        js(map, "geoMarkers['s'].fire('click')");
        js(map, "geoMarkers['s'].setLatLng([47, 12]); geoMarkers['s'].fire('dragend')");
        assertEquals(List.of("click s false", "moved s 47.0 12.0"), events);
    }
}