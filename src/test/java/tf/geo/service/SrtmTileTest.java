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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tf.geo.LatLon;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SrtmTileTest {
    @Test
    void formatBySize(@TempDir Path folder) throws IOException {
        // plan 7: SRTM3 tiles may be upgraded to SRTM1 - the format comes from the size, the file isn't read
        final Path srtm3 = SrtmTestFiles.write(folder, "N46E011", SrtmTestFiles.SRTM3, (row, col) -> 0);
        final Path srtm1 = SrtmTestFiles.write(folder, "N47E011", SrtmTestFiles.SRTM1, (row, col) -> 0);
        final Path broken = Files.write(folder.resolve("N48E011.hgt"), new byte[] {1, 2, 3});

        assertEquals(Optional.of(SrtmFormat.SRTM3), SrtmTile.format(srtm3));
        assertEquals(Optional.of(SrtmFormat.SRTM1), SrtmTile.format(srtm1));
        assertEquals(Optional.empty(), SrtmTile.format(broken));
        assertEquals(Optional.empty(), SrtmTile.format(folder.resolve("N49E011.hgt")));
    }

    @Test
    void srtm1CoversSixtyNorthToFiftySixSouth() {
        assertTrue(SrtmFormat.srtm1Covers("N59E010"));
        assertFalse(SrtmFormat.srtm1Covers("N60E010"));
        assertTrue(SrtmFormat.srtm1Covers("S56W070"));
        assertFalse(SrtmFormat.srtm1Covers("S57W070"));
        assertEquals(SrtmFormat.SRTM3, SrtmFormat.SRTM1.forTile("N63E019"));
        assertEquals(SrtmFormat.SRTM1, SrtmFormat.SRTM1.forTile("N46E011"));
        assertEquals(SrtmFormat.SRTM3, SrtmFormat.SRTM3.forTile("N46E011"));
    }

    @Test
    void namesFollowTheSouthWestCorner() {
        assertEquals("N46E011", SrtmTile.name(new LatLon(46.67, 11.16)));
        assertEquals("S34W071", SrtmTile.name(new LatLon(-33.9, -70.6)));
        // GPXEditor counted 0 as south / west - here the floor decides
        assertEquals("N00W001", SrtmTile.name(new LatLon(0.5, -0.5)));
        assertEquals("S01E000", SrtmTile.name(new LatLon(-0.5, 0.5)));
        assertEquals("N46E011", SrtmTile.name(new LatLon(46.0, 11.0)));
    }
}
