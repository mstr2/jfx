package javafx.scene.shape;

import com.sun.javafx.collections.FloatArraySyncer;
import com.sun.javafx.collections.IntegerArraySyncer;
import com.sun.javafx.geom.BaseBounds;
import com.sun.javafx.geom.BoxBounds;
import com.sun.javafx.sg.prism.NGTriangleMesh;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class VertexMesh<T extends Vertex> extends Mesh {

    private final ObservableList<T> vertices = FXCollections.observableArrayList();
    private final ObservableList<Integer> indices = FXCollections.observableArrayList();
    private final boolean hasPositions;
    private final boolean hasNormals;
    private final boolean hasTexCoords;
    private BaseBounds cachedBounds;
    private NGTriangleMesh peer;

    public VertexMesh(Class<T> vertexType) {
        hasPositions = VertexPosition.class.isAssignableFrom(vertexType);
        hasNormals = VertexNormal.class.isAssignableFrom(vertexType);
        hasTexCoords = VertexTexCoord.class.isAssignableFrom(vertexType);
    }

    public final ObservableList<T> getVertices() {
        return vertices;
    }

    public final ObservableList<Integer> getIndices() {
        return indices;
    }

    @Override
    NGTriangleMesh getPGMesh() {
        if (peer == null) {
            peer = new NGTriangleMesh();
        }

        return peer;
    }

    @Override
    void updatePG() {
        if (!isDirty()) {
            return;
        }

        NGTriangleMesh mesh = getPGMesh();

        assert hasPositions;
        assert hasTexCoords;
        assert !hasNormals;

        mesh.syncPoints(new FloatSyncer<>(vertices, 3, new VertexSerializer<T>() {
            @Override
            public void serialize(T vertex, float[] buffer, int index) {
                ((VertexPosition)vertex).serializePosition(buffer, index);
            }
        }));

        mesh.syncTexCoords(new FloatSyncer<>(vertices, 2, new VertexSerializer<T>() {
            @Override
            public void serialize(T vertex, float[] buffer, int index) {
                ((VertexTexCoord)vertex).serializeTexCoord(buffer, index);
            }
        }));

        mesh.syncFaces(new IntegerArraySyncer() {
            @Override
            public int[] syncTo(int[] array, int[] fromAndLengthIndices) {
                int length = indices.size() * 2;

                if (array == null || array.length != length) {
                    fromAndLengthIndices[0] = 0;
                    fromAndLengthIndices[1] = length;
                    array = new int[length];
                }

                for (int i = 0, max = indices.size(); i < max; ++i) {
                    int index = indices.get(i);
                    array[i * 2] = index;
                    array[i * 2 + 1] = index;
                }

                return array;
            }
        });

        mesh.syncFaceSmoothingGroups(new IntegerArraySyncer() {
            @Override
            public int[] syncTo(int[] array, int[] fromAndLengthIndices) {
                return new int[0];
            }
        });
    }

    @Override
    BaseBounds computeBounds(BaseBounds bounds) {
        if (isDirty() || cachedBounds == null) {
            cachedBounds = new BoxBounds();
            float[] position = new float[3];

            if (hasPositions) {
                for (int i = 0, max = vertices.size(); i < max; ++i) {
                    ((VertexPosition)vertices.get(i)).serializePosition(position, 0);
                    cachedBounds.add(position[0], position[1], position[2]);
                }
            }
        }

        return bounds.deriveWithNewBounds(cachedBounds);
    }

    interface VertexPosition {
        void serializePosition(float[] buffer, int index);
    }

    interface VertexTexCoord {
        void serializeTexCoord(float[] buffer, int index);
    }

    interface VertexNormal {
        void serializeNormal(float[] buffer, int index);
    }

    private interface VertexSerializer<T> {
        void serialize(T vertex, float[] buffer, int index);
    }

    private record FloatSyncer<T>(ObservableList<T> vertices, int componentSize, VertexSerializer<T> serializer)
            implements FloatArraySyncer {
        @Override
        public float[] syncTo(float[] array, int[] fromAndLengthIndices) {
            if (array == null || array.length != vertices.size()) {
                fromAndLengthIndices[0] = 0;
                fromAndLengthIndices[1] = vertices.size();
                array = new float[vertices.size() * componentSize];
            }

            for (int i = 0, max = vertices.size(); i < max; ++i) {
                serializer.serialize(vertices.get(i), array, i * componentSize);
            }

            return array;
        }
    }
}
