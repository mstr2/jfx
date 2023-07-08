/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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

package javafx.css;

import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.css.StyleConverter.StringStore;
import javafx.scene.text.Font;

import com.sun.javafx.collections.TrackableObservableList;
import com.sun.javafx.css.FontFaceImpl;
import com.sun.javafx.css.StyleManager;
import com.sun.javafx.css.StylesheetHelper;
import com.sun.javafx.logging.PlatformLogger;
import com.sun.javafx.util.DataURI;
import com.sun.javafx.util.Logging;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A stylesheet which can apply properties to a tree of objects.  A stylesheet
 * is a collection of zero or more {@link Rule Rules}, each of which is applied
 * to each object in the tree.  Typically the selector will examine the object to
 * determine whether or not it is applicable, and if so it will apply certain
 * property values to the object.
 *
 * @since 9
 */
public class Stylesheet {

    static {
        StylesheetHelper.setAccessor(new StylesheetHelper.Accessor() {
            @Override
            public URI getURI(Stylesheet stylesheet) {
                return stylesheet.uri;
            }

            @Override
            public Stylesheet tryLoad(URI uri, Function<String, URL> resourceLoader) {
                return Stylesheet.tryLoad(uri, resourceLoader);
            }
        });
    }

    private static final PlatformLogger LOGGER = Logging.getCSSLogger();
    private static final Pattern FILENAME_EXTENSION_PATTERN = Pattern.compile("(?<=.)\\.[^.]+$");

    /**
     * Version number of binary CSS format. The value is incremented whenever the format of the
     * binary stream changes. This number does not correlate with JavaFX versions.
     * Version 5: persist @font-face
     * Version 6: converter classes moved to public package
     */
    final static int BINARY_CSS_VERSION = 6;

    /**
     *  The URL from which this {@code Stylesheet} was loaded.
     *
     * @return A {@code String} representation of the URL from which the stylesheet was loaded, or {@code null} if
     *         the stylesheet was created from an inline style.
     */
    public String getUrl() {
        return uri != null ? uri.toString() : null;
    }

    private URI uri;

    void initURI(URI uri) {
        if (this.uri != null) {
            throw new IllegalArgumentException();
        }

        this.uri = uri;
    }

    /**
     * Specifies the origin of this {@code Stylesheet}. We need to know this so
     * that we can make user important styles have higher priority than
     * author styles.
     */
    private StyleOrigin origin = StyleOrigin.AUTHOR;

    /**
     * Returns the origin of this {@code Stylesheet}.
     *
     * @return the origin of this {@code Stylesheet}
     */
    public StyleOrigin getOrigin() {
        return origin;
    }

    /**
     * Sets the origin of this {@code Stylesheet}.

     * @param origin the origin of this {@code Stylesheet}
     */
    public void setOrigin(StyleOrigin origin) {
        this.origin = origin;
    }

    /** All the rules contained in the stylesheet in the order they are in the file */
    private final ObservableList<Rule> rules = new TrackableObservableList<>() {

        @Override protected void onChanged(Change<Rule> c) {
            c.reset();
            while (c.next()) {
                if (c.wasAdded()) {
                    for(Rule rule : c.getAddedSubList()) {
                        rule.setStylesheet(Stylesheet.this);
                    }
                } else if (c.wasRemoved()) {
                    for (Rule rule : c.getRemoved()) {
                        if (rule.getStylesheet() == Stylesheet.this) rule.setStylesheet(null);
                    }
                }
            }
        }
    };

    /** List of all font faces */
    private final List<FontFace> fontFaces = new ArrayList<>();

    /**
     * Constructs a stylesheet with the base URI defaulting to the root
     * path of the application.
     */
    Stylesheet() {

//        ClassLoader cl = Thread.currentThread().getContextClassLoader();
//        this.url = (cl != null) ? cl.getResource("") : null;
        //
        // RT-17344
        // The above code is unreliable. The getResource call is intended
        // to return the root path of the Application instance, but it sometimes
        // returns null. Here, we'll set url to null and then when a url is
        // resolved, the url path can be used in the getResource call. For
        // example, if the css is -fx-image: url("images/duke.png"), we can
        // do cl.getResouce("images/duke.png") in URLConverter
        //

        this(null);
    }

    /**
     * Constructs a Stylesheet using the given URL as the base URI. The
     * parameter may not be null.
     *
     * @param uri the base URI for this stylesheet
     */
    Stylesheet(URI uri) {
        this.uri = uri;
    }

    /**
     * Returns the rules that are defined in this {@code Stylesheet}.
     *
     * @return a list of rules used by this {@code Stylesheet}
     */
    public List<Rule> getRules() {
        return rules;
    }

    /**
     * Returns the font faces used by this {@code Stylesheet}.
     *
     * @return a list of font faces used by this {@code Stylesheet}
     */
    public List<FontFace> getFontFaces() {
        return fontFaces;
    }

    /**
     * Indicates whether this {@code Stylesheet} is "equal to" some other object. Equality of two {@code Stylesheet}s is
     * based on the equality of their URL as defined by {@link #getUrl()}.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof Stylesheet other) {
            if (this.uri == null && other.uri == null) {
                return true;
            } else if (this.uri == null || other.uri == null) {
                return false;
            } else {
                return this.uri.equals(other.uri);
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override public int hashCode() {
        int hash = 7;
        hash = 13 * hash + (this.uri != null ? this.uri.hashCode() : 0);
        return hash;
    }

    /** Returns a string representation of this object. */
    @Override public String toString() {
        StringBuilder sbuf = new StringBuilder();
        sbuf.append("/* ");
        if (uri != null) sbuf.append(uri);
        if (rules.isEmpty()) {
            sbuf.append(" */");
        } else {
            sbuf.append(" */\n");
            for(int r=0; r<rules.size(); r++) {
                sbuf.append(rules.get(r));
                sbuf.append('\n');
            }
        }
        return sbuf.toString();
    }

    // protected for unit testing
    final void writeBinary(final DataOutputStream os, final StringStore stringStore)
        throws IOException
    {
        // Note: url is not written since it depends on runtime environment.
        int index = stringStore.addString(origin.name());
        os.writeShort(index);
        os.writeShort(rules.size());
        for (Rule r : rules) r.writeBinary(os,stringStore);

        // Version 5 adds persistence of FontFace
        List<FontFace> fontFaceList = getFontFaces();
        int nFontFaces = fontFaceList != null ? fontFaceList.size() : 0;
        os.writeShort(nFontFaces);

        for(int n=0; n<nFontFaces; n++) {
            FontFace fontFace = fontFaceList.get(n);
            if (fontFace instanceof FontFaceImpl) {
                ((FontFaceImpl)fontFace).writeBinary(os, stringStore);
            }
        }
    }

    // protected for unit testing
    final void readBinary(int bssVersion, DataInputStream is, String[] strings)
        throws IOException
    {
        this.stringStore = strings;
        final int index = is.readShort();
        this.setOrigin(StyleOrigin.valueOf(strings[index]));
        final int nRules = is.readShort();
        List<Rule> persistedRules = new ArrayList<>(nRules);
        for (int n=0; n<nRules; n++) {
            persistedRules.add(Rule.readBinary(bssVersion,is,strings));
        }
        this.rules.addAll(persistedRules);

        if (bssVersion >= 5) {
            List<FontFace> fontFaceList = this.getFontFaces();
            int nFontFaces = is.readShort();
            for (int n=0; n<nFontFaces; n++) {
                FontFace fontFace = FontFaceImpl.readBinary(bssVersion, is, strings);
                fontFaceList.add(fontFace);
            }
        }
    }

    private String[] stringStore;
    final String[] getStringStore() { return stringStore; }

    /**
     * Loads a stylesheet from the given URI.
     *
     * @param uri the URI of the requested stylesheet
     * @return the stylesheet
     * @throws NullPointerException if {@code name} is null
     * @throws FileNotFoundException if the stylesheet cannot be found or is inaccessible
     * @throws IOException if an I/O exception occurs when the stylesheet is loaded
     * @since 21
     */
    public static Stylesheet load(URI uri) throws IOException {
        Objects.requireNonNull("uri", "uri cannot be null");
        return loadURI(uri.normalize(), null);
    }

    /**
     * Loads a stylesheet from the given URI, using a method that can find stylesheet resources.
     *
     * @param uri the URI of the requested stylesheet
     * @param resourceLoader a function that can find the stylesheet resource; usually this is a reference
     *                       to the {@link Class#getResource(String)} method of the calling class or the
     *                       {@link ClassLoader#getResource(String)} method of a class loader
     * @return the stylesheet
     * @throws NullPointerException if {@code name} is null
     * @throws FileNotFoundException if the stylesheet cannot be found or is inaccessible
     * @throws IOException if an I/O exception occurs when the stylesheet is loaded
     * @since 21
     */
    public static Stylesheet load(URI uri, Function<String, URL> resourceLoader) throws IOException {
        Objects.requireNonNull("uri", "uri cannot be null");
        Objects.requireNonNull("resourceLoader", "resourceLoader cannot be null");
        return loadURI(uri.normalize(), resourceLoader);
    }

    /**
     * Loads a stylesheet from the given URI.
     * In contrast to {@link #load(URI, Function)}, this method doesn't throw exceptions.
     *
     * @param uri the URI of the requested stylesheet
     * @param resourceLoader a function that can find the stylesheet resource; usually this is a reference
     *                       to the {@link Class#getResource(String)} method of the calling class or the
     *                       {@link ClassLoader#getResource(String)} method of a class loader
     * @return the stylesheet, or {@code null} if an error occurred
     */
    private static Stylesheet tryLoad(URI uri, Function<String, URL> resourceLoader) {
        try {
            return loadURI(uri.normalize(), resourceLoader);
        } catch (Exception ex) {
            // For data URIs, use the pretty-printed version for logging
            var dataUri = DataURI.tryParse(uri.toString());
            String stylesheetName = dataUri != null ? dataUri.toString() : uri.toString();
            String message = String.format("Failed to load stylesheet %s: %s", stylesheetName, ex.getMessage());

            ObservableList<CssParser.ParseError> errors = StyleManager.getErrors();
            if (errors != null) {
                errors.add(new CssParser.ParseError(message));
            }

            if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                LOGGER.warning(message);
            }

            return null;
        }
    }

    private static synchronized Stylesheet loadURI(URI uri, Function<String, URL> resourceLoader) throws IOException {
        if ("data".equalsIgnoreCase(uri.getScheme())) {
            DataURI dataUri = DataURI.tryParse(uri.toString());
            return loadDataURI(dataUri, resourceLoader);
        }

        @SuppressWarnings("removal")
        boolean allowBss = AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> {
            return Boolean.valueOf(System.getProperty("binary.css"));
        });

        Stylesheet stylesheet = allowBss ? tryLoadBinaryURI(uri, resourceLoader) : null;
        if (stylesheet != null) {
            return stylesheet;
        }

        URL cssUrl = findURL(uri, resourceLoader);
        if (cssUrl == null) {
            throw new FileNotFoundException("Stylesheet not found or inaccessible to caller module: " + uri);
        }

        stylesheet = new CssParser(resourceLoader).parse(cssUrl);
        stylesheet.initURI(uri);

        loadFonts(stylesheet);

        return stylesheet;
    }

    private static Stylesheet loadDataURI(DataURI dataUri, Function<String, URL> resourceLoader) throws IOException {
        boolean isText =
            "text".equalsIgnoreCase(dataUri.getMimeType())
                && ("css".equalsIgnoreCase(dataUri.getMimeSubtype())
                || "plain".equalsIgnoreCase(dataUri.getMimeSubtype()));

        if (isText) {
            String charsetName = dataUri.getParameters().get("charset");
            Charset charset;

            try {
                charset = charsetName != null ? Charset.forName(charsetName) : Charset.defaultCharset();
            } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
                throw new UnsupportedCharsetException(
                    String.format("Unsupported charset \"%s\" in stylesheet URI \"%s\"", charsetName, dataUri));
            }

            String stylesheetText = new String(dataUri.getData(), charset);
            Stylesheet stylesheet = new CssParser(resourceLoader).parse(stylesheetText);
            loadFonts(stylesheet);
            return stylesheet;
        }

        boolean isBinary =
            "application".equalsIgnoreCase(dataUri.getMimeType())
                && "octet-stream".equalsIgnoreCase(dataUri.getMimeSubtype());

        if (isBinary) {
            try (InputStream stream = new ByteArrayInputStream(dataUri.getData())) {
                Stylesheet stylesheet = Stylesheet.loadBinary(stream);
                loadFonts(stylesheet);
                return stylesheet;
            }
        }

        throw new IllegalArgumentException(
            String.format("Unexpected MIME type \"%s/%s\" in stylesheet URI \"%s\"",
                          dataUri.getMimeType(), dataUri.getMimeSubtype(), dataUri));
    }

    /**
     * Given an URI that points to a CSS file, this method looks for a BSS file with the
     * same name, and tries to load the BSS file instead.
     *
     * @return a stylesheet that was loaded from a BSS file, or {@code null}
     */
    private static Stylesheet tryLoadBinaryURI(URI uri, Function<String, URL> resourceLoader) throws IOException {
        String fileName = FILENAME_EXTENSION_PATTERN.matcher(uri.getPath()).replaceAll("");
        String extension = uri.getPath().substring(fileName.length());
        URI bssUri;

        try {
            bssUri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                    fileName + ".bss", uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }

        if (".bss".equalsIgnoreCase(extension)) {
            URL bssUrl = findURL(bssUri, resourceLoader);
            if (bssUrl == null) {
                throw new FileNotFoundException(
                    "Stylesheet not found or inaccessible to caller module: " + bssUri);
            }

            try (InputStream stream = bssUrl.openStream()) {
                return loadBinary(stream, bssUrl.toURI());
            } catch (URISyntaxException ignored) {
                return null;
            }
        }

        URL bssUrl = findURL(bssUri, resourceLoader);
        if (bssUrl != null) {
            try (InputStream stream = bssUrl.openStream()) {
                return loadBinary(stream, bssUrl.toURI());
            } catch (IOException | URISyntaxException ignored) {
                // If loadBinary throws an IOException, we return null to give the caller
                // the chance to try loading the .CSS file instead.
            }
        }

        return null;
    }

    /**
     * Converts the specified URI to an URL.
     * If the URL has no scheme component, this method tries to find the path using the specified
     * function or the calling thread's context class loader (in this order).
     */
    private static URL findURL(URI uri, Function<String, URL> resourceLoader) throws IOException {
        if (uri.isAbsolute()) {
            return uri.toURL();
        }

        String name = uri.toString();
        String cleanName = name.startsWith("/") ? name.substring(1) : name;

        if (resourceLoader != null) {
            URL url = resourceLoader.apply(cleanName);
            if (url != null) {
                return url;
            }
        }

        return Thread.currentThread().getContextClassLoader().getResource(cleanName);
    }

    /**
     * Loads all fonts that are referenced by the specified stylesheet.
     */
    private static void loadFonts(Stylesheet stylesheet) {
        faceLoop: for (FontFace fontFace : stylesheet.getFontFaces()) {
            if (fontFace instanceof FontFaceImpl fontFaceImpl) {
                for (FontFaceImpl.FontFaceSrc src : fontFaceImpl.getSources()) {
                    if (src.getType() == FontFaceImpl.FontFaceSrcType.URL) {
                        Font loadedFont = Font.loadFont(src.getSrc(), 10);
                        if (loadedFont == null) {
                            LOGGER.info("Could not load @font-face font [" + src.getSrc() + "]");
                        }

                        continue faceLoop;
                    }
                }
            }
        }
    }

    /**
     * Loads a binary stylesheet from a {@code URL}.
     *
     * @param url the {@code URL} from which the {@code Stylesheet} will be loaded
     * @return the loaded {@code Stylesheet}
     * @throws IOException if the binary stream corresponds to a more recent binary
     * css version or if an I/O error occurs while reading from the stream
     */
    public static Stylesheet loadBinary(URL url) throws IOException {
        if (url == null) {
            return null;
        }

        try (InputStream stream = url.openStream()) {
            return loadBinary(stream, url.toURI());
        } catch (FileNotFoundException | URISyntaxException ex) {
            return null;
        }
    }

    /**
     * Loads a binary stylesheet from a stream.
     *
     * @param stream the input stream
     * @return the loaded {@code Stylesheet}
     * @throws IOException if the binary stream corresponds to a more recent binary
     * css version or if an I/O error occurs while reading from the stream
     *
     * @since 17
     */
    public static Stylesheet loadBinary(InputStream stream) throws IOException {
        return loadBinary(stream, null);
    }

    private static Stylesheet loadBinary(InputStream stream, URI uri) throws IOException {
        Stylesheet stylesheet = null;

        try (DataInputStream dataInputStream =
                     new DataInputStream(new BufferedInputStream(stream, 4 * 1024))) {

            // read file version
            final int bssVersion = dataInputStream.readShort();
            if (bssVersion > Stylesheet.BINARY_CSS_VERSION) {
                throw new IOException(
                    String.format("Wrong binary CSS version %s, expected version less than or equal to %s",
                        uri != null ? bssVersion + " in stylesheet \"" + uri + "\"" : bssVersion,
                        Stylesheet.BINARY_CSS_VERSION));
            }
            // read strings
            final String[] strings = StringStore.readBinary(dataInputStream);
            // read binary data
            stylesheet = new Stylesheet(uri);

            try {

                dataInputStream.mark(Integer.MAX_VALUE);
                stylesheet.readBinary(bssVersion, dataInputStream, strings);

            } catch (Exception e) {

                stylesheet = new Stylesheet(uri);

                dataInputStream.reset();

                if (bssVersion == 2) {
                    // RT-31022
                    stylesheet.readBinary(3, dataInputStream, strings);
                } else {
                    stylesheet.readBinary(Stylesheet.BINARY_CSS_VERSION, dataInputStream, strings);
                }
            }

        }

        // return stylesheet
        return stylesheet;
    }

    /**
     * Converts the css file referenced by {@code source} to binary format and writes it to {@code destination}.
     *
     * @param source the JavaFX compliant css file to convert
     * @param destination the file to which the binary formatted data is written
     * @throws IOException if the destination file can not be created or if an I/O error occurs
     * @throws IllegalArgumentException if either parameter is {@code null}, if {@code source} and
     * {@code destination} are the same, if {@code source} cannot be read, or if {@code destination}
     * cannot be written
     */
    public static void convertToBinary(File source, File destination) throws IOException {

        if (source == null || destination == null) {
            throw new IllegalArgumentException("parameters may not be null");
        }

        if (source.getAbsolutePath().equals(destination.getAbsolutePath())) {
            throw new IllegalArgumentException("source and destination may not be the same");
        }

        if (source.canRead() == false) {
            throw new IllegalArgumentException("cannot read source file");
        }

        if (destination.exists() ? (destination.canWrite() == false) : (destination.createNewFile() == false)) {
            throw new IllegalArgumentException("cannot write destination file");
        }

        URI sourceURI = source.toURI();
        Stylesheet stylesheet = new CssParser().parse(sourceURI.toURL());

        // first write all the css binary data into the buffer and collect strings on way
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        StringStore stringStore = new StringStore();
        stylesheet.writeBinary(dos, stringStore);
        dos.flush();
        dos.close();

        FileOutputStream fos = new FileOutputStream(destination);
        DataOutputStream os = new DataOutputStream(fos);

        // write file version
        os.writeShort(BINARY_CSS_VERSION);

        // write strings
        stringStore.writeBinary(os);

        // write binary css
        os.write(baos.toByteArray());
        os.flush();
        os.close();
    }

    // Add the rules from the other stylesheet to this one
    void importStylesheet(Stylesheet importedStylesheet) {
        if (importedStylesheet == null) return;

        List<Rule> rulesToImport = importedStylesheet.getRules();
        if (rulesToImport == null || rulesToImport.isEmpty()) return;

        List<Rule> importedRules = new ArrayList<>(rulesToImport.size());
        for (Rule rule : rulesToImport) {
            List<Selector> selectors = rule.getSelectors();
            List<Declaration> declarations = rule.getUnobservedDeclarationList();
            importedRules.add(new Rule(selectors, declarations));
        }

        rules.addAll(importedRules);
    }
}
