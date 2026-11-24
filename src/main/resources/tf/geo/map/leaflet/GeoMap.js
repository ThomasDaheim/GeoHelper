// GeoHelper: markers by id (clustered unless selected), tracks by id, pixel <-> lat/lng
var geoMarkers = {};
// geoClusterOptions is set by LeafletMapView from MapConfig - always with chunkedLoading: false: markers still
// waiting for their chunk can't be removed (removeLayer ignores them) and would be added later anyway - ghosts
var geoCluster = L.markerClusterGroup(geoClusterOptions);
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

// ctrl + drag draws a selection rectangle (in Java) - the map must neither move nor box-zoom meanwhile
// same as GPXEditor MapHelper.js
function geoDisableCtrlDrag(e) {
    if (e.originalEvent.ctrlKey) {
        myMap.dragging.disable();
        myMap.boxZoom.disable();
    }
}
function geoEnableCtrlDrag(e) {
    if (e.originalEvent.ctrlKey) {
        myMap.dragging.enable();
        myMap.boxZoom.enable();
    }
}
myMap.on('mousedown', geoDisableCtrlDrag);
myMap.on('mouseup', geoEnableCtrlDrag);

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

// lines: [{coords: [[lat, lng], ...], color: "..."}] - shown, hidden and removed together
function geoShowTrack(id, lines) {
    geoRemoveTrack(id);
    var polylines = [];
    for (var i = 0; i < lines.length; i++) {
        polylines.push(L.polyline(lines[i].coords, { color: lines[i].color, weight: 3, opacity: 0.8 }));
    }
    geoTracks[id] = L.featureGroup(polylines).addTo(myMap);
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

// coordinates of the mouse and of the center - like GPXEditor's MousePosition and MapCenter (plan 9)
var GeoCoordinates = L.Control.extend({
    options: { position: 'topleft' },
    onAdd: function () {
        var div = L.DomUtil.create('div', 'geo-coordinates');
        div.style.cssText = 'background: rgba(255, 255, 255, 0.8); padding: 2px 6px; font: 12px sans-serif; color: #333;';
        L.DomEvent.disableClickPropagation(div);
        this.mouse = L.DomUtil.create('div', '', div);
        this.center = L.DomUtil.create('div', '', div);
        this.mouse.textContent = 'Mouse: –';
        return div;
    }
});
var geoCoordinates = new GeoCoordinates().addTo(myMap);

function geoFormatLatLng(latlng) {
    var wrapped = myMap.wrapLatLng(latlng);
    return wrapped.lat.toFixed(5) + ' | ' + wrapped.lng.toFixed(5);
}
function geoUpdateCenter() {
    geoCoordinates.center.textContent = 'Center: ' + geoFormatLatLng(myMap.getCenter());
}
myMap.on('mousemove', function (e) {
    geoCoordinates.mouse.textContent = 'Mouse: ' + geoFormatLatLng(e.latlng);
});
myMap.on('move', geoUpdateCenter);
geoUpdateCenter();

// both lines, for tests
function geoCoordinatesText() {
    return geoCoordinates.mouse.textContent + '\n' + geoCoordinates.center.textContent;
}

// popup at a marker - opened at its position, so that it works for a clustered marker as well (plan 9); its links
// are reported to Java with the marker id
var geoPopup = null;
function geoShowPopup(id, html) {
    var marker = geoMarkers[id];
    if (!marker) {
        return;
    }
    var content = document.createElement('div');
    content.innerHTML = html;
    var links = content.querySelectorAll('a[data-link]');
    for (var i = 0; i < links.length; i++) {
        links[i].addEventListener('click', function (e) {
            e.preventDefault();
            jscallback.popupLinkClicked(id, this.getAttribute('data-link'));
        });
    }
    // closeOnClick false: Leaflet would close it on any click, also on a marker or a cluster the user opens to get to
    // a file - GeoTagger moves the popup to the file the click selects (manual check of GeoTagger plan 10)
    geoPopup = L.popup({ maxWidth: 400, closeOnClick: false }).setLatLng(marker.getLatLng()).setContent(content).openOn(myMap);
}
// a click on the map itself still closes it - clicks on markers don't reach the map (bubblingMouseEvents false)
myMap.on('click', function () {
    if (geoPopup !== null) {
        myMap.closePopup(geoPopup);
    }
});
function geoPopupOpen() {
    return geoPopup !== null && myMap.hasLayer(geoPopup);
}

// a position without a file, e.g. the selected track point (GeoTagger plan 12) - a circle, not draggable
var geoPosition = null;
function geoShowPosition(lat, lng) {
    geoHidePosition();
    geoPosition = L.circleMarker([lat, lng], { radius: 7, color: '#d000d0', weight: 2, fillColor: '#d000d0',
        fillOpacity: 0.4, interactive: false }).addTo(myMap);
}
function geoHidePosition() {
    if (geoPosition !== null) {
        myMap.removeLayer(geoPosition);
        geoPosition = null;
    }
}
// for tests: "lat,lng" or null
function geoPositionShown() {
    if (geoPosition === null) {
        return null;
    }
    var position = geoPosition.getLatLng();
    return position.lat + ',' + position.lng;
}

// the id of the file marker whose icon is at this point of the map container - null for a cluster or the map
// itself (GeoTagger plan 12)
function geoMarkerIdAt(x, y) {
    var element = document.elementFromPoint(x, y);
    var icon = element ? element.closest('.leaflet-marker-icon') : null;
    if (!icon) {
        return null;
    }
    for (var id in geoMarkers) {
        if (geoMarkers[id]._icon === icon) {
            return id;
        }
    }
    return null;
}
// for tests: the middle of a marker's icon in the map container, null if it isn't shown on its own
function geoMarkerIconCenter(id) {
    var marker = geoMarkers[id];
    if (!marker || !marker._icon) {
        return null;
    }
    var bounds = marker._icon.getBoundingClientRect();
    return [bounds.left + bounds.width / 2, bounds.top + bounds.height / 2];
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