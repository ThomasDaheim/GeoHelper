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

import java.util.Locale;
import java.util.stream.Stream;

/**
 * Place at a position as a reverse geocoder reports it - the parts are null where the service knows nothing.
 *
 * @param countryCode ISO 3166-1 alpha-2 code in upper case, e.g. "IT"
 * @param country name of the country
 * @param state state, province or region
 * @param city city, town or village
 * @param sublocation part of the city, e.g. a district or neighbourhood
 */
public record Place(String countryCode, String country, String state, String city, String sublocation) {
    /**
     * Trims the parts, blank parts become null, the country code upper case.
     */
    public Place {
        countryCode = clean(countryCode);
        if (countryCode != null) {
            countryCode = countryCode.toUpperCase(Locale.ROOT);
        }
        country = clean(country);
        state = clean(state);
        city = clean(city);
        sublocation = clean(sublocation);
    }

    /**
     * @return true if the service knows none of the parts
     */
    public boolean isEmpty() {
        return Stream.of(countryCode, country, state, city, sublocation).allMatch(part -> part == null);
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
