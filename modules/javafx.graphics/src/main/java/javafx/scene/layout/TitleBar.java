package javafx.scene.layout;

import com.sun.glass.ui.WindowControlsMetrics;
import com.sun.javafx.stage.StageHelper;
import com.sun.javafx.tk.quantum.WindowStage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Subscription;

public class TitleBar extends Region {

    public final ObservableList<Node> left = FXCollections.observableArrayList();
    public final ObservableList<Node> center = FXCollections.observableArrayList();
    public final ObservableList<Node> right = FXCollections.observableArrayList();
    private WindowControlsMetrics metrics;
    private Subscription subscription;

    public TitleBar() {
        parentProperty().subscribe(parent -> {
            if (subscription == null && parent != null) {
                subscription = sceneProperty()
                    .flatMap(Scene::windowProperty)
                    .flatMap(Window::showingProperty)
                    .subscribe(showing -> {
                        if (showing != Boolean.TRUE || !(getScene().getWindow() instanceof Stage stage)
                                || !(StageHelper.getPeer(stage) instanceof WindowStage windowStage)) {
                            return;
                        }

                        metrics = windowStage.getPlatformWindow().getWindowControlsMetrics();
                        requestLayout();
                    });
            }

            if (subscription != null && parent == null) {
                subscription.unsubscribe();
                subscription = null;
            }
        });
    }

    public ObservableList<Node> getLeft() {
        return left;
    }

    public ObservableList<Node> getCenter() {
        return center;
    }

    public ObservableList<Node> getRight() {
        return right;
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
