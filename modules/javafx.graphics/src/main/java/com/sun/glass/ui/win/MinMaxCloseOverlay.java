package com.sun.glass.ui.win;

import javafx.css.PseudoClass;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class MinMaxCloseOverlay extends AnchorPane {

    private static final double BUTTON_WIDTH = 46;
    private static final double BUTTON_HEIGHT = 29;
    private static final String MINIMIZE_GLYPH = "\uE921";
    private static final String MAXIMIZE_GLYPH = "\uE922";
    private static final String RESTORE_GLYPH = "\uE923";
    private static final String CLOSE_GLYPH = "\uE8BB";
    private static final Font FONT = Font.font("Segoe MDL2 Assets", 9);

    private final Rectangle minimize, maximize, close;

    public MinMaxCloseOverlay() {
        getStylesheets().add("data:text/css;base64," + Base64.getEncoder().encodeToString("""
            #close, #minimize, #maximize {
                -fx-fill: transparent;
                transition: -fx-fill 0.25s;
            }

            #minimize:hover, #maximize:hover {
                -fx-fill: gray;
                transition: -fx-fill 0s;
            }

            #close:hover {
                -fx-fill: #c42b1c;
                transition: -fx-fill 0s;
            }
            """.getBytes(StandardCharsets.UTF_8)));

        minimize = new Rectangle(BUTTON_WIDTH, BUTTON_HEIGHT);
        minimize.setId("minimize");
        setRightAnchor(minimize, BUTTON_WIDTH * 2.0);

        maximize = new Rectangle(BUTTON_WIDTH, BUTTON_HEIGHT);
        maximize.setId("maximize");
        setRightAnchor(maximize, BUTTON_WIDTH);

        close = new Rectangle(BUTTON_WIDTH, BUTTON_HEIGHT);
        close.setId("close");
        setRightAnchor(close, 0.0);

        var minimizeGlyph = new Text(0, 20, MINIMIZE_GLYPH);
        minimizeGlyph.setFont(FONT);
        setRightAnchor(minimizeGlyph, BUTTON_WIDTH * 2 + BUTTON_WIDTH / 2 - 5);

        var maximizeGlyph = new Text(0, 20, MAXIMIZE_GLYPH);
        maximizeGlyph.setFont(FONT);
        setRightAnchor(maximizeGlyph, BUTTON_WIDTH + BUTTON_WIDTH / 2 - 5);

        var closeGlyph = new Text(0, 20, CLOSE_GLYPH);
        closeGlyph.setFont(FONT);
        setRightAnchor(closeGlyph, BUTTON_WIDTH / 2 - 5);

        getChildren().addAll(minimize, maximize, close, minimizeGlyph, maximizeGlyph, closeGlyph);
    }

    public HitTestResult hitTest(double x, double y) {
        if (minimize.getBoundsInParent().contains(x, y)) {
            close.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), false);
            return HitTestResult.MIN_BUTTON;
        }

        if (maximize.getBoundsInParent().contains(x, y)) {
            close.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), false);
            return HitTestResult.MAX_BUTTON;
        }

        if (close.getBoundsInParent().contains(x, y)) {
            close.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
            return HitTestResult.CLOSE_BUTTON;
        }

        close.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), false);
        return HitTestResult.CLIENT;
    }
}
