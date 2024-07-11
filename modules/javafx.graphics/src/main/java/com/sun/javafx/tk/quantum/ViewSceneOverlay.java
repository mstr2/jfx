package com.sun.javafx.tk.quantum;

import com.sun.javafx.scene.NodeHelper;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SubScene;

final class ViewSceneOverlay {

    private final javafx.scene.Scene fxScene;
    private final ViewPainter painter;
    private Parent root;
    private double width, height;

    ViewSceneOverlay(javafx.scene.Scene fxScene, ViewPainter painter) {
        this.fxScene = fxScene;
        this.painter = painter;
    }

    public void processCSS() {
        if (root != null) {
            NodeHelper.processCSS(root);
        }
    }

    public void resize(double width, double height) {
        this.width = width;
        this.height = height;
    }

    public void layout() {
        if (fxScene == null) {
            return;
        }

        var window = fxScene.getWindow();

        if (root != null && window != null) {
            root.resize(width, height);
            root.layout();
            NodeHelper.updateBounds(root);
        }
    }

    public void setRoot(Parent root) {
        if (this.root == root) {
            return;
        }

        this.root = root;

        if (root != null) {
            NodeHelper.setScenes(root, fxScene, null);
            painter.setOverlayRoot(NodeHelper.getPeer(root));
        } else {
            painter.setOverlayRoot(null);
        }
    }

    public void synchronize() {
        if (root != null && !NodeHelper.isDirtyEmpty(root)) {
            syncPeer(root);
        }
    }

    private void syncPeer(Node node) {
        NodeHelper.syncPeer(node);

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                syncPeer(child);
            }
        } else if (node instanceof SubScene subScene) {
            syncPeer(subScene.getRoot());
        }

        if (node.getClip() != null) {
            syncPeer(node.getClip());
        }
    }

}
