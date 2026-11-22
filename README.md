# GeoHelper
Geo related helper classes used in my other repos. Current version: v0.1

## Status

* `tf.geo.map.LeafletMapView`: Leaflet map in a JavaFX WebView - taken over from [GPXEditor](https://github.com/ThomasDaheim/GPXEditor) and decoupled from it
  * base layers without API key (OpenStreetMap, OpenTopoMap, Esri, Google, Bing, TopPlusOpen); Mapbox and OpenCycleMap with `MapLayer.withApiKey()`
  * markers with an id, clustered unless selected; selected markers can be dragged
  * clustering set in `MapConfig`: radius (default 40 px) and the zoom from which markers aren't bundled anymore (default 15) - markercluster has no minimum count
  * tracks as one or more lines (`TrackLine`), each with its own colour - shown, hidden and removed together
  * callbacks for marker clicks and drags, and for an area drawn with Ctrl + drag (`MapListener.areaSelected`, as GPXEditor's waypoint selection)
  * pixel position to lat / lon (e.g. for drag & drop and context menus)
  * the view stays in the world with the markers when zoomed far out
  * coordinates of the mouse and of the map center in the upper left corner, like GPXEditor
  * popups at a marker (`MapPopup`: title, rows, lines of links) - also for a clustered marker; a click on a link calls `MapListener.popupLinkClicked`; clicks on markers and clusters leave it open, a click on the map closes it
* `tf.geo.LatLon`: a position; `distanceTo()` gives the great circle distance in meters
* `tf.geo.service`: reverse geocoding behind the interface `ReverseGeocoder` - position to `Place` (country code, country, state, city, sublocation)
  * `GeoNamesReverseGeocoder`: GeoNames `findNearbyPlaceName` over https, needs a GeoNames user enabled for the web services; the nearest place that isn't a district is the city, a district (e.g. Eixample in Barcelona) the sublocation if it is the nearest place; historical, abandoned and destroyed places (e.g. dioceses, medieval counties) are ignored; an answer with districts only (e.g. at Milano Centrale) gets the town from a second request for towns only (`cities1000`)
  * `MapboxReverseGeocoder`: Mapbox Geocoding API v6, needs an access token; sublocation from locality or neighborhood
  * `nearby()`: the places to choose from with their distance (`NearbyPlace`), the best answer first - GeoNames lists the districts and cities of the same request, other services just their one answer
  * names in a chosen language (e.g. "de"), or in the service's default when the language is empty
  * errors as `GeoServiceException` with a kind: retry later, quota used up, settings wrong, or failed
  * one call = one request: caching, throttling (`minRequestInterval()`) and retries are up to the caller
  * HTTP via `HttpTransport` - `JdkHttpTransport` (java.net.http with timeouts) or a fake in tests
* `tf.geo.service`: elevation behind the interface `ElevationProvider` - a list of positions to a list of elevations in meters
  * `SrtmElevationProvider`: SRTM tiles (.hgt, SRTM1 or SRTM3) from a local folder, nearest grid point or bilinear (`SrtmInterpolation`), the last 8 tiles in memory; `missingTiles()` tells what to download
  * `SrtmDownloader`: SRTM1 per tile from ESA (step.esa.int, 60° N - 56° S; outside automatically SRTM3) or SRTM3 in blocks from viewfinderpanoramas.org; SRTM3 tiles are replaced by SRTM1, never the other way round (`SrtmElevationProvider.upgradableTiles()` tells which) (block names as in GPXEditor) - zip entries with unsafe paths are refused, no half written files, broken tiles are replaced; a failed download doesn't stop the others, the result tells which tiles are missing and why, and which the server doesn't have (404); only entries named like tiles (`N46E011`) are written, existing tiles of an SRTM3 block aren't copied, leftovers of an aborted download (`.part`, temporary zips) older than an hour are removed
  * `JdkFileDownload`: ends a download that gets no new data for a minute; HTTP 404 comes as `FileNotOnServerException`
  * `OpenRouteServiceElevationProvider`: openrouteservice /elevation/point and /elevation/line with an API key, at most 1000 points per call; an answer for other points than asked for is an error

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
map.showTrack("tour.gpx", List.of(new TrackLine(points, "#e6194b")));

// place of a position - GeoNames with a user name, names in German
ReverseGeocoder geocoder = new GeoNamesReverseGeocoder("myGeoNamesUser", "de", new JdkHttpTransport());
Optional<Place> place = geocoder.reverse(new LatLon(46.67, 11.16));   // Italien / Trentino-Südtirol / Meran
```

## Notes

* Note on building: "gradlew build" compiles, tests and installs the jar into the local maven repo. Found there by the other repos during their build as `tf.GeoHelper:GeoHelper:<version>`. Requires JDK 25; Gradle comes via the wrapper.
* Note on JavaFX: GeoHelper only compiles against JavaFX - the application brings it. It needs `javafx.web` (which requires `javafx.media`) and `netscape.javascript.JSObject` from JavaFX's `jdk-jsobject` via `--upgrade-module-path` - see build.gradle of GeoTagger or GPXEditor.
* Note on Leaflet: version 1.8.0 - 1.9.4 leads to flickering tiles with JavaFX 20 and later.
* Note on tests: the WebView tests start JavaFX without a window, but need a desktop session.
