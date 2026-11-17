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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SrtmDownloaderTest {
    @TempDir
    Path temp;

    // a zip with these entries, each with a few bytes - the content doesn't matter for the downloader
    private static byte[] zip(String... entries) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(new byte[] {1, 2, 3});
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static List<String> filesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void srtm3BlocksLikeGpxEditor() {
        // the cases of GPXEditor's TestSRTM.testSRTM3Names
        assertEquals("A31", SrtmDownloader.srtm3Block(0, 0));
        assertEquals("L10", SrtmDownloader.srtm3Block(45, -121));
        assertEquals("H30", SrtmDownloader.srtm3Block(30, -4));
        assertEquals("E31", SrtmDownloader.srtm3Block(19, 5));
        assertEquals("A43", SrtmDownloader.srtm3Block(0, 73));
        assertEquals("U44", SrtmDownloader.srtm3Block(80, 80));
        assertEquals("SA19", SrtmDownloader.srtm3Block(-1, -72));
        assertEquals("SB20", SrtmDownloader.srtm3Block(-8, -61));
        assertEquals("SE21", SrtmDownloader.srtm3Block(-20, -60));
        assertEquals("A31", SrtmDownloader.srtm3Block(1, 1));
        assertEquals("SA31", SrtmDownloader.srtm3Block(-1, 1));
        assertEquals("A30", SrtmDownloader.srtm3Block(1, -1));
        assertEquals("SA30", SrtmDownloader.srtm3Block(-1, -1));
        assertEquals("GL-North", SrtmDownloader.srtm3Block(81, -20));
        assertEquals("GL-South", SrtmDownloader.srtm3Block(62, -48));
        assertEquals("GL-East", SrtmDownloader.srtm3Block(70, -30));
        assertEquals("GL-West", SrtmDownloader.srtm3Block(70, -50));
        assertEquals("01-15", SrtmDownloader.srtm3Block(-80, -144));
        assertEquals("16-30", SrtmDownloader.srtm3Block(-72, -64));
        assertEquals("31-45", SrtmDownloader.srtm3Block(-72, 64));
        assertEquals("46-60", SrtmDownloader.srtm3Block(-72, 152));
        assertEquals("Q37v2", SrtmDownloader.srtm3Block(67, 40));
        assertEquals("SVALBARD", SrtmDownloader.srtm3Block(80, 25));
        assertEquals("FJ", SrtmDownloader.srtm3Block(80, 51));
        assertEquals("FAR", SrtmDownloader.srtm3Block(62, -7));
        assertEquals("SHL", SrtmDownloader.srtm3Block(61, -1));
        assertEquals("JANMAYEN", SrtmDownloader.srtm3Block(71, -7));
        assertEquals("BEAR", SrtmDownloader.srtm3Block(75, 19));
        assertEquals("ISL", SrtmDownloader.srtm3Block(65, -20));
    }

    @Test
    void sourcesPerFormat() {
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> { });
        assertEquals(List.of(URI.create("https://step.esa.int/auxdata/dem/SRTMGL1/N46E011.SRTMGL1.hgt.zip")),
                downloader.sources(List.of("N46E011"), SrtmFormat.SRTM1));
        // two tiles of the same block: one download
        assertEquals(List.of(URI.create("https://viewfinderpanoramas.org/dem3/L32.zip")),
                downloader.sources(List.of("N46E011", "N47E011"), SrtmFormat.SRTM3));
        assertEquals(List.of(URI.create("https://viewfinderpanoramas.org/ANTDEM3/01-15.zip")),
                downloader.sources(List.of("S80W144"), SrtmFormat.SRTM3));
    }

    @Test
    void extractsTheHgtFiles() throws IOException {
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        final byte[] block = zip("L32/N46E011.hgt", "L32/N47E011.hgt", "L32/readme.txt");
        final List<Integer> progress = new ArrayList<>();
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> Files.write(target, block));

        final List<String> present = downloader.download(List.of("N46E011", "N47E011"), SrtmFormat.SRTM3, folder,
                (done, total) -> progress.add(done));

        assertEquals(List.of("N46E011.hgt", "N47E011.hgt"), filesIn(folder));
        assertEquals(List.of("N46E011", "N47E011"), present);
        assertEquals(List.of(1), progress);
    }

    @Test
    void esaNamesAreShortened() throws IOException {
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> Files.write(target, zip("N46E011.SRTMGL1.hgt")));

        downloader.download(List.of("N46E011"), SrtmFormat.SRTM1, folder, (done, total) -> { });

        assertEquals(List.of("N46E011.hgt"), filesIn(folder));
    }

    @Test
    void zipSlipIsRefused() throws IOException {
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> Files.write(target, zip("../evil.hgt")));

        assertThrows(IOException.class, () -> downloader.download(List.of("N46E011"), SrtmFormat.SRTM1, folder, (done, total) -> { }));

        assertFalse(Files.exists(temp.resolve("evil.hgt")));
        assertTrue(filesIn(folder).isEmpty());
    }

    @Test
    void failedDownloadLeavesNoFile() throws IOException {
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> {
            Files.write(target, new byte[] {1, 2});
            throw new IOException("connection reset");
        });

        assertThrows(IOException.class, () -> downloader.download(List.of("N46E011"), SrtmFormat.SRTM1, folder, (done, total) -> { }));

        assertTrue(filesIn(folder).isEmpty());
    }
}
