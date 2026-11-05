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

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;

/**
 * Runs code on the JavaFX thread for the WebView tests - no window needed.
 */
final class FxTestSupport {
    private FxTestSupport() {
    }

    /**
     * Starts the JavaFX toolkit once per test JVM.
     */
    static void startFx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // started by an earlier test class
        }
        Platform.setImplicitExit(false);
    }

    /**
     * @param <T> type of the result
     * @param work code to run on the JavaFX thread
     * @return its result
     */
    static <T> T onFx(Callable<T> work) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Exception | Error ex) {
                result.completeExceptionally(ex);
            }
        });
        try {
            return result.get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * @return a map in a scene (800 x 600), loaded and ready
     */
    static LeafletMapView readyMap() {
        final LeafletMapView map = onFx(LeafletMapView::new);
        final CompletableFuture<Void> ready = onFx(() -> {
            new Scene(map, 800, 600);
            return map.displayMap(MapConfig.defaults());
        });
        try {
            ready.get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return map;
    }

    /**
     * @param map the map
     * @param script JavaScript to run in its page
     * @return the result of the script
     */
    static Object js(LeafletMapView map, String script) {
        return onFx(() -> map.getWebView().getEngine().executeScript(script));
    }
}