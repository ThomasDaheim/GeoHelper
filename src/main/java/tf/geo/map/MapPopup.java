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
import java.util.Objects;

/**
 * Content of a popup at a marker - like GeoSetter's image info on its map: a title, rows "label: value" and lines of
 * links. A click on a link is reported with MapListener.popupLinkClicked.
 *
 * @param title e.g. the file name
 * @param rows label and value per row
 * @param linkLines lines of links, each with an optional label in front
 */
public record MapPopup(String title, List<Row> rows, List<LinkLine> linkLines) {
    public MapPopup {
        Objects.requireNonNull(title);
        rows = List.copyOf(rows);
        linkLines = List.copyOf(linkLines);
    }

    /**
     * One row of the table.
     *
     * @param label e.g. "Altitude"
     * @param value e.g. "46.5 m"
     */
    public record Row(String label, String value) {
        public Row {
            Objects.requireNonNull(label);
            Objects.requireNonNull(value);
        }
    }

    /**
     * Links in one line.
     *
     * @param label text in front of the links, empty for none
     * @param links the links
     */
    public record LinkLine(String label, List<Link> links) {
        public LinkLine {
            Objects.requireNonNull(label);
            links = List.copyOf(links);
        }
    }

    /**
     * A link - reported with its id when clicked.
     *
     * @param id e.g. "radius-10"
     * @param text e.g. "&lt; 10 m"
     */
    public record Link(String id, String text) {
        public Link {
            Objects.requireNonNull(id);
            Objects.requireNonNull(text);
        }
    }

    /**
     * @return the popup as HTML - all texts escaped, links as {@code <a href="#" data-link="id">}
     */
    public String html() {
        final StringBuilder html = new StringBuilder("<div class=\"geo-popup\"><b>").append(escape(title)).append("</b>");
        if (!rows.isEmpty()) {
            html.append("<table>");
            rows.forEach(row -> html.append("<tr><td>").append(escape(row.label())).append(":</td><td>")
                    .append(escape(row.value())).append("</td></tr>"));
            html.append("</table>");
        }
        for (LinkLine line : linkLines) {
            html.append("<div>");
            if (!line.label().isEmpty()) {
                html.append(escape(line.label())).append(' ');
            }
            line.links().forEach(link -> html.append("<a href=\"#\" data-link=\"").append(escape(link.id())).append("\">")
                    .append(escape(link.text())).append("</a> "));
            html.append("</div>");
        }
        return html.append("</div>").toString();
    }

    // text into HTML - also into attribute values in double quotes
    private static String escape(String text) {
        final StringBuilder escaped = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
