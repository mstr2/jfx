package com.sun.glass.ui.win;

public class WinCompositionLayer {

    private long handle;

    public WinCompositionLayer(long handle) {
        this.handle = handle;
    }

    public void dispose() {
        if (handle != 0) {
            _release(handle);
            handle = 0;
        }
    }

    public void setBlurBehind(boolean enabled) {
        checkDisposed();
        _setBlurBehind(handle, enabled);
    }

    public void present(long compositionDevice, long textureSharedHandle, int width, int height) {
        checkDisposed();
        _present(handle, compositionDevice, textureSharedHandle, width, height);
    }

    private void checkDisposed() {
        if (handle == 0) {
            throw new RuntimeException("Object is disposed.");
        }
    }

    private static native void _release(long handle);
    private static native void _present(long handle, long compositionDevice, long textureSharedHandle, int width, int height);
    private static native void _setBlurBehind(long handle, boolean enabled);
}
