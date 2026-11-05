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

import java.util.List;

/**
 * A base layer of the map - copied from GPXEditor's MapLayer, without its preferences.
 *
 * @param name name shown in the layer control
 * @param url tile URL - for layers with API key it ends with "=" and the key is appended
 * @param apiKey API key, empty for layers without key
 * @param minZoom lowest zoom with tiles
 * @param maxZoom highest zoom with tiles - Leaflet scales the tiles above it
 * @param attribution copyright text shown on the map (HTML)
 * @param tileLayerClass Leaflet class and options to use
 */
public record MapLayer(String name, String url, String apiKey, int minZoom, int maxZoom, String attribution,
        TileLayerClass tileLayerClass) {
    // Leaflet zooms in up to here, also beyond the maxZoom of the tiles
    private static final int MAX_ZOOM = 18;

    /**
     * Leaflet class of a layer and the options it needs - same as GPXEditor.
     */
    public enum TileLayerClass {
        STANDARD("L.TileLayer", List.of(), ""),
        MAPBOX("L.TileLayer", List.of("tileSize: 512", "zoomOffset: -1"), ""),
        QUADKEY("L.TileLayer.QuadKeyTileLayer", List.of("subdomains: '0123'"), "QuadKeyTileLayer");

        private final String jsClass;
        private final List<String> options;
        private final String jsResource;

        TileLayerClass(String jsClass, List<String> options, String jsResource) {
            this.jsClass = jsClass;
            this.options = options;
            this.jsResource = jsResource;
        }

        /**
         * @return Leaflet class, e.g. "L.TileLayer"
         */
        public String jsClass() {
            return jsClass;
        }

        /**
         * @return additional options for the layer, e.g. "tileSize: 512"
         */
        public List<String> options() {
            return options;
        }

        /**
         * @return script this class needs, without folder and extension - empty if none
         */
        public String jsResource() {
            return jsResource;
        }
    }

    public static final MapLayer OPENSTREETMAP = new MapLayer("OpenStreetMap",
            "http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", "", 0, 18,
            "Map data &copy; OpenStreetMap and contributors", TileLayerClass.STANDARD);
    public static final MapLayer OPENTOPOMAP = new MapLayer("OpenTopoMap",
            "https://opentopomap.org/{z}/{x}/{y}.png", "", 0, 17,
            "Map data: &copy; OpenTopoMap.org", TileLayerClass.STANDARD);
    public static final MapLayer SATELLITE_ESRI = new MapLayer("Satellite Esri",
            "http://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", "", 0, 18,
            "&copy; Esri, DigitalGlobe, GeoEye, i-cubed, USDA FSA, USGS, AEX, Getmapping, Aerogrid, IGN, IGP, swisstopo and the GIS User Community",
            TileLayerClass.STANDARD);
    public static final MapLayer GOOGLE_TERRAIN = new MapLayer("Google Terrain",
            "http://mt0.google.com/vt/lyrs=p&x={x}&y={y}&z={z}", "", 0, 21, "Map tiles from Google", TileLayerClass.STANDARD);
    public static final MapLayer GOOGLE_HYBRID = new MapLayer("Google Hybrid",
            "http://mt0.google.com/vt/lyrs=y&x={x}&y={y}&z={z}", "", 0, 21, "Map tiles from Google", TileLayerClass.STANDARD);
    public static final MapLayer GOOGLE_AERIAL = new MapLayer("Google Aerial",
            "http://mt0.google.com/vt/lyrs=s&x={x}&y={y}&z={z}", "", 0, 21, "Map tiles from Google", TileLayerClass.STANDARD);
    public static final MapLayer GOOGLE_ROADMAP = new MapLayer("Google Roadmap",
            "http://mt0.google.com/vt/lyrs=m&x={x}&y={y}&z={z}", "", 0, 21, "Map tiles from Google", TileLayerClass.STANDARD);
    public static final MapLayer BING = new MapLayer("Bing Maps",
            "https://ecn.t{s}.tiles.virtualearth.net/tiles/r{q}?g=864&mkt=en-gb&lbl=l1&stl=h&shading=hill&n=z", "", 3, 20,
            "Bing - map data copyright Microsoft and its suppliers", TileLayerClass.QUADKEY);
    public static final MapLayer BING_AERIAL = new MapLayer("Bing Aerial",
            "https://ecn.t{s}.tiles.virtualearth.net/tiles/a{q}?g=737&n=z", "", 3, 20,
            "Bing - map data copyright Microsoft and its suppliers", TileLayerClass.QUADKEY);
    public static final MapLayer DE_TOPOPLUSOPEN = new MapLayer("DE: TopPlusOpen",
            "http://sgx.geodatenzentrum.de/wmts_topplus_open/tile/1.0.0/web/default/WEBMERCATOR/{z}/{y}/{x}.png", "", 0, 18,
            "Map data: &copy; geodatenzentrum.de", TileLayerClass.STANDARD);

    // layers that need an API key - see withApiKey()
    public static final MapLayer OPENCYCLEMAP = new MapLayer("OpenCycleMap",
            "https://tile.thunderforest.com/cycle/{z}/{x}/{y}.png?apikey=", "", 0, 18,
            "&copy; OpenCycleMap, Map data &copy; OpenStreetMap contributors", TileLayerClass.STANDARD);
    public static final MapLayer MAPBOX = new MapLayer("MapBox",
            "https://api.mapbox.com/styles/v1/mapbox/outdoors-v11/tiles/{z}/{x}/{y}?access_token=", "", 0, 18,
            "Map data &copy; OpenStreetMap contributors, Imagery &copy; Mapbox", TileLayerClass.MAPBOX);
    public static final MapLayer MAPBOX_SATELLITE = new MapLayer("MapBox Satellite",
            "https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v11/tiles/{z}/{x}/{y}?access_token=", "", 0, 18,
            "Map data &copy; OpenStreetMap contributors, Imagery &copy; Mapbox", TileLayerClass.MAPBOX);

    /**
     * @return all layers that work without API key - OpenStreetMap first
     */
    public static List<MapLayer> withoutApiKey() {
        return List.of(OPENSTREETMAP, OPENTOPOMAP, SATELLITE_ESRI, GOOGLE_TERRAIN, GOOGLE_HYBRID, GOOGLE_AERIAL,
                GOOGLE_ROADMAP, BING, BING_AERIAL, DE_TOPOPLUSOPEN);
    }

    /**
     * @return true if the tile server needs an API key appended to the URL
     */
    public boolean needsApiKey() {
        return url.endsWith("=");
    }

    /**
     * @param key API key of the tile server
     * @return this layer with the key
     */
    public MapLayer withApiKey(String key) {
        return new MapLayer(name, url, key, minZoom, maxZoom, attribution, tileLayerClass);
    }

    /**
     * @return JavaScript expression creating the Leaflet layer - format as in GPXEditor's MapLayer.getJSCode()
     */
    public String jsCode() {
        final StringBuilder result = new StringBuilder("new ").append(tileLayerClass.jsClass()).append('(')
                .append(Js.string(url + apiKey)).append(", {\n");
        for (String option : tileLayerClass.options()) {
            result.append("    ").append(option).append(",\n");
        }
        result.append("    maxZoom: ").append(MAX_ZOOM).append(",\n");
        result.append("    maxNativeZoom: ").append(maxZoom).append(",\n");
        result.append("    minZoom: ").append(minZoom).append(",\n");
        result.append("    attribution: ").append(Js.string(attribution)).append('\n');
        // no ";" - used inside other statements
        return result.append("})").toString();
    }
}