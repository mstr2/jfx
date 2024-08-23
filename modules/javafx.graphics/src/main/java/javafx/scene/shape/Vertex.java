package javafx.scene.shape;

import javafx.geometry.Point2D;
import javafx.geometry.Point3D;

public sealed interface Vertex permits Vertex.PositionTexCoord {

    record PositionTexCoord(Point3D position, Point2D texCoord)
            implements Vertex, VertexMesh.VertexPosition, VertexMesh.VertexTexCoord {

        @Override
        public void serializePosition(float[] buffer, int index) {
            buffer[index] = (float)position.getX();
            buffer[index + 1] = (float)position.getY();
            buffer[index + 2] = (float)position.getZ();
        }

        @Override
        public void serializeTexCoord(float[] buffer, int index) {
            buffer[index] = (float)texCoord.getX();
            buffer[index + 1] = (float)texCoord.getY();
        }
    }
}
