package com.sun.glass.ui.gtk;

import com.sun.glass.ui.NonClientTheme;
import com.sun.javafx.application.PlatformImpl;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.MapChangeListener;

final class NonClientThemeChooser {

    private static final String THEME_NAME_KEY = "GTK.theme_name";

    private final ReadOnlyObjectWrapper<NonClientTheme> nonClientTheme =
            new ReadOnlyObjectWrapper<>(this, "nonClientTheme");

    private NonClientThemeChooser() {
        PlatformImpl.getPlatformPreferences().addListener((MapChangeListener<String, Object>) change -> {
            if (THEME_NAME_KEY.equals(change.getKey())) {
                updateThemeStylesheets();
            }
        });

        updateThemeStylesheets();
    }

    public static NonClientThemeChooser getInstance() {
        class Holder {
            static final NonClientThemeChooser instance = new NonClientThemeChooser();
        }

        return Holder.instance;
    }

    public ReadOnlyObjectProperty<NonClientTheme> nonClientThemeProperty() {
        return nonClientTheme.getReadOnlyProperty();
    }

    private void updateThemeStylesheets() {
        var controlsTheme = WindowControlsTheme.findBestFit();
        nonClientTheme.set(controlsTheme.getNonClientTheme());
    }
}
