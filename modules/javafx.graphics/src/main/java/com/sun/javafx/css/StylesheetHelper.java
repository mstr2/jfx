/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javafx.css;

import com.sun.javafx.application.PlatformImpl;
import com.sun.javafx.logging.PlatformLogger;
import com.sun.javafx.util.Utils;
import javafx.css.StyleTheme;
import javafx.css.Stylesheet;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.function.Function;

public final class StylesheetHelper {

    private static Accessor accessor;

    private StylesheetHelper() {}

    static {
        Utils.forceInit(Stylesheet.class);
    }

    public static void setAccessor(Accessor accessor) {
        StylesheetHelper.accessor = accessor;
    }

    public static URI getURI(Stylesheet stylesheet) {
        return accessor.getURI(stylesheet);
    }

    public static Stylesheet tryLoad(URI uri, Function<String, URL> resourceLoader) {
        return accessor.tryLoad(uri, resourceLoader);
    }

    public static URL getResource(String resource) {
        return getResource(null, resource);
    }

    public static URL getResource(URI baseUrl, String resource) {
        try {
            // if baseUrl is null, then we're dealing with an in-line style.
            // If there is no scheme part, then the url is interpreted as being relative to the application's class-loader.
            URI resourceUri = new URI(resource);
            if (resourceUri.isAbsolute()) {
                return resourceUri.toURL();
            }

            StyleTheme currentStyleTheme = PlatformImpl.platformUserAgentStyleThemeProperty().get();
            if (currentStyleTheme != null) {
                URL resolvedUrl = currentStyleTheme.getResource(resource);
                if (resolvedUrl != null) {
                    return resolvedUrl;
                }
            }

            String path = resourceUri.getPath();
            if (path.startsWith("/")) {
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                // FIXME: JIGSAW -- use Class.getResource if resource is in a module
                return contextClassLoader.getResource(path.substring(1));
            }

            baseUrl = baseUrl != null ? baseUrl.trim() : null;

            if (baseUrl != null && !baseUrl.isEmpty()) {
                URI baseUri = new URI(baseUrl);
                if (baseUri.isOpaque()) {
                    // stylesheet URI is something like jar:file:
                    return new URL(baseUri.toURL(), resourceUri.getPath());
                }

                return baseUri.resolve(resourceUri).toURL();
            }

            // URL doesn't have scheme or baseUrl is null
            final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            // FIXME: JIGSAW -- use Class.getResource if resource is in a module
            return contextClassLoader.getResource(path);
        } catch (MalformedURLException | URISyntaxException e) {
            PlatformLogger cssLogger = com.sun.javafx.util.Logging.getCSSLogger();
            if (cssLogger.isLoggable(PlatformLogger.Level.WARNING)) {
                cssLogger.warning(e.getLocalizedMessage());
            }

            return null;
        }
    }

    public interface Accessor {
        URI getURI(Stylesheet stylesheet);
        Stylesheet tryLoad(URI uri, Function<String, URL> resourceLoader);
    }

}
