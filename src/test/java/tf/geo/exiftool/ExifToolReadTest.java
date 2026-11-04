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
package tf.geo.exiftool;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExifToolReadTest {
    private static ExifTool exifTool;

    @TempDir
    Path dir;

    @BeforeAll
    static void start() {
        exifTool = new ExifTool(ExifToolTestSupport.require());
    }

    @AfterAll
    static void stop() {
        if (exifTool != null) {
            exifTool.close();
        }
    }

    private static Path jpeg(Path file) throws IOException {
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "jpg", file.toFile());
        return file;
    }

    @Test
    void readsTagsWithUmlautsInValueAndPath() throws IOException {
        final Path file = jpeg(dir.resolve("Bild_ä.jpg"));
        exifTool.execute(List.of("-overwrite_original", "-XMP-photoshop:City=Völs", "-GPS:GPSLatitude#=46.5",
                "-GPS:GPSLatitudeRef#=S", file.toString()));

        final List<Map<String, Object>> entries = exifTool.readJson(List.of(file),
                List.of("XMP-photoshop:City", "Composite:GPSLatitude"));

        assertEquals(1, entries.size());
        final Map<String, Object> entry = entries.get(0);
        assertEquals(file.toAbsolutePath().normalize(), Path.of(entry.get("SourceFile").toString()).toAbsolutePath().normalize());
        assertEquals("Völs", entry.get("XMP-photoshop:City"));
        assertEquals(-46.5, ((Number) entry.get("Composite:GPSLatitude")).doubleValue(), 1e-9);
    }

    @Test
    void emptyFileListGivesEmptyResult() {
        assertTrue(exifTool.readJson(List.of(), List.of("XMP-photoshop:City")).isEmpty());
    }

    @Test
    void extractsBinaryTag() throws IOException {
        final Path thumb = jpeg(dir.resolve("thumb.jpg"));
        final Path file = jpeg(dir.resolve("image.jpg"));
        exifTool.execute(List.of("-overwrite_original", "-ThumbnailImage<=" + thumb, file.toString()));

        final Path target = dir.resolve("extracted.jpg");
        assertTrue(exifTool.extractBinary(file, "ThumbnailImage", target));
        assertArrayEquals(Files.readAllBytes(thumb), Files.readAllBytes(target));
    }

    @Test
    void missingBinaryTagGivesFalse() throws IOException {
        final Path file = jpeg(dir.resolve("plain.jpg"));
        assertFalse(exifTool.extractBinary(file, "PreviewImage", dir.resolve("none.jpg")));
    }
}
