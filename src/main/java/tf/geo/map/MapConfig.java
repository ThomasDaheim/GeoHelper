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
import tf.geo.LatLon;

/**
 * What the map shows at the start and how it bundles markers.
 *
 * @param baseLayers base layers - the first one is shown, a layer control appears for more than one
 * @param initialCenter center at the start
 * @param initialZoom zoom at the start (0 - 18)
 * @param clusterRadius markers closer than this (pixels) are bundled - markercluster can't set a minimum count
 * @param noClusteringFromZoom from this zoom on markers aren't bundled anymore
 */
public record MapConfig(List<MapLayer> baseLayers, LatLon initialCenter, int initialZoom, int clusterRadius,
        int noClusteringFromZoom) {
    /**
     * @return all layers without API key, centered on Central Europe, bundling within 40 px up to zoom 14
     */
    public static MapConfig defaults() {
        return new MapConfig(MapLayer.withoutApiKey(), new LatLon(50.0, 10.0), 5, 40, 15);
    }
}