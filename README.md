# GeoHelper
Geo related helper classes used in my other repos. Current version: v0.1

## Status

Empty so far - the code follows with the next steps of [GeoTagger](https://github.com/ThomasDaheim/GeoTagger):

* map (Leaflet in a JavaFX WebView)
* geo services: reverse geocoding, altitude, time zone

Both are taken over from [GPXEditor](https://github.com/ThomasDaheim/GPXEditor), which will switch to GeoHelper later on.

The ExifTool wrapper that started here has moved to its own library [ExifToolHelper](https://github.com/ThomasDaheim/ExifToolHelper), so its users don't get JavaFX or map dependencies.

## Notes

* Note on building: "gradlew build" compiles, tests and installs the jar into the local maven repo. Found there by the other repos during their build as `tf.GeoHelper:GeoHelper:<version>`. Requires JDK 25; Gradle comes via the wrapper.
