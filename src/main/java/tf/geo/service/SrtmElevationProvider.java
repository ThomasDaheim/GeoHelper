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

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import tf.geo.LatLon;

/**
 * Elevation from SRTM tiles (.hgt) in a local folder - offline, no key. A missing tile gives no value; download it
 * with SrtmDownloader. At most 8 tiles stay in memory (an SRTM1 tile takes about 26 MB).
 */
public final class SrtmElevationProvider implements ElevationProvider {
    private static final int CACHED_TILES = 8;

    private final Path folder;
    private final SrtmInterpolation interpolation;
    // the tiles used last - missing tiles aren't remembered, so that downloaded ones are found right away
    private final Map<String, SrtmTile> tiles = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SrtmTile> eldest) {
            return size() > CACHED_TILES;
        }
    };

    /**
     * @param folder folder with the .hgt files, named like N46E011.hgt
     * @param interpolation nearest grid point or bilinear
     */
    public SrtmElevationProvider(Path folder, SrtmInterpolation interpolation) {
        this.folder = Objects.requireNonNull(folder);
        this.interpolation = Objects.requireNonNull(interpolation);
    }

    @Override
    public synchronized List<OptionalDouble> elevations(List<LatLon> positions) throws GeoServiceException {
        final List<OptionalDouble> result = new ArrayList<>(positions.size());
        for (LatLon position : positions) {
            final Optional<SrtmTile> tile;
            try {
                tile = tile(SrtmTile.name(position));
            } catch (UncheckedIOException ex) {
                throw new GeoServiceException(GeoServiceException.Kind.FAILED, ex.getMessage(), ex);
            }
            result.add(tile.map(found -> found.elevation(position, interpolation)).orElse(OptionalDouble.empty()));
        }
        return result;
    }

    @Override
    public String name() {
        return "SRTM";
    }

    /**
     * @param positions positions to look up
     * @return the tiles without a file of the size of a tile in the folder, sorted, each once - the files aren't read,
     * so this is quick enough for the JavaFX thread
     */
    public List<String> missingTiles(List<LatLon> positions) {
        return positions.stream()
                .map(SrtmTile::name)
                .distinct()
                .sorted()
                .filter(name -> !SrtmTile.isTile(file(name)))
                .toList();
    }

    private Optional<SrtmTile> tile(String name) {
        final SrtmTile cached = tiles.get(name);
        if (cached != null) {
            return Optional.of(cached);
        }
        final Optional<SrtmTile> read = SrtmTile.read(file(name));
        read.ifPresent(tile -> tiles.put(name, tile));
        return read;
    }

    // N46E011.hgt - also n46e011.hgt on file systems that care
    private Path file(String name) {
        final Path upper = folder.resolve(name + ".hgt");
        return Files.isRegularFile(upper) ? upper : folder.resolve(name.toLowerCase(Locale.ROOT) + ".hgt");
    }
}
