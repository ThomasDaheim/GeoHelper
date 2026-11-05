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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Difference between the markers on the map and the wanted ones - only that is sent to the WebView.
 *
 * @param changed new or changed markers, in the order of the wanted ones
 * @param removed ids of markers that are no longer wanted, sorted
 */
public record MarkerDiff(List<MapMarker> changed, List<String> removed) {
    /**
     * @param shown markers on the map by id
     * @param wanted all markers that shall be on the map
     * @return what to update and what to remove
     * @throws IllegalArgumentException if two wanted markers have the same id
     */
    public static MarkerDiff between(Map<String, MapMarker> shown, Collection<MapMarker> wanted) {
        final List<MapMarker> changed = new ArrayList<>();
        final Set<String> wantedIds = new HashSet<>();
        for (MapMarker marker : wanted) {
            if (!wantedIds.add(marker.id())) {
                throw new IllegalArgumentException("Duplicate marker id: " + marker.id());
            }
            if (!Objects.equals(shown.get(marker.id()), marker)) {
                changed.add(marker);
            }
        }
        final List<String> removed = shown.keySet().stream().filter(id -> !wantedIds.contains(id)).sorted().toList();
        return new MarkerDiff(List.copyOf(changed), removed);
    }

    /**
     * @return true if the map is up to date
     */
    public boolean isEmpty() {
        return changed.isEmpty() && removed.isEmpty();
    }
}