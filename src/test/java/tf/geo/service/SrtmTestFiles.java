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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntBinaryOperator;

/**
 * Synthetic SRTM tiles for the tests - no real .hgt files in the repository.
 */
public final class SrtmTestFiles {
    /** points per row of an SRTM3 tile (3 arc seconds) */
    public static final int SRTM3 = 1201;
    /** points per row of an SRTM1 tile (1 arc second) */
    public static final int SRTM1 = 3601;

    private SrtmTestFiles() {
    }

    /**
     * @param size SRTM3 or SRTM1
     * @param value height per (row, column) - rows from north to south, columns from west to east
     * @return the content of a .hgt file: big-endian shorts
     */
    public static byte[] content(int size, IntBinaryOperator value) {
        final ByteBuffer buffer = ByteBuffer.allocate(size * size * 2).order(ByteOrder.BIG_ENDIAN);
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                buffer.putShort((short) value.applyAsInt(row, col));
            }
        }
        return buffer.array();
    }

    /**
     * @param folder folder of the tiles
     * @param tile name like N46E011
     * @param size SRTM3 or SRTM1
     * @param value height per (row, column)
     * @return the written file
     * @throws IOException if it can't be written
     */
    public static Path write(Path folder, String tile, int size, IntBinaryOperator value) throws IOException {
        return Files.write(folder.resolve(tile + ".hgt"), content(size, value));
    }
}
