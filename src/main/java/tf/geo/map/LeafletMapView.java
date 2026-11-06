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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSException;
import netscape.javascript.JSObject;
import tf.geo.LatLon;

/**
 * Leaflet map in a WebView - copied from GPXEditor's LeafletMapView (a backport of Stefan Saring's sportstracker
 * leafletmap) and decoupled: no preferences, no TrackMap.
 * Markers are kept by id: showMarkers() gets all wanted markers and sends only the changes to the page.
 * Calls before the map is ready are kept and executed once it is.
 */
public class LeafletMapView extends StackPane {
    private static final Logger LOGGER = Logger.getLogger(LeafletMapView.class.getName());
    private static final String RESOURCES = "/tf/geo/map/leaflet/";
    // 1.9.4 leads to flickering tiles with JavaFX >= 20 - see GPXEditor
    private static final String LEAFLET = "leaflet/leaflet.1.8.0.min";

    private final WebView webView = new WebView();
    private final WebEngine engine = webView.getEngine();
    // field: the WebView only keeps a weak reference to the bridge object
    private final MapCallback callback = new MapCallback();
    private final Map<String, MapMarker> shownMarkers = new LinkedHashMap<>();
    private final List<Runnable> pending = new ArrayList<>();
    private boolean ready = false;
    // smaller rectangles count as a click
    private static final double MIN_AREA = 3;
    private final Rectangle selectRect = new Rectangle();
    // where the ctrl + drag started - null while no area is drawn
    private Point2D selectStart;

    public LeafletMapView() {
        getChildren().add(webView);
        // the application shows its own context menu
        webView.setContextMenuEnabled(false);
        initAreaSelection();
    }

    // ctrl + drag draws a rectangle and reports the area - same as GPXEditor TrackMap.handleMouseCntrl*;
    // GeoMap.js stops the map from moving meanwhile
    private void initAreaSelection() {
        selectRect.getStyleClass().add("selectRect");
        selectRect.setFill(Color.rgb(30, 144, 255, 0.15));
        selectRect.setStroke(Color.DODGERBLUE);
        selectRect.getStrokeDashArray().setAll(4.0, 4.0);
        selectRect.setManaged(false);
        selectRect.setMouseTransparent(true);
        selectRect.setVisible(false);
        getChildren().add(selectRect);

        webView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.isControlDown() && event.getButton() == MouseButton.PRIMARY) {
                selectStart = new Point2D(event.getX(), event.getY());
                resizeSelection(event);
                selectRect.setVisible(true);
                event.consume();
            }
        });
        webView.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (selectStart != null) {
                resizeSelection(event);
                event.consume();
            }
        });
        webView.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (selectStart != null) {
                resizeSelection(event);
                selectRect.setVisible(false);
                selectStart = null;
                event.consume();
                // a click with ctrl is not an area
                if (selectRect.getWidth() >= MIN_AREA && selectRect.getHeight() >= MIN_AREA) {
                    reportArea(event.isShiftDown());
                }
            }
        });
    }

    private void resizeSelection(MouseEvent event) {
        selectRect.setX(Math.min(selectStart.getX(), event.getX()));
        selectRect.setY(Math.min(selectStart.getY(), event.getY()));
        selectRect.setWidth(Math.abs(event.getX() - selectStart.getX()));
        selectRect.setHeight(Math.abs(event.getY() - selectStart.getY()));
    }

    private void reportArea(boolean shiftDown) {
        final Optional<LatLon> upperLeft = latLonAt(selectRect.getX(), selectRect.getY());
        final Optional<LatLon> lowerRight = latLonAt(selectRect.getX() + selectRect.getWidth(), selectRect.getY() + selectRect.getHeight());
        if (upperLeft.isEmpty() || lowerRight.isEmpty()) {
            return;
        }
        final LatLon southWest = new LatLon(Math.min(upperLeft.get().latitude(), lowerRight.get().latitude()),
                Math.min(upperLeft.get().longitude(), lowerRight.get().longitude()));
        final LatLon northEast = new LatLon(Math.max(upperLeft.get().latitude(), lowerRight.get().latitude()),
                Math.max(upperLeft.get().longitude(), lowerRight.get().longitude()));
        callback.listener().areaSelected(southWest, northEast, shiftDown);
    }

    /**
     * Loads the map page and creates the map.
     *
     * @param config layers and initial view
     * @return completed once the map is ready - exceptionally with IllegalStateException if the page can't be loaded
     */
    public CompletableFuture<Void> displayMap(MapConfig config) {
        Objects.requireNonNull(config);
        final CompletableFuture<Void> result = new CompletableFuture<>();
        engine.setJavaScriptEnabled(true);
        final ChangeListener<Worker.State> loaded = new ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends Worker.State> observable,
                    Worker.State oldState, Worker.State state) {
                if (state == Worker.State.SUCCEEDED) {
                    observable.removeListener(this);
                    setUpMap(config);
                    result.complete(null);
                } else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
                    observable.removeListener(this);
                    result.completeExceptionally(new IllegalStateException("Map page can't be loaded"));
                }
            }
        };
        engine.getLoadWorker().stateProperty().addListener(loaded);
        engine.load(Objects.requireNonNull(LeafletMapView.class.getResource(RESOURCES + "leafletmap.html")).toExternalForm());
        return result;
    }

    // same order as GPXEditor: callback, styles, scripts, layers, map, controls - then our own script
    private void setUpMap(MapConfig config) {
        final JSObject window = (JSObject) engine.executeScript("window");
        window.setMember("jscallback", callback);

        addStyle(LEAFLET + ".css");
        addStyle("markercluster/MarkerCluster.min.css");
        addStyle("markercluster/MarkerCluster.Default.min.css");
        runScript(LEAFLET + ".js");
        runScript("leaflet-color-markers/leaflet-color-markers.min.js");
        runScript("markercluster/leaflet.markercluster-src.min.js");
        runScript("QuadKeyTileLayer.min.js");

        final List<MapLayer> layers = config.baseLayers();
        execScript("var baseMaps = {" + layers.stream()
                .map(layer -> Js.string(layer.name()) + ": " + layer.jsCode())
                .collect(Collectors.joining(",\n")) + "};");
        final StringBuilder map = new StringBuilder("var myMap = L.map('map', {center: [")
                .append(Js.number(config.initialCenter().latitude())).append(", ")
                .append(Js.number(config.initialCenter().longitude())).append("], zoom: ").append(config.initialZoom())
                // dragging beyond -180 / 180 jumps back to the world with the markers - see also GeoMap.js
                .append(", worldCopyJump: true");
        if (!layers.isEmpty()) {
            map.append(", layers: [baseMaps[").append(Js.string(layers.get(0).name())).append("]]");
        }
        map.append("});\nmyMap.attributionControl.setPrefix('Leaflet');");
        execScript(map.toString());
        if (layers.size() > 1) {
            execScript("L.control.layers(baseMaps).addTo(myMap);");
        }
        execScript("L.control.scale({imperial: false}).addTo(myMap);");
        // read by GeoMap.js - no chunkedLoading, see there
        execScript("var geoClusterOptions = {maxClusterRadius: " + config.clusterRadius()
                + ", disableClusteringAtZoom: " + config.noClusteringFromZoom()
                + ", spiderfyOnMaxZoom: true, chunkedLoading: false};");
        runScript("GeoMap.js");

        ready = true;
        final List<Runnable> calls = new ArrayList<>(pending);
        pending.clear();
        calls.forEach(Runnable::run);
    }

    /**
     * @return true once the map is shown and accepts markers and tracks directly
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Shows exactly these markers - only the differences to the markers shown so far are sent to the page.
     *
     * @param markers all markers that shall be on the map
     * @throws IllegalArgumentException if two markers have the same id
     */
    public void showMarkers(Collection<MapMarker> markers) {
        final MarkerDiff diff = MarkerDiff.between(shownMarkers, markers);
        if (diff.isEmpty()) {
            return;
        }
        diff.removed().forEach(shownMarkers::remove);
        diff.changed().forEach(marker -> shownMarkers.put(marker.id(), marker));

        final StringBuilder script = new StringBuilder();
        if (!diff.removed().isEmpty()) {
            script.append("geoRemoveMarkers([")
                    .append(diff.removed().stream().map(Js::string).collect(Collectors.joining(",")))
                    .append("]);\n");
        }
        if (!diff.changed().isEmpty()) {
            script.append("geoUpdateMarkers([")
                    .append(diff.changed().stream().map(LeafletMapView::markerJson).collect(Collectors.joining(",\n")))
                    .append("]);");
        }
        whenReady(script.toString());
    }

    private static String markerJson(MapMarker marker) {
        return "{\"id\":" + Js.string(marker.id())
                + ",\"lat\":" + Js.number(marker.position().latitude())
                + ",\"lng\":" + Js.number(marker.position().longitude())
                + ",\"title\":" + Js.string(marker.title())
                + ",\"icon\":" + Js.string(marker.color().jsIcon())
                + ",\"selected\":" + marker.selected() + "}";
    }

    /**
     * Shows a track as lines - replaces a track with the same id. All lines are shown, hidden and removed together.
     *
     * @param id id of the track, e.g. the path of the GPX file
     * @param lines its lines, e.g. the segments, each in its own colour
     */
    public void showTrack(String id, List<TrackLine> lines) {
        whenReady("geoShowTrack(" + Js.string(id) + ", " + lines.stream()
                .map(line -> "{\"coords\":" + coordinates(line.points()) + ",\"color\":" + Js.string(line.color()) + "}")
                .collect(Collectors.joining(",", "[", "]")) + ");");
    }

    /**
     * @param id id of a track shown with showTrack()
     * @param visible false hides the track, true shows it again
     */
    public void setTrackVisible(String id, boolean visible) {
        whenReady("geoSetTrackVisible(" + Js.string(id) + ", " + visible + ");");
    }

    /**
     * @param id id of a track shown with showTrack()
     */
    public void removeTrack(String id) {
        whenReady("geoRemoveTrack(" + Js.string(id) + ");");
    }

    /**
     * Shows all positions - nothing happens for none, one position is centered with zoom 14 or more.
     *
     * @param positions positions to show
     */
    public void fitBounds(Collection<LatLon> positions) {
        if (positions.isEmpty()) {
            return;
        }
        whenReady("geoFitBounds(" + coordinates(positions) + ");");
    }

    /**
     * @param position new center of the map - the zoom stays
     */
    public void panTo(LatLon position) {
        // no animation: the map jumps, and its state is final right away
        whenReady("myMap.panTo([" + Js.number(position.latitude()) + ", " + Js.number(position.longitude())
                + "], {animate: false});");
    }

    /**
     * @param x x relative to the WebView, e.g. MouseEvent.getX()
     * @param y y relative to the WebView
     * @return the position at this point - empty while the map isn't ready
     */
    public Optional<LatLon> latLonAt(double x, double y) {
        if (!ready) {
            return Optional.empty();
        }
        final Object result = execScript("getLatLngForPoint(" + Js.number(x) + ", " + Js.number(y) + ");");
        if (!(result instanceof JSObject latLng)) {
            return Optional.empty();
        }
        return Optional.of(normalized(((Number) latLng.getSlot(0)).doubleValue(), ((Number) latLng.getSlot(1)).doubleValue()));
    }

    // Leaflet repeats the world horizontally - longitudes outside -180 .. 180 are mapped back, latitudes clamped
    static LatLon normalized(double latitude, double longitude) {
        final double wrapped = ((longitude + 180) % 360 + 360) % 360 - 180;
        return new LatLon(Math.max(-90, Math.min(90, latitude)), wrapped);
    }

    /**
     * @param listener gets clicks and drags of the markers
     */
    public void setMapListener(MapListener listener) {
        callback.setListener(listener);
    }

    /**
     * @return the WebView showing the map - e.g. for drag & drop and mouse handlers
     */
    public WebView getWebView() {
        return webView;
    }

    /**
     * Unloads the page - called when the application ends or the map isn't needed anymore.
     */
    public void dispose() {
        ready = false;
        pending.clear();
        shownMarkers.clear();
        engine.load("about:blank");
    }

    private static String coordinates(Collection<LatLon> positions) {
        return positions.stream()
                .map(position -> "[" + Js.number(position.latitude()) + "," + Js.number(position.longitude()) + "]")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private void whenReady(String script) {
        if (ready) {
            execScript(script);
        } else {
            pending.add(() -> execScript(script));
        }
    }

    private void addStyle(String resource) {
        execScript("var geoStyle = document.createElement('style'); geoStyle.textContent = " + Js.string(resourceText(resource))
                + "; document.head.appendChild(geoStyle);");
    }

    private void runScript(String resource) {
        execScript(resourceText(resource));
    }

    private static String resourceText(String resource) {
        try (InputStream in = LeafletMapView.class.getResourceAsStream(RESOURCES + resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing map resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Object execScript(String script) {
        try {
            return engine.executeScript(script);
        } catch (JSException ex) {
            LOGGER.log(Level.SEVERE, "Script failed: " + script.substring(0, Math.min(200, script.length())), ex);
            return null;
        }
    }
}