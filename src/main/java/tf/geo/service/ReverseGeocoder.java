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
package tf.geo.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import tf.geo.LatLon;

/**
 * Finds the place (country, state, city, ...) at a position. One call is one request to the service - caching,
 * throttling and retries are up to the caller.
 */
public interface ReverseGeocoder {
    /**
     * @param position position to look up
     * @return the place, empty if the service knows none there
     * @throws GeoServiceException if the request failed - its kind tells whether a retry makes sense
     */
    Optional<Place> reverse(LatLon position) throws GeoServiceException;

    /**
     * Places near the position to choose from, e.g. the districts of a city - with one request like reverse().
     * By default the answer of reverse() alone, with distance 0.
     *
     * @param position position to look up
     * @return the places, the best answer (that of reverse()) first - empty if the service knows none there
     * @throws GeoServiceException if the request failed - its kind tells whether a retry makes sense
     */
    default List<NearbyPlace> nearby(LatLon position) throws GeoServiceException {
        return reverse(position).map(place -> List.of(new NearbyPlace(place, 0))).orElse(List.of());
    }

    /**
     * @return pause the caller should keep between two requests, to stay within the limits of the service
     */
    Duration minRequestInterval();

    /**
     * @return name of the service for messages, e.g. "GeoNames"
     */
    String name();
}
