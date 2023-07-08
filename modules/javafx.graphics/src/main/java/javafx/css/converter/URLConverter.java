/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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

package javafx.css.converter;

import com.sun.javafx.css.StylesheetHelper;
import com.sun.javafx.util.DataURI;
import javafx.css.ParsedValue;
import javafx.css.StyleConverter;
import javafx.scene.text.Font;
import java.net.URL;

/**
 * Converter to convert a parsed value representing URL to a URL string that is
 * resolved relative to the location of the stylesheet.
 * The input value is in the form: {@code url("<path>")}.
 *
 * @since 9
 */
public final class URLConverter extends StyleConverter<ParsedValue[], String> {

    // lazy, thread-safe instatiation
    private static class Holder {
        static final URLConverter INSTANCE = new URLConverter();
        static final SequenceConverter SEQUENCE_INSTANCE = new SequenceConverter();
    }

    /**
     * Gets the {@code URLConverter} instance.
     * @return the {@code URLConverter} instance
     */
    public static StyleConverter<ParsedValue[], String> getInstance() {
        return Holder.INSTANCE;
    }

    private URLConverter() {
        super();
    }

    @Override
    public String convert(ParsedValue<ParsedValue[], String> value, Font font) {

        String url = null;

        ParsedValue[] values = value.getValue();

        String resource = values.length > 0 ? StringConverter.getInstance().convert(values[0], font) : null;
        resource = resource != null ? resource.trim() : null;

        if (resource != null && !resource.isEmpty()) {
            if (resource.startsWith("url(")) {
                resource = com.sun.javafx.util.Utils.stripQuotes(resource.substring(4, resource.length() - 1));
            } else {
                resource = com.sun.javafx.util.Utils.stripQuotes(resource);
            }

            if (DataURI.matchScheme(resource)) {
                url = resource;
            } else if (!resource.isEmpty()) {
                String stylesheetURL = values.length > 1 && values[1] != null ? (String) values[1].getValue() : null;
                URL resolvedURL = StylesheetHelper.getResource(stylesheetURL, resource);
                if (resolvedURL != null) url = resolvedURL.toExternalForm();
            }
        }

        return url;
    }

    @Override
    public String toString() {
        return "URLType";
    }

    /**
     * Converter to convert a sequence of URLs to an array of {@code String}s.
     * @since 9
     */
    public static final class SequenceConverter extends StyleConverter<ParsedValue<ParsedValue[], String>[], String[]> {

        /**
         * Gets the {@code SequenceConverter} instance.
         * @return the {@code SequenceConverter} instance
         */
        public static SequenceConverter getInstance() {
            return Holder.SEQUENCE_INSTANCE;
        }

        private SequenceConverter() {
            super();
        }

        @Override
        public String[] convert(ParsedValue<ParsedValue<ParsedValue[], String>[], String[]> value, Font font) {
            ParsedValue<ParsedValue[], String>[] layers = value.getValue();
            String[] urls = new String[layers.length];
            for (int layer = 0; layer < layers.length; layer++) {
                urls[layer] = URLConverter.getInstance().convert(layers[layer], font);
            }
            return urls;
        }

        @Override
        public String toString() {
            return "URLSeqType";
        }
    }

}
