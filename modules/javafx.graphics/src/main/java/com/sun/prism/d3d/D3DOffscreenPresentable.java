package com.sun.prism.d3d;

import com.sun.glass.ui.Screen;
import com.sun.glass.ui.win.WinCompositionLayer;
import com.sun.javafx.geom.Rectangle;
import com.sun.prism.CompositeMode;
import com.sun.prism.Graphics;
import com.sun.prism.Presentable;
import com.sun.prism.PresentableState;

class D3DOffscreenPresentable extends D3DResource implements D3DRenderTarget, Presentable, D3DContextSource {

    private final D3DRTTexture texBackBuffer;
    private final float pixelScaleFactorX;
    private final float pixelScaleFactorY;
    private final WinCompositionLayer compositionLayer;
    private final long compositionDevice;
    private D3DGraphics graphics;

    D3DOffscreenPresentable(
            long compositionDevice, WinCompositionLayer compositionLayer, D3DContext context,
            D3DRTTexture rtt, float pixelScaleX, float pixelScaleY) {
        super(new D3DRecord(context, 0));
        this.texBackBuffer = rtt;
        this.pixelScaleFactorX = pixelScaleX;
        this.pixelScaleFactorY = pixelScaleY;
        this.compositionLayer = compositionLayer;
        this.compositionDevice = compositionDevice;
    }

    @Override
    public boolean prepare(Rectangle dirtyregion) {
        D3DContext context = getContext();

        context.flushVertexBuffer();
        D3DGraphics g = D3DGraphics.create(texBackBuffer, context);
        if (g == null) {
            return false;
        }
        int sw = texBackBuffer.getContentWidth();
        int sh = texBackBuffer.getContentHeight();
        int dw = this.getContentWidth();
        int dh = this.getContentHeight();
        if (isMSAA()) {
            context.flushVertexBuffer();
            g.blit(texBackBuffer, null, 0, 0, sw, sh, 0, 0, dw, dh);
        } else {
            g.setCompositeMode(CompositeMode.SRC);
            g.drawTexture(texBackBuffer, 0, 0, dw, dh, 0, 0, sw, sh);
        }
        context.flushVertexBuffer();
        texBackBuffer.unlock();
        return true;
    }

    @Override
    public void dispose() {
        texBackBuffer.dispose();
        super.dispose();
    }

    @Override
    public boolean present() {
        D3DContext context = getContext();
        int res = nPresent(context.getContextHandle());
        boolean valid = context.validatePresent(res);

        if (valid) {
            compositionLayer.setBlurBehind(graphics.hasBackgroundAcrylic());
            compositionLayer.present(
                compositionDevice, texBackBuffer.getSharedHandle(),
                texBackBuffer.getContentWidth(), texBackBuffer.getContentHeight());
        }

        return valid;
    }

    @Override
    public long getResourceHandle() {
        return d3dResRecord.getResource();
    }

    @Override
    public int getPhysicalWidth() {
        return D3DResourceFactory.nGetTextureWidth(d3dResRecord.getResource());
    }

    @Override
    public int getPhysicalHeight() {
        return D3DResourceFactory.nGetTextureHeight(d3dResRecord.getResource());
    }

    @Override
    public int getContentWidth() {
        return getPhysicalWidth();
    }

    @Override
    public int getContentHeight() {
        return getPhysicalHeight();
    }

    @Override
    public int getContentX() {
        return 0;
    }

    @Override
    public int getContentY() {
        return 0;
    }

    @Override
    public D3DContext getContext() {
        return d3dResRecord.getContext();
    }

    @Override
    public boolean lockResources(PresentableState pState) {
        if (pState.getRenderWidth() != texBackBuffer.getContentWidth() ||
            pState.getRenderHeight() != texBackBuffer.getContentHeight() ||
            pState.getRenderScaleX() != pixelScaleFactorX ||
            pState.getRenderScaleY() != pixelScaleFactorY)
        {
            return true;
        }
        texBackBuffer.lock();
        return texBackBuffer.isSurfaceLost();
    }

    @Override
    public Graphics createGraphics() {
        graphics = D3DGraphics.create(texBackBuffer, getContext());
        graphics.scale(pixelScaleFactorX, pixelScaleFactorY);
        return graphics;
    }

    @Override
    public Screen getAssociatedScreen() {
        return getContext().getAssociatedScreen();
    }

    @Override
    public float getPixelScaleFactorX() {
        return pixelScaleFactorX;
    }

    @Override
    public float getPixelScaleFactorY() {
        return pixelScaleFactorY;
    }

    @Override
    public boolean isOpaque() {
        return texBackBuffer.isOpaque();
    }

    @Override
    public void setOpaque(boolean opaque) {
        texBackBuffer.setOpaque(opaque);
    }

    @Override
    public boolean isMSAA() {
        return texBackBuffer != null ? texBackBuffer.isMSAA() : false;
    }

    private static native int nPresent(long pContext);

}
