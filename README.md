# GeoHelper
Geo related helper classes used in my other repos. Current version: v0.1

## Status

* `tf.geo.map.LeafletMapView`: Leaflet map in a JavaFX WebView - taken over from [GPXEditor](https://github.com/ThomasDaheim/GPXEditor) and decoupled from it
  * base layers without API key (OpenStreetMap, OpenTopoMap, Esri, Google, Bing, TopPlusOpen); Mapbox and OpenCycleMap with `MapLayer.withApiKey()`
  * markers with an id, clustered unless selected; selected markers can be dragged
  * tracks as lines, can be hidden and removed
  * callbacks for marker clicks and drags, pixel position to lat / lon (e.g. for drag & drop and context menus)
* geo services (reverse geocoding, altitude, time zone) follow with the next step of [GeoTagger](https://github.com/ThomasDaheim/GeoTagger)

GPXEditor will switch to GeoHelper later on.

The ExifTool wrapper that started here has moved to its own library [ExifToolHelper](https://github.com/ThomasDaheim/ExifToolHelper), so its users don't get JavaFX or map dependencies.

## Usage

```java
LeafletMapView map = new LeafletMapView();
map.setMapListener(new MapListener() {
    @Override
    public void markerMoved(String id, LatLon position) {
        // the user dragged a selected marker
    }
});
map.displayMap(MapConfig.defaults());
// all markers - only the changes are sent to the page; calls before the map is ready are kept
map.showMarkers(List.of(new MapMarker("a.jpg", new LatLon(46.67, 11.16), "a.jpg", MarkerColor.BLUE, false)));
map.showTrack("tour.gpx", points, "#e6194b");
```

## Notes

* Note on building: "gradlew build" compiles, tests and installs the jar into the local maven repo. Found there by the other repos during their build as `tf.GeoHelper:GeoHelper:<version>`. Requires JDK 25; Gradle comes via the wrapper.
* Note on JavaFX: GeoHelper only compiles against JavaFX - the application brings it. It needs `javafx.web` (which requires `javafx.media`) and `netscape.javascript.JSObject` from JavaFX's `jdk-jsobject` via `--upgrade-module-path` - see build.gradle of GeoTagger or GPXEditor.
* Note on Leaflet: version 1.8.0 - 1.9.4 leads to flickering tiles with JavaFX 20 and later.
* Note on tests: the WebView tests start JavaFX without a window, but need a desktop session.
