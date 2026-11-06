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
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.web.WebView;
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
            map.showTrack("t", List.of(new TrackLine(List.of(new LatLon(46, 11), new LatLon(46.1, 11.1)), "#ff0000")));
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
            map.showTrack("t", List.of(new TrackLine(List.of(new LatLon(46, 11), new LatLon(46.1, 11.1)), "#ff0000")));
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
    void manyMarkersLeaveNoGhosts() throws InterruptedException {
        final LeafletMapView map = FxTestSupport.readyMap();
        final List<MapMarker> markers = new ArrayList<>();
        for (int i = 0; i < 20000; i++) {
            markers.add(new MapMarker("m" + i, new LatLon(40 + (i % 1000) / 100.0, 10 + i / 2000.0), "m" + i, MarkerColor.BLUE, false));
        }
        // the last marker is the one still waiting longest if the cluster adds markers in chunks
        final List<MapMarker> lastSelected = new ArrayList<>(markers);
        lastSelected.set(19999, new MapMarker("m19999", markers.get(19999).position(), "m19999", MarkerColor.RED, true));
        // two separate updates - e.g. loading a big folder and selecting a file right away
        FxTestSupport.onFx(() -> {
            map.showMarkers(markers);
            return null;
        });
        FxTestSupport.onFx(() -> {
            map.showMarkers(lastSelected);
            return null;
        });
        // the cluster may still add markers later (chunked loading) - wait until nothing changes anymore
        int last = -1;
        for (int i = 0; i < 50; i++) {
            final int now = number(js(map, "geoClusteredCount()"));
            if (now == last) {
                break;
            }
            last = now;
            Thread.sleep(200);
        }
        assertEquals(19999, number(js(map, "geoClusteredCount()")));
        assertEquals(0, number(js(map, "geoCluster.getLayers().filter(function (m) { return m.geoId === 'm19999'; }).length")));
    }

    @Test
    void viewStaysInTheWorldWithTheMarkers() {
        final LeafletMapView map = FxTestSupport.readyMap();
        // zoomed far out the world is shown several times - the markers are only in the middle one;
        // e.g. zooming in again around the mouse ends up in a copy one turn further west
        js(map, "myMap.setView([35.5, -440.0], 5, {animate: false})");
        assertEquals(-80.0, ((Number) js(map, "myMap.getCenter().lng")).doubleValue(), 1e-6);
        assertEquals(35.5, ((Number) js(map, "myMap.getCenter().lat")).doubleValue(), 1e-6);
    }

    @Test
    void viewInsideTheWorldIsLeftAlone() {
        final LeafletMapView map = FxTestSupport.readyMap();
        // 11.55 wraps to 11.550000000000011 - that must not start a loop of setView / moveend
        final Object result = js(map, "try { myMap.setView([46.5, 11.55], 10, {animate: false}); 'ok'; } catch (e) { e.message; }");
        assertEquals("ok", result);
        assertEquals(11.55, ((Number) js(map, "myMap.getCenter().lng")).doubleValue(), 1e-9);
    }

    @Test
    void clusterOptionsComeFromTheConfig() {
        final LeafletMapView map = FxTestSupport.readyMap();
        assertEquals(40, number(js(map, "geoCluster.options.maxClusterRadius")));
        assertEquals(15, number(js(map, "geoCluster.options.disableClusteringAtZoom")));
    }

    @Test
    void trackWithSeveralColouredLines() {
        final LeafletMapView map = FxTestSupport.readyMap();
        FxTestSupport.onFx(() -> {
            map.showTrack("t", List.of(new TrackLine(List.of(new LatLon(46, 11), new LatLon(46.1, 11.1)), "#ff0000"),
                    new TrackLine(List.of(new LatLon(47, 12), new LatLon(47.1, 12.1)), "darkblue")));
            return null;
        });
        assertEquals(1, number(js(map, "geoTrackCount()")));
        assertEquals(2, number(js(map, "geoTracks['t'].getLayers().length")));
        assertEquals("darkblue", js(map, "geoTracks['t'].getLayers()[1].options.color"));
    }

    // a mouse event at x / y of the WebView (scene and WebView start at 0 / 0)
    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double x, double y, boolean ctrl, boolean shift) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1, shift, ctrl, false, false,
                true, false, false, false, false, false, null);
    }

    private static List<String> dragArea(LeafletMapView map, double fromX, double fromY, double toX, double toY) {
        final List<String> events = new ArrayList<>();
        FxTestSupport.onFx(() -> {
            map.setMapListener(new MapListener() {
                @Override
                public void areaSelected(LatLon southWest, LatLon northEast, boolean shiftDown) {
                    events.add(southWest.latitude() < northEast.latitude() && southWest.longitude() < northEast.longitude()
                            ? "area " + shiftDown : "wrong corners");
                }
            });
            final WebView web = map.getWebView();
            web.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, fromX, fromY, true, false));
            web.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, toX, toY, true, false));
            web.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, toX, toY, true, false));
            return null;
        });
        return events;
    }

    @Test
    void ctrlDragSelectsAnArea() {
        final LeafletMapView map = FxTestSupport.readyMap();
        assertEquals(List.of("area false"), dragArea(map, 300, 200, 100, 400));
    }

    @Test
    void ctrlClickWithoutDragSelectsNoArea() {
        final LeafletMapView map = FxTestSupport.readyMap();
        assertEquals(List.of(), dragArea(map, 300, 200, 301, 201));
    }

    @Test
    void panToIsImmediate() {        final LeafletMapView map = FxTestSupport.readyMap();
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