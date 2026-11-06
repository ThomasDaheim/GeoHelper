// GeoHelper: markers by id (clustered unless selected), tracks by id, pixel <-> lat/lng
var geoMarkers = {};
// no chunkedLoading: markers still waiting for their chunk can't be removed (removeLayer ignores them) and
// would be added later anyway - ghost markers on the map
var geoCluster = L.markerClusterGroup({ chunkedLoading: false, spiderfyOnMaxZoom: true });
var geoSelected = L.layerGroup();
var geoTracks = {};
myMap.addLayer(geoCluster);
myMap.addLayer(geoSelected);

// zoomed far out the world repeats - markers and tracks exist only in the middle copy: keep the view there
// (worldCopyJump does this while dragging only, not e.g. for zooming around the mouse)
function geoKeepInWorld() {
    var center = myMap.getCenter();
    // only outside the world: wrapLatLng changes e.g. 11.55 to 11.550000000000011, comparing its result would loop
    if (center.lng < -180 || center.lng > 180) {
        myMap.setView(myMap.wrapLatLng(center), myMap.getZoom(), { animate: false });
    }
}
myMap.on('moveend', geoKeepInWorld);

function geoRemoveMarker(id) {
    var marker = geoMarkers[id];
    if (marker) {
        geoCluster.removeLayer(marker);
        geoSelected.removeLayer(marker);
        delete geoMarkers[id];
    }
}

// list: [{id, lat, lng, title, icon, selected}] - replaces markers with the same id
function geoUpdateMarkers(list) {
    var clustered = [];
    for (var i = 0; i < list.length; i++) {
        var data = list[i];
        geoRemoveMarker(data.id);
        var marker = L.marker([data.lat, data.lng], { title: data.title, icon: window[data.icon],
            draggable: data.selected, zIndexOffset: data.selected ? 1000 : 0 });
        marker.geoId = data.id;
        marker.on('click', function (e) {
            jscallback.markerClicked(this.geoId, e.originalEvent ? e.originalEvent.shiftKey : false);
        });
        marker.on('dragend', function () {
            var position = this.getLatLng();
            jscallback.markerMoved(this.geoId, position.lat, position.lng);
        });
        geoMarkers[data.id] = marker;
        if (data.selected) {
            geoSelected.addLayer(marker);
        } else {
            clustered.push(marker);
        }
    }
    geoCluster.addLayers(clustered);
}

function geoRemoveMarkers(ids) {
    for (var i = 0; i < ids.length; i++) {
        geoRemoveMarker(ids[i]);
    }
}

function geoMarkerCount() { return Object.keys(geoMarkers).length; }
function geoClusteredCount() { return geoCluster.getLayers().length; }

function geoRemoveTrack(id) {
    if (geoTracks[id]) {
        myMap.removeLayer(geoTracks[id]);
        delete geoTracks[id];
    }
}

function geoShowTrack(id, coords, color) {
    geoRemoveTrack(id);
    geoTracks[id] = L.polyline(coords, { color: color, weight: 3, opacity: 0.8 }).addTo(myMap);
}

function geoSetTrackVisible(id, visible) {
    var track = geoTracks[id];
    if (!track) {
        return;
    }
    if (visible && !myMap.hasLayer(track)) {
        track.addTo(myMap);
    } else if (!visible) {
        myMap.removeLayer(track);
    }
}

function geoTrackCount() { return Object.keys(geoTracks).length; }
function geoVisibleTrackCount() {
    var count = 0;
    for (var id in geoTracks) {
        if (myMap.hasLayer(geoTracks[id])) {
            count++;
        }
    }
    return count;
}

function geoFitBounds(coords) {
    if (coords.length === 0) {
        return;
    }
    // no animation: the map jumps, and its state is final right away
    if (coords.length === 1) {
        myMap.setView(coords[0], Math.max(myMap.getZoom(), 14), { animate: false });
    } else {
        myMap.fitBounds(coords, { padding: [20, 20], maxZoom: 16, animate: false });
    }
}

// x / y relative to the map container - same as GPXEditor MapHelper.js
function getLatLngForPoint(x, y) {
    var latlng = myMap.containerPointToLatLng(L.point(x, y));
    return [latlng.lat, latlng.lng];
}

function getPointForLatLng(lat, lng) {
    var point = myMap.latLngToContainerPoint(L.latLng(lat, lng));
    return [point.x, point.y];
}