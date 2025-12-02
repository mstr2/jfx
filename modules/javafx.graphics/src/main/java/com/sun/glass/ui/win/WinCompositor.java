package com.sun.glass.ui.win;

public class WinCompositor {

    private long handle;

    public static boolean isCompositionAvailable() {
        return _isCompositionAvailable();
    }

    public WinCompositor() {
        this.handle = _createCompositor();
    }

    public void dispose() {
        if (handle != 0) {
            _releaseCompositor(handle);
            handle = 0;
        }
    }

    public void run(Runnable tick) {
        checkDisposed();
        _run(handle, tick);
    }

    public WinCompositionLayer createCompositionLayer(long hwnd) {
        checkDisposed();
        return new WinCompositionLayer(_createCompositionLayer(handle, hwnd));
    }

    private void checkDisposed() {
        if (handle == 0) {
            throw new RuntimeException("Object is disposed.");
        }
    }

    private static native boolean _isCompositionAvailable();
    private static native long _createCompositor();
    private static native void _releaseCompositor(long compositorHandle);
    private static native long _createCompositionLayer(long compositorHandle, long hwnd);
    private static native void _run(long compositorHandle, Runnable tick);
}
