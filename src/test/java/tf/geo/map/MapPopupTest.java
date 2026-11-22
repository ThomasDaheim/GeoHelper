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
package tf.geo.map;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapPopupTest {
    @Test
    void htmlHasTitleRowsAndLinks() {
        final MapPopup popup = new MapPopup("IMG_5846.CR2", List.of(new MapPopup.Row("Altitude", "46.5 m")),
                List.of(new MapPopup.LinkLine("Select nearby:", List.of(new MapPopup.Link("radius-10", "< 10 m")))));

        final String html = popup.html();

        assertTrue(html.contains("IMG_5846.CR2"), html);
        assertTrue(html.contains("Altitude") && html.contains("46.5 m"), html);
        assertTrue(html.contains("Select nearby:"), html);
        assertTrue(html.contains("data-link=\"radius-10\""), html);
        assertTrue(html.contains("&lt; 10 m"), html);
    }

    @Test
    void textIsEscaped() {
        // plan 9: file names are text, not HTML
        final MapPopup popup = new MapPopup("a&b <1>.jpg", List.of(new MapPopup.Row("City", "\"<u>\"")),
                List.of(new MapPopup.LinkLine("", List.of(new MapPopup.Link("x\"y", "<i>copy</i>")))));

        final String html = popup.html();

        assertTrue(html.contains("a&amp;b &lt;1&gt;.jpg"), html);
        assertTrue(html.contains("&quot;&lt;u&gt;&quot;"), html);
        assertTrue(html.contains("&lt;i&gt;copy&lt;/i&gt;"), html);
        assertTrue(html.contains("data-link=\"x&quot;y\""), html);
        assertFalse(html.contains("<1>") || html.contains("<u>") || html.contains("<i>"), html);
    }
}
