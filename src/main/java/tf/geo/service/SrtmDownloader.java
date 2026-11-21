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
import java.io.InputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads missing SRTM tiles into a folder - SRTM1 per tile from ESA, SRTM3 in blocks from viewfinderpanoramas.org.
 * Taken over from GPXEditor's SRTMDownloader, including its names of the SRTM3 blocks.
 */
public final class SrtmDownloader {
    private static final String SRTM1_URL = "https://step.esa.int/auxdata/dem/SRTMGL1/";
    private static final String SRTM3_URL = "https://viewfinderpanoramas.org/dem3/";
    private static final String SRTM3_ANTARCTICA_URL = "https://viewfinderpanoramas.org/ANTDEM3/";
    private static final String HGT = ".hgt";
    private static final String PART = ".part";
    private static final String TEMP_PREFIX = "geohelper-srtm-";
    private static final Duration LEFTOVER_AGE = Duration.ofHours(1);
    private static final Pattern TILE_NAME = Pattern.compile("[NS]\\d{2}[EW]\\d{3}");

    // blocks of viewfinderpanoramas with names of their own - see GPXEditor's SRTMDownloader
    private static final Set<String> V2 = Set.of("R33", "R34", "R35", "R36", "R37", "R38",
            "Q32", "Q33", "Q34", "Q35", "Q36", "Q37", "Q38", "Q39", "Q40",
            "P31", "P32", "P33", "P34", "P35", "P36", "P37", "P38", "P39", "P40");
    private static final Set<String> SVALBARD = Set.of("U33", "U34", "U35", "U36", "T32", "T33", "T34", "T35", "T36");
    private static final Set<String> FJ = Set.of("U37", "U38", "U39", "U40", "U41", "T39");
    private static final Set<String> GL_NORTH = Set.of("T20", "T21", "T22", "T23", "T24", "T25", "T26", "T27", "T28",
            "U23", "U24", "U25", "U26", "U27", "U28");
    private static final Set<String> GL_SOUTH = Set.of("O22", "O23", "O24", "P22", "P23", "P24");
    private static final Set<String> GL_EAST = Set.of("Q24", "Q25", "R24", "R25", "R26", "R27", "S24", "S25", "S26", "S27");
    private static final Set<String> GL_WEST = Set.of("Q21", "Q22", "Q23", "R21", "R22", "R23", "S21", "S22", "S23");
    private static final Set<String> ANTARCTICA_PREFIXES = Set.of("SP", "SQ", "SR", "SS", "ST", "SU", "SV", "SW", "SX", "SY", "SZ");
    private static final Set<String> ANTARCTICA = Set.of("01-15", "16-30", "31-45", "46-60");

    private final FileDownload download;

    /**
     * @param download how files are downloaded, e.g. JdkFileDownload
     */
    public SrtmDownloader(FileDownload download) {
        this.download = Objects.requireNonNull(download);
    }

    /**
     * @param tiles names like N46E011
     * @param format SRTM1 (tiles outside 60° N - 56° S still come as SRTM3 - ESA has no SRTM1 there) or SRTM3
     * @return the files to download for them, each once (SRTM3: one block contains several tiles)
     */
    public List<URI> sources(List<String> tiles, SrtmFormat format) {
        final Set<URI> sources = new LinkedHashSet<>();
        for (String tile : tiles) {
            // SRTM1 only where ESA has it - outside the SRTM3 block (plan 7)
            if (format.forTile(tile) == SrtmFormat.SRTM1) {
                sources.add(URI.create(SRTM1_URL + tile + ".SRTMGL1.hgt.zip"));
            } else {
                final String block = srtm3Block(latitude(tile), longitude(tile));
                sources.add(URI.create((ANTARCTICA.contains(block) ? SRTM3_ANTARCTICA_URL : SRTM3_URL) + block + ".zip"));
            }
        }
        return List.copyOf(sources);
    }

    /**
     * Downloads the zips and puts their .hgt files into the folder - missing and broken tiles are written, SRTM3 tiles
     * are replaced by SRTM1, an SRTM1 tile is never replaced by SRTM3; nothing is left half written. A failing download
     * doesn't stop the others (e.g. ESA has no tiles of the open sea).
     *
     * @param tiles names like N46E011
     * @param format SRTM1 (SRTM3 outside 60° N - 56° S) or SRTM3
     * @param folder folder of the SRTM files
     * @param progress called with (files downloaded, files in total) after each download
     * @return the tiles in the wanted format afterwards, the others and why they aren't
     * @throws IOException if the folder can't be created
     */
    public Result download(List<String> tiles, SrtmFormat format, Path folder, BiConsumer<Integer, Integer> progress)
            throws IOException {
        Files.createDirectories(folder);
        final Path tempFolder = Path.of(System.getProperty("java.io.tmpdir"));
        removeLeftovers(folder, tempFolder, Instant.now());
        final List<URI> sources = sources(tiles, format);
        final List<String> problems = new ArrayList<>();
        final Set<String> failedTiles = new LinkedHashSet<>();
        final List<String> notOnServer = new ArrayList<>();
        int done = 0;
        for (URI source : sources) {
            final List<String> fromSource = tiles.stream().filter(tile -> sources(List.of(tile), format).contains(source)).toList();
            try {
                final Path zip = Files.createTempFile(tempFolder, TEMP_PREFIX, ".zip");
                try {
                    download.download(source, zip);
                    // SRTM3 blocks hold many tiles - only the SRTM1 file of a single tile can be an upgrade
                    extract(zip, folder, source.toString().startsWith(SRTM1_URL) ? SrtmFormat.SRTM1 : SrtmFormat.SRTM3);
                } finally {
                    Files.deleteIfExists(zip);
                }
            } catch (IOException ex) {
                problems.add(String.valueOf(ex.getMessage()));
                failedTiles.addAll(fromSource);
                if (ex instanceof FileNotOnServerException) {
                    notOnServer.addAll(fromSource);
                }
            }
            progress.accept(++done, sources.size());
        }
        final List<String> present = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        for (String tile : tiles) {
            // in the wanted format afterwards - or better: SRTM1 where SRTM3 was wanted
            final Optional<SrtmFormat> now = SrtmTile.format(folder.resolve(tile + HGT));
            if (now.isPresent() && (now.get() == SrtmFormat.SRTM1 || format.forTile(tile) == SrtmFormat.SRTM3)) {
                present.add(tile);
            } else {
                missing.add(tile);
                if (!failedTiles.contains(tile)) {
                    // downloaded, but not in it - e.g. an HTML page instead of a zip
                    problems.add(tile + ": not contained in the download");
                }
            }
        }
        return new Result(present, missing, problems, notOnServer);
    }

    /**
     * Outcome of a download.
     *
     * @param present the tiles in the wanted format afterwards - SRTM1 where SRTM1 was wanted and ESA has it, SRTM1 or
     *     SRTM3 where SRTM3 was wanted
     * @param missing the tiles not in the wanted format afterwards, e.g. still SRTM3 where SRTM1 was wanted
     * @param problems why, e.g. "Download of ... failed (HTTP 500)"
     * @param notOnServer the tiles whose download the server doesn't have (HTTP 404) - e.g. ESA has no tiles of the
     *     open sea
     */
    public record Result(List<String> present, List<String> missing, List<String> problems, List<String> notOnServer) {
    }

    /**
     * Removes what an aborted download left behind - .part files in the SRTM folder and temporary zips - once they are
     * older than an hour; younger ones may belong to a download still running. Best effort: what can't be removed stays.
     *
     * @param folder folder of the SRTM files
     * @param tempFolder folder of the temporary zips
     * @param now the current time
     */
    static void removeLeftovers(Path folder, Path tempFolder, Instant now) {
        removeOld(folder, name -> name.endsWith(HGT + PART), now);
        removeOld(tempFolder, name -> name.startsWith(TEMP_PREFIX) && name.endsWith(".zip"), now);
    }

    // regular files only - nothing else of the folders is touched
    private static void removeOld(Path folder, Predicate<String> ours, Instant now) {
        final Instant limit = now.minus(LEFTOVER_AGE);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
            for (Path file : files) {
                try {
                    if (ours.test(file.getFileName().toString()) && Files.isRegularFile(file)
                            && Files.getLastModifiedTime(file).toInstant().isBefore(limit)) {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException ex) {
                    // in use or gone meanwhile - the next download tries again
                }
            }
        } catch (IOException ex) {
            // folder not readable - nothing to clean up
        }
    }

    /**
     * The block of viewfinderpanoramas containing a tile - GPXEditor's getSRTM3NameForCoordinates.
     * Blocks are 4° x 6°: letters A - U from the equator (south with an "S" before), numbers 1 - 60 from the
     * antimeridian eastwards; several blocks have names of their own (Greenland, Svalbard, Antarctica, ...).
     *
     * @param latitude latitude of the tile's south-west corner, e.g. 46 for N46, -1 for S01
     * @param longitude longitude of the tile's south-west corner, e.g. 11 for E011, -72 for W072
     * @return name of the block, e.g. L32
     */
    static String srtm3Block(int latitude, int longitude) {
        final StringBuilder name = new StringBuilder();
        int letter = 'A';
        if (latitude < 0) {
            name.append('S');
            // blocks are A = 1-4, B = 5-8, ...
            letter += (-latitude - 1) / 4;
        } else {
            // blocks are A = 0-3, B = 4-7, ...
            letter += latitude / 4;
        }
        name.append((char) letter);
        // 30 = W001-W006, 29 = W007-W012, ...; 31 = E000-E005, 32 = E006-E011, ...
        final int number = longitude < 0 ? 30 - (-longitude - 1) / 6 : 31 + longitude / 6;
        name.append(String.format(Locale.ROOT, "%02d", number));

        String result = name.toString();
        if (V2.contains(result)) {
            result = result + "v2";
        }
        if (SVALBARD.contains(result)) {
            result = "SVALBARD";
        }
        if (FJ.contains(result)) {
            result = "FJ";
        }
        result = switch (result) {
            case "P29" -> "FAR";
            case "P30" -> "SHL";
            case "R29" -> "JANMAYEN";
            case "S34" -> "BEAR";
            case "Q27", "Q28" -> "ISL";
            default -> result;
        };
        if (GL_NORTH.contains(result)) {
            result = "GL-North";
        }
        if (GL_SOUTH.contains(result)) {
            result = "GL-South";
        }
        if (GL_EAST.contains(result)) {
            result = "GL-East";
        }
        if (GL_WEST.contains(result)) {
            result = "GL-West";
        }
        // far south: Antarctica in 4 files by longitude
        if (result.length() == 4 && ANTARCTICA_PREFIXES.contains(result.substring(0, 2))) {
            result = number <= 15 ? "01-15" : number <= 30 ? "16-30" : number <= 45 ? "31-45" : "46-60";
        }
        return result;
    }

    // the tiles of the zip, flat into the folder - ESA's N46E011.SRTMGL1.hgt becomes N46E011.hgt
    private static void extract(Path zip, Path folder, SrtmFormat sourceFormat) throws IOException {
        try (InputStream in = Files.newInputStream(zip); ZipInputStream entries = new ZipInputStream(in)) {
            for (ZipEntry entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry()) {
                final String path = entry.getName().replace('\\', '/');
                if (path.contains("..") || path.startsWith("/") || path.contains(":")) {
                    throw new IOException("Unsafe path in the SRTM zip: " + entry.getName());
                }
                final String fileName = path.substring(path.lastIndexOf('/') + 1);
                if (entry.isDirectory() || !fileName.toLowerCase(Locale.ROOT).endsWith(HGT)) {
                    continue;
                }
                final String tile = fileName.substring(0, fileName.indexOf('.')).toUpperCase(Locale.ROOT);
                if (!TILE_NAME.matcher(tile).matches()) {
                    // e.g. readme.hgt - only tiles go into the folder
                    continue;
                }
                final Path target = folder.resolve(tile + HGT);
                final Optional<SrtmFormat> existing = SrtmTile.format(target);
                if (existing.equals(Optional.of(SrtmFormat.SRTM1)) || (existing.isPresent() && sourceFormat == SrtmFormat.SRTM3)) {
                    // never worse: an SRTM3 block mustn't replace an SRTM1 tile (plan 7), and an existing tile needs no
                    // copy of the same quality - not even copied
                    continue;
                }
                // written under another name first - a broken download leaves no half .hgt file
                final Path part = folder.resolve(tile + HGT + PART);
                try {
                    Files.copy(entries, part, StandardCopyOption.REPLACE_EXISTING);
                    // missing or broken: replaced; SRTM3: replaced by SRTM1 only
                    if (existing.isEmpty() || SrtmTile.format(part).equals(Optional.of(SrtmFormat.SRTM1))) {
                        Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(part);
                }
            }
        }
    }

    // N46 -> 46, S01 -> -1
    private static int latitude(String tile) {
        final int value = Integer.parseInt(tile.substring(1, 3));
        return Character.toUpperCase(tile.charAt(0)) == 'S' ? -value : value;
    }

    // E011 -> 11, W072 -> -72
    private static int longitude(String tile) {
        final int value = Integer.parseInt(tile.substring(4, 7));
        return Character.toUpperCase(tile.charAt(3)) == 'W' ? -value : value;
    }
}
