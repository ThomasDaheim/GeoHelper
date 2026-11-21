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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SrtmDownloaderTest {
    @TempDir
    Path temp;

    // an SRTM3 tile with 0 m everywhere - the size makes it a tile
    private static final byte[] TILE = new byte[2 * SrtmTestFiles.SRTM3 * SrtmTestFiles.SRTM3];

    // an SRTM1 tile with 0 m everywhere
    private static final byte[] TILE1 = new byte[2 * SrtmTestFiles.SRTM1 * SrtmTestFiles.SRTM1];

    // a zip with these entries, .hgt entries with the size of an SRTM3 tile, others with a few bytes
    private static byte[] zip(String... entries) throws IOException {
        return zipWith(TILE, entries);
    }

    // a zip with these entries, .hgt entries with this content
    private static byte[] zipWith(byte[] tile, String... entries) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(entry.endsWith(".hgt") ? tile : new byte[] {1, 2, 3});
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void onlyTileNamesAreWritten() throws IOException {
        // plan 8: readme.hgt became README.hgt - only [NS]dd[EW]ddd counts; lower case and sub folders stay fine
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> Files.write(target,
                zip("L32/N46E011.hgt", "n47e011.SRTMGL1.hgt", "readme.hgt")));

        downloader.download(List.of("N46E011"), SrtmFormat.SRTM3, folder, (done, total) -> { });

        assertEquals(List.of("N46E011.hgt", "N47E011.hgt"), filesIn(folder));
    }

    @Test
    void leftoversAreRemovedOthersStay() throws IOException {
        // plan 8: a killed process left .part files and temporary zips behind
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        final Path tempFolder = Files.createDirectory(temp.resolve("tmp"));
        final Instant now = Instant.parse("2026-11-21T12:00:00Z");
        final FileTime old = FileTime.from(now.minusSeconds(2 * 3600));
        final FileTime fresh = FileTime.from(now.minusSeconds(300));
        Files.setLastModifiedTime(Files.write(folder.resolve("N46E011.hgt.part"), new byte[1]), old);
        Files.setLastModifiedTime(Files.write(folder.resolve("N47E011.hgt.part"), new byte[1]), fresh);
        Files.setLastModifiedTime(Files.write(folder.resolve("notes.part"), new byte[1]), old);
        Files.setLastModifiedTime(Files.write(tempFolder.resolve("geohelper-srtm-1.zip"), new byte[1]), old);
        Files.setLastModifiedTime(Files.write(tempFolder.resolve("other.zip"), new byte[1]), old);

        SrtmDownloader.removeLeftovers(folder, tempFolder, now);

        assertEquals(List.of("N47E011.hgt.part", "notes.part"), filesIn(folder));
        assertEquals(List.of("other.zip"), filesIn(tempFolder));
    }

    @Test
    void existingSrtm3TilesOfABlockAreNotCopied() throws IOException {
        // plan 8: each existing tile of a block was copied to .part and thrown away - about 65 MB per block. A non-empty
        // folder in the way of the .part file makes any copy fail.
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        Files.write(folder.resolve("N47E011.hgt"), TILE);
        Files.write(Files.createDirectory(folder.resolve("N47E011.hgt.part")).resolve("x"), new byte[1]);
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> Files.write(target, zip("L32/N46E011.hgt", "L32/N47E011.hgt")));

        final SrtmDownloader.Result result = downloader.download(List.of("N46E011"), SrtmFormat.SRTM3, folder, (done, total) -> { });

        assertTrue(result.problems().isEmpty(), result.problems().toString());
        assertEquals(List.of("N46E011"), result.present());
    }

    @Test
    void notFoundIsReported() throws IOException {
        // plan 8: GeoTagger remembers tiles ESA doesn't have - the downloader tells them apart from other failures
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> {
            if (uri.toString().contains("N43E008")) {
                throw new FileNotOnServerException(uri);
            }
            Files.write(target, zipWith(TILE1, "N43E009.SRTMGL1.hgt"));
        });

        final SrtmDownloader.Result result = downloader.download(List.of("N43E008", "N43E009"), SrtmFormat.SRTM1, folder,
                (done, total) -> { });

        assertEquals(List.of("N43E008"), result.notOnServer());
        assertEquals(List.of("N43E009"), result.present());
        assertTrue(result.problems().get(0).contains("N43E008"), result.problems().get(0));
    }

    @Test
    void srtm1OutsideCoverageFallsBackToSrtm3() {
        // plan 7: ESA has SRTM1 only from 60° N to 56° S - further north the SRTM3 block of viewfinderpanoramas
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> { });
        assertEquals(List.of(URI.create("https://step.esa.int/auxdata/dem/SRTMGL1/N46E011.SRTMGL1.hgt.zip"),
                URI.create("https://viewfinderpanoramas.org/dem3/" + SrtmDownloader.srtm3Block(63, 19) + ".zip")),
                downloader.sources(List.of("N46E011", "N63E019"), SrtmFormat.SRTM1));
    }

    @Test
    void srtm1ReplacesSrtm3() throws IOException {
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        Files.write(folder.resolve("N46E011.hgt"), TILE);
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> Files.write(target, zipWith(TILE1, "N46E011.SRTMGL1.hgt")));

        final SrtmDownloader.Result result = downloader.download(List.of("N46E011"), SrtmFormat.SRTM1, folder, (done, total) -> { });

        assertEquals(TILE1.length, Files.size(folder.resolve("N46E011.hgt")));
        assertEquals(List.of("N46E011"), result.present());
        assertTrue(result.missing().isEmpty());
    }

    @Test
    void srtm3BlockKeepsExistingSrtm1Tiles() throws IOException {
        // a block has many tiles - it must not make an SRTM1 tile worse
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        Files.write(folder.resolve("N47E011.hgt"), TILE1);
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> Files.write(target, zip("L32/N46E011.hgt", "L32/N47E011.hgt")));

        final SrtmDownloader.Result result = downloader.download(List.of("N46E011"), SrtmFormat.SRTM3, folder, (done, total) -> { });

        assertEquals(TILE1.length, Files.size(folder.resolve("N47E011.hgt")));
        assertEquals(TILE.length, Files.size(folder.resolve("N46E011.hgt")));
        assertEquals(List.of("N46E011"), result.present());
    }

    @Test
    void failedUpgradeKeepsSrtm3() throws IOException {
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        Files.write(folder.resolve("N46E011.hgt"), TILE);
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> {
            throw new IOException("Download of " + uri + " failed (HTTP 503)");
        });

        final SrtmDownloader.Result result = downloader.download(List.of("N46E011"), SrtmFormat.SRTM1, folder, (done, total) -> { });

        assertEquals(TILE.length, Files.size(folder.resolve("N46E011.hgt")));
        assertEquals(List.of("N46E011"), result.missing());
        assertTrue(result.problems().get(0).contains("HTTP 503"), result.problems().get(0));
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

        final SrtmDownloader.Result result = downloader.download(List.of("N46E011", "N47E011"), SrtmFormat.SRTM3, folder,
                (done, total) -> progress.add(done));

        assertEquals(List.of("N46E011.hgt", "N47E011.hgt"), filesIn(folder));
        assertEquals(List.of("N46E011", "N47E011"), result.present());
        assertTrue(result.missing().isEmpty() && result.problems().isEmpty());
        assertEquals(List.of(1), progress);
    }

    @Test
    void unavailableSourceDoesNotStopTheOthers() throws IOException {
        // final review 5b: ESA has no tiles of the open sea - a ferry trip must still get the tiles on land
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> {
            if (uri.toString().contains("N43E008")) {
                throw new IOException("Download of " + uri + " failed (HTTP 404)");
            }
            Files.write(target, zipWith(TILE1, "N43E009.SRTMGL1.hgt"));
        });

        final SrtmDownloader.Result result = downloader.download(List.of("N43E008", "N43E009"), SrtmFormat.SRTM1, folder,
                (done, total) -> { });

        assertEquals(List.of("N43E009"), result.present());
        assertEquals(List.of("N43E008"), result.missing());
        assertEquals(1, result.problems().size());
        assertTrue(result.problems().get(0).contains("HTTP 404"), result.problems().get(0));
    }

    @Test
    void brokenTileIsReplaced() throws IOException {
        // final review 5b: a truncated file must not block the tile forever
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        Files.write(folder.resolve("N46E011.hgt"), new byte[] {1, 2, 3});
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> Files.write(target, zipWith(TILE1, "N46E011.SRTMGL1.hgt")));

        final SrtmDownloader.Result result = downloader.download(List.of("N46E011"), SrtmFormat.SRTM1, folder, (done, total) -> { });

        assertEquals(TILE1.length, Files.size(folder.resolve("N46E011.hgt")));
        assertEquals(List.of("N46E011"), result.present());
    }

    @Test
    void zipWithoutTheTileReportsItMissing() throws IOException {
        // final review 5b: e.g. an HTML page instead of a zip - the user must learn why
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> Files.write(target, zip("readme.txt")));

        final SrtmDownloader.Result result = downloader.download(List.of("N46E011"), SrtmFormat.SRTM1, folder, (done, total) -> { });

        assertEquals(List.of("N46E011"), result.missing());
        assertEquals(List.of("N46E011: not contained in the download"), result.problems());
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

        final SrtmDownloader.Result result = downloader.download(List.of("N46E011"), SrtmFormat.SRTM1, folder, (done, total) -> { });

        assertFalse(Files.exists(temp.resolve("evil.hgt")));
        assertTrue(filesIn(folder).isEmpty());
        assertTrue(result.problems().get(0).contains("Unsafe path"), result.problems().get(0));
    }

    @Test
    void failedDownloadLeavesNoFile() throws IOException {
        final Path folder = Files.createDirectory(temp.resolve("srtm"));
        final SrtmDownloader downloader = new SrtmDownloader((uri, target) -> {
            Files.write(target, new byte[] {1, 2});
            throw new IOException("connection reset");
        });

        final SrtmDownloader.Result result = downloader.download(List.of("N46E011"), SrtmFormat.SRTM1, folder, (done, total) -> { });

        assertTrue(filesIn(folder).isEmpty());
        assertEquals(List.of("N46E011"), result.missing());
        assertTrue(result.problems().get(0).contains("connection reset"), result.problems().get(0));
    }
}
