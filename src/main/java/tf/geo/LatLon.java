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
package tf.geo;

/**
 * A position on earth in decimal degrees (WGS 84).
 *
 * @param latitude -90 (south) to 90 (north)
 * @param longitude -180 (west) to 180 (east)
 */
public record LatLon(double latitude, double longitude) {
    /**
     * @param latitude -90 (south) to 90 (north)
     * @param longitude -180 (west) to 180 (east)
     * @throws IllegalArgumentException if a value is out of range or not a number
     */
    public LatLon {
        // written as "not inside" so that NaN is rejected as well
        if (!(latitude >= -90 && latitude <= 90)) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90: " + latitude);
        }
        if (!(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180: " + longitude);
        }
    }

    // mean earth radius in meters (IUGG)
    private static final double EARTH_RADIUS = 6_371_008.8;

    /**
     * Great circle distance (haversine) - also across the antimeridian the short way.
     *
     * @param other the other position
     * @return the distance in meters
     */
    public double distanceTo(LatLon other) {
        final double lat1 = Math.toRadians(latitude);
        final double lat2 = Math.toRadians(other.latitude());
        final double sinLat = Math.sin((lat2 - lat1) / 2);
        final double sinLon = Math.sin(Math.toRadians(other.longitude() - longitude) / 2);
        final double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        return 2 * EARTH_RADIUS * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}