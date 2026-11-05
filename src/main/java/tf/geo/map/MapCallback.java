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

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import tf.geo.LatLon;

/**
 * Bridge object "jscallback" - JavaScript in the map page calls these methods, same as in GPXEditor.
 * Must be public with public methods, otherwise the WebView can't call them.
 */
public final class MapCallback {
    private static final Logger LOGGER = Logger.getLogger(MapCallback.class.getName());

    private MapListener listener = new MapListener() { };

    MapCallback() {
    }

    void setListener(MapListener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    /**
     * @param id id of the clicked marker
     * @param shiftDown true if shift was pressed
     */
    public void markerClicked(String id, boolean shiftDown) {
        listener.markerClicked(id, shiftDown);
    }

    /**
     * @param id id of the dragged marker
     * @param latitude new latitude
     * @param longitude new longitude
     */
    public void markerMoved(String id, double latitude, double longitude) {
        listener.markerMoved(id, LeafletMapView.normalized(latitude, longitude));
    }

    /**
     * @param message debug output of the scripts
     */
    public void log(String message) {
        LOGGER.log(Level.FINE, message);
    }
}