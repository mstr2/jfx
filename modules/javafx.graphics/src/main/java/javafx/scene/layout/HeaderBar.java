package javafx.scene.layout;

import com.sun.glass.ui.WindowOverlayMetrics;
import com.sun.javafx.stage.StageHelper;
import com.sun.javafx.tk.quantum.WindowStage;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Dimension2D;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Subscription;
import java.util.function.Consumer;

public class HeaderBar extends Region {

    private final Consumer<WindowOverlayMetrics> metricsListener = this::onMetricsChanged;
    private Subscription showingSubscription, metricsSubscription;

    public HeaderBar() {
        parentProperty().subscribe(parent -> {
            if (showingSubscription == null && parent != null) {
                showingSubscription = sceneProperty()
                    .flatMap(Scene::windowProperty)
                    .flatMap(Window::showingProperty)
                    .orElse(Boolean.FALSE)
                    .subscribe(this::onShowingChanged);
            }

            if (parent == null) {
                if (showingSubscription != null) {
                    showingSubscription.unsubscribe();
                    showingSubscription = null;
                }

                if (metricsSubscription != null) {
                    metricsSubscription.unsubscribe();
                    metricsSubscription = null;
                }
            }
        });
    }

    private void onShowingChanged(boolean showing) {
        if (!showing) {
            if (metricsSubscription != null) {
                metricsSubscription.unsubscribe();
                metricsSubscription = null;
            }
        } else if (getScene().getWindow() instanceof Stage stage
                   && StageHelper.getPeer(stage) instanceof WindowStage windowStage) {
            ObservableValue<WindowOverlayMetrics> metrics =
                    windowStage.getPlatformWindow().windowOverlayMetrics();

            if (metrics != null) {
                metricsSubscription = metrics.subscribe(metricsListener);
            }

            requestLayout();
        }
    }

    private void onMetricsChanged(WindowOverlayMetrics metrics) {
        if (metrics.alignment() == HPos.LEFT) {
            leftInset.set(metrics.size());
            rightInset.set(new Dimension2D(0, 0));
        }

        if (metrics.alignment() == HPos.RIGHT) {
            leftInset.set(new Dimension2D(0, 0));
            rightInset.set(metrics.size());
        }
    }

    private final ReadOnlyObjectWrapper<Dimension2D> leftInset =
            new ReadOnlyObjectWrapper<>(this, "leftInset", new Dimension2D(0, 0));

    public final ReadOnlyObjectWrapper<Dimension2D> leftInsetProperty() {
        return leftInset;
    }

    public final Dimension2D getLeftInset() {
        return leftInset.get();
    }

    private final ReadOnlyObjectWrapper<Dimension2D> rightInset =
            new ReadOnlyObjectWrapper<>(this, "rightInset", new Dimension2D(0, 0));

    public final ReadOnlyObjectWrapper<Dimension2D> rightInsetProperty() {
        return rightInset;
    }

    public final Dimension2D getRightInset() {
        return rightInset.get();
    }

    private final ObjectProperty<Node> left = new SimpleObjectProperty<>(this, "left");

    public final ObjectProperty<Node> leftProperty() {
        return left;
    }

    public final Node getLeft() {
        return left.get();
    }

    public final void setLeft(Node value) {
        left.set(value);
    }

    private final ObjectProperty<Node> center = new SimpleObjectProperty<>(this, "center");

    public final ObjectProperty<Node> centerProperty() {
        return center;
    }

    public final Node getCenter() {
        return center.get();
    }

    public final void setCenter(Node value) {
        center.set(value);
    }

    private final ObjectProperty<Node> right = new SimpleObjectProperty<>(this, "right");

    public final ObjectProperty<Node> rightProperty() {
        return right;
    }

    public final Node getRight() {
        return right.get();
    }

    public final void setRight(Node value) {
        right.set(value);
    }

    @Override
    protected double computePrefWidth(double height) {
        return super.computePrefWidth(height);
    }

    @Override
    protected double computePrefHeight(double width) {
        return super.computePrefHeight(width);
    }

    @Override
    protected double computeMinWidth(double height) {
        return super.computeMinWidth(height);
    }

    @Override
    protected double computeMinHeight(double width) {
        return super.computeMinHeight(width);
    }

    @Override
    protected double computeMaxWidth(double height) {
        return super.computeMaxWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width) {
        return super.computeMaxHeight(width);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
    }
}
