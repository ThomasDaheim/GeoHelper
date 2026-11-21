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

/**
 * SRTM data to download.
 */
public enum SrtmFormat {
    /** 1 arc second (about 30 m), one zip per tile from ESA - only between 60° N and 56° S, about 25 MB per tile */
    SRTM1("SRTM1 - 30 m, ESA (SRTM3 outside 60° N - 56° S)"),
    /** 3 arc seconds (about 90 m), blocks of tiles from viewfinderpanoramas - with gaps filled, also further north */
    SRTM3("SRTM3 - 90 m, viewfinderpanoramas");

    // ESA's SRTM1: tiles with their south-west corner from 56° S to 59° N
    private static final int SRTM1_SOUTH = -56;
    private static final int SRTM1_NORTH = 59;

    private final String label;

    SrtmFormat(String label) {
        this.label = label;
    }

    /**
     * @return name for the user
     */
    public String label() {
        return label;
    }

    /**
     * @param tile name like N46E011
     * @return true if ESA has SRTM1 for it - from 60° N to 56° S
     */
    public static boolean srtm1Covers(String tile) {
        final int value = Integer.parseInt(tile.substring(1, 3));
        final int latitude = Character.toUpperCase(tile.charAt(0)) == 'S' ? -value : value;
        return latitude >= SRTM1_SOUTH && latitude <= SRTM1_NORTH;
    }

    /**
     * @param tile name like N46E011
     * @return the format to get for this tile - SRTM1 falls back to SRTM3 outside its coverage (plan 7)
     */
    public SrtmFormat forTile(String tile) {
        return this == SRTM1 && !srtm1Covers(tile) ? SRTM3 : this;
    }
}
