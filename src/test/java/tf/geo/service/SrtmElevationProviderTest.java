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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tf.geo.LatLon;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SrtmElevationProviderTest {
    private static final int VOID = -32768;

    @TempDir
    Path folder;

    // the position of a grid point (row, col) of tile N46E011 - rows from the north edge (47°), columns from 11°
    private static LatLon at(double row, double col, int size) {
        return new LatLon(47.0 - row / (size - 1), 11.0 + col / (size - 1));
    }

    private double elevation(LatLon position, SrtmInterpolation interpolation) throws GeoServiceException {
        final OptionalDouble value = new SrtmElevationProvider(folder, interpolation).elevations(List.of(position)).get(0);
        return value.orElseThrow();
    }

    @BeforeEach
    void tile() throws IOException {
        // linear, so that bilinear gives exactly 1000 + row - col between the grid points
        SrtmTestFiles.write(folder, "N46E011", SrtmTestFiles.SRTM3, (row, col) -> 1000 + row - col);
    }

    @Test
    void nearestTakesTheClosestGridPoint() throws GeoServiceException {
        assertEquals(1299, elevation(at(600.25, 300.75, SrtmTestFiles.SRTM3), SrtmInterpolation.NEAREST), 1e-9);
    }

    @Test
    void bilinearWeightsTheFourNeighbours() throws GeoServiceException {
        assertEquals(1299.5, elevation(at(600.25, 300.75, SrtmTestFiles.SRTM3), SrtmInterpolation.BILINEAR), 1e-6);
    }

    @Test
    void edgesOfTheTile() throws GeoServiceException {
        // north-west corner: row 0, column 0
        assertEquals(1000, elevation(new LatLon(47.0 - 1e-9, 11.0), SrtmInterpolation.NEAREST), 1e-9);
        // south-west corner: row 1200
        assertEquals(2200, elevation(new LatLon(46.0, 11.0), SrtmInterpolation.NEAREST), 1e-9);
        // south-east corner just inside the tile: row 1200, column 1200
        assertEquals(1000, elevation(new LatLon(46.0, 12.0 - 1e-9), SrtmInterpolation.BILINEAR), 1e-3);
    }

    @Test
    void voidValuesGiveNoAltitude() throws IOException, GeoServiceException {
        Files.delete(folder.resolve("N46E011.hgt"));
        // a hole around row 600 / column 300
        SrtmTestFiles.write(folder, "N46E011", SrtmTestFiles.SRTM3,
                (row, col) -> row == 600 && col == 300 ? VOID : 1000 + row - col);
        final SrtmElevationProvider srtm = new SrtmElevationProvider(folder, SrtmInterpolation.NEAREST);
        assertFalse(srtm.elevations(List.of(at(600, 300, SrtmTestFiles.SRTM3))).get(0).isPresent());
        // bilinear: only the valid neighbours count - (600,301), (601,300), (601,301) weighted equally here
        assertEquals((1299 + 1301 + 1300) / 3.0,
                elevation(at(600.5, 300.5, SrtmTestFiles.SRTM3), SrtmInterpolation.BILINEAR), 1e-6);
    }

    @Test
    void srtm1IsReadAsWell() throws IOException, GeoServiceException {
        SrtmTestFiles.write(folder, "N47E011", SrtmTestFiles.SRTM1, (row, col) -> 500 + col);
        assertEquals(500 + 1800, elevation(new LatLon(47.5, 11.5), SrtmInterpolation.NEAREST), 1e-9);
    }

    @Test
    void missingTileGivesNoAltitudeAndIsListed() throws GeoServiceException {
        final SrtmElevationProvider srtm = new SrtmElevationProvider(folder, SrtmInterpolation.BILINEAR);
        final List<LatLon> positions = List.of(new LatLon(10.5, 10.5), new LatLon(46.5, 11.5), new LatLon(10.2, 10.9));

        assertFalse(srtm.elevations(positions).get(0).isPresent());
        assertEquals(List.of("N10E010"), srtm.missingTiles(positions));
    }

    @Test
    void wrongFileSizeIsIgnored() throws IOException, GeoServiceException {
        Files.write(folder.resolve("N20E020.hgt"), new byte[100]);
        final SrtmElevationProvider srtm = new SrtmElevationProvider(folder, SrtmInterpolation.NEAREST);
        assertFalse(srtm.elevations(List.of(new LatLon(20.5, 20.5))).get(0).isPresent());
        assertEquals(List.of("N20E020"), srtm.missingTiles(List.of(new LatLon(20.5, 20.5))));
    }
}
