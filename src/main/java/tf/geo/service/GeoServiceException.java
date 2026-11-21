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

import java.util.Objects;

/**
 * A request to a geo service failed. The message is meant for the user.
 */
public final class GeoServiceException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * What went wrong - decides what the caller does next.
     */
    public enum Kind {
        /** temporary problem (network, rate limit per minute, server busy) - try again after a pause */
        RETRYABLE,
        /** the credits of the account are used up - further requests fail as well */
        QUOTA_EXCEEDED,
        /** key, token or user name missing or rejected - nothing works until the settings are fixed */
        CONFIGURATION,
        /** this request failed, others might work */
        FAILED
    }

    private final Kind kind;

    /**
     * @param kind what went wrong
     * @param message text for the user
     */
    public GeoServiceException(Kind kind, String message) {
        super(message);
        this.kind = Objects.requireNonNull(kind);
    }

    /**
     * @param kind what went wrong
     * @param message text for the user
     * @param cause the underlying problem
     */
    public GeoServiceException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind);
    }

    /**
     * @return what went wrong
     */
    public Kind kind() {
        return kind;
    }

    /**
     * @param cause a problem, e.g. of the connection
     * @return its message - or the simple name of its class if it has none, e.g. "ConnectException"
     */
    public static String describe(Throwable cause) {
        final String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
