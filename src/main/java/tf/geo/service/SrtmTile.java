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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import tf.geo.LatLon;

/**
 * One SRTM tile (.hgt): 1° x 1°, named after its south-west corner, e.g. N46E011. Big-endian 16 bit heights in meters,
 * rows from north to south, columns from west to east; -32768 means "no value". SRTM1 has 3601 x 3601 points (1 arc
 * second), SRTM3 1201 x 1201 (3 arc seconds) - taken over from GPXEditor's SRTMDataHelper / SRTMData.
 */
public final class SrtmTile {
    private static final int SRTM1_SIZE = 3601;
    private static final int SRTM3_SIZE = 1201;
    private static final short VOID = Short.MIN_VALUE;

    private final int size;
    private final short[] heights;

    private SrtmTile(int size, short[] heights) {
        this.size = size;
        this.heights = heights;
    }

    /**
     * @param position a position
     * @return name of the tile containing it, e.g. N46E011 or S34W071 - by the floor of latitude and longitude
     */
    public static String name(LatLon position) {
        final int latitude = (int) Math.floor(position.latitude());
        final int longitude = (int) Math.floor(position.longitude());
        return String.format(Locale.ROOT, "%s%02d%s%03d", latitude >= 0 ? "N" : "S", Math.abs(latitude),
                longitude >= 0 ? "E" : "W", Math.abs(longitude));
    }

    /**
     * Cheap check without reading the file - e.g. for the files to download.
     *
     * @param file a .hgt file
     * @return true if it exists with the size of an SRTM1 or SRTM3 tile
     */
    public static boolean isTile(Path file) {
        return format(file).isPresent();
    }

    /**
     * Cheap check without reading the file - e.g. whether an SRTM3 tile could be replaced by SRTM1.
     *
     * @param file a .hgt file
     * @return SRTM1 or SRTM3 by the size of the file - empty if it doesn't exist or has another size
     */
    public static Optional<SrtmFormat> format(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            final long bytes = Files.size(file);
            if (bytes == 2L * SRTM1_SIZE * SRTM1_SIZE) {
                return Optional.of(SrtmFormat.SRTM1);
            }
            return bytes == 2L * SRTM3_SIZE * SRTM3_SIZE ? Optional.of(SrtmFormat.SRTM3) : Optional.empty();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    /**
     * @param file a .hgt file
     * @return the tile - empty if the file doesn't exist or has neither the size of SRTM1 nor of SRTM3
     * @throws UncheckedIOException if the file can't be read
     */
    public static Optional<SrtmTile> read(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            final long bytes = Files.size(file);
            final int size = bytes == 2L * SRTM1_SIZE * SRTM1_SIZE ? SRTM1_SIZE : bytes == 2L * SRTM3_SIZE * SRTM3_SIZE ? SRTM3_SIZE : 0;
            if (size == 0) {
                return Optional.empty();
            }
            final short[] heights = new short[size * size];
            ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(heights);
            return Optional.of(new SrtmTile(size, heights));
        } catch (IOException ex) {
            throw new UncheckedIOException("Can't read SRTM file " + file, ex);
        }
    }

    /**
     * @param position a position inside this tile - see name()
     * @param interpolation nearest grid point or bilinear
     * @return its elevation in meters - empty if the grid points around are "no value"
     */
    public OptionalDouble elevation(LatLon position, SrtmInterpolation interpolation) {
        final int last = size - 1;
        // rows count from the north edge, columns from the west edge
        final double row = clamp((Math.floor(position.latitude()) + 1 - position.latitude()) * last, last);
        final double col = clamp((position.longitude() - Math.floor(position.longitude())) * last, last);
        if (interpolation == SrtmInterpolation.NEAREST) {
            final short height = height((int) Math.round(row), (int) Math.round(col));
            return height == VOID ? OptionalDouble.empty() : OptionalDouble.of(height);
        }
        final int row0 = (int) Math.floor(row);
        final int col0 = (int) Math.floor(col);
        final int row1 = Math.min(row0 + 1, last);
        final int col1 = Math.min(col0 + 1, last);
        final double dRow = row - row0;
        final double dCol = col - col0;
        // only valid neighbours count, weighted - none valid: no value
        double sum = 0;
        double weights = 0;
        final int[][] corners = {{row0, col0}, {row0, col1}, {row1, col0}, {row1, col1}};
        final double[] cornerWeights = {(1 - dRow) * (1 - dCol), (1 - dRow) * dCol, dRow * (1 - dCol), dRow * dCol};
        for (int i = 0; i < corners.length; i++) {
            final short height = height(corners[i][0], corners[i][1]);
            if (height != VOID && cornerWeights[i] > 0) {
                sum += cornerWeights[i] * height;
                weights += cornerWeights[i];
            }
        }
        if (weights == 0) {
            // exactly on a void grid point, or all neighbours void
            final short height = height((int) Math.round(row), (int) Math.round(col));
            return height == VOID ? OptionalDouble.empty() : OptionalDouble.of(height);
        }
        return OptionalDouble.of(sum / weights);
    }

    private short height(int row, int col) {
        return heights[row * size + col];
    }

    private static double clamp(double value, int last) {
        return Math.max(0, Math.min(last, value));
    }
}
