#include "CompositionLayer.h"
#include "common.h"
#include "GlassApplication.h"
#include <cstdio>

#include "com_sun_glass_ui_win_WinCompositionLayer.h"

using namespace Microsoft::WRL;
using namespace ABI::Windows::UI::Composition;
using namespace ABI::Windows::UI::Composition::Desktop;
using namespace ABI::Windows::Graphics::DirectX;

/*
 * Class:     com_sun_glass_ui_win_WinCompositionLayer
 * Method:    _release
 */
JNIEXPORT void JNICALL Java_com_sun_glass_ui_win_WinCompositionLayer__1release
  (JNIEnv *env, jclass, jlong handle)
{
    CompositionLayer* compositionLayer = (CompositionLayer*)handle;
    compositionLayer->Release();
}

/*
 * Class:     com_sun_glass_ui_win_WinCompositionLayer
 * Method:    _initialize
 */
JNIEXPORT void JNICALL Java_com_sun_glass_ui_win_WinCompositionLayer__1present
  (JNIEnv *env, jclass, jlong handle, jlong d3d11Device, jlong textureSharedHandle, jint width, jint height)
{
    try {
        ((CompositionLayer*)handle)->present((ID3D11Device*)d3d11Device, (HANDLE)textureSharedHandle, width, height);
    } catch (RoException const& ex) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), ex.message());
    }
}

/*
 * Class:     com_sun_glass_ui_win_WinCompositionLayer
 * Method:    _setBlurBehind
 */
JNIEXPORT void JNICALL Java_com_sun_glass_ui_win_WinCompositionLayer__1setBlurBehind
  (JNIEnv *env, jclass, jlong handle, jboolean enabled)
{
    CompositionLayer* compositionLayer = (CompositionLayer*)handle;
    compositionLayer->setBlurBehind(enabled);
}

CompositionLayer::CompositionLayer(
    ComPtr<ICompositor> compositor,
    ComPtr<IDesktopWindowTarget> desktopWindowTarget,
    ComPtr<ICompositionTarget> compositionTarget,
    ComPtr<ICompositionBrush> backgroundBrush,
    ComPtr<ISpriteVisual> backgroundVisual,
    ComPtr<ISpriteVisual> contentVisual) :
        blurBehind_(false),
        width_(0),
        height_(0),
        device_(nullptr),
        compositor_(compositor),
        desktopWindowTarget_(desktopWindowTarget),
        compositionTarget_(compositionTarget),
        backgroundBrush_(backgroundBrush),
        backgroundVisual_(backgroundVisual),
        contentVisual_(contentVisual) {}

void CompositionLayer::setBlurBehind(bool enabled) {
    if (enabled && !blurBehind_) {
        backgroundVisual_->put_Brush(backgroundBrush_.Get());
    } else if (!enabled && blurBehind_) {
        ComPtr<ICompositionColorBrush> defaultBrush;
        RO_CHECKED("ICompositor::CreateColorBrushWithColor",
                   compositor_->CreateColorBrushWithColor({255, 255, 255, 255}, &defaultBrush));

        ComPtr<ICompositionBrush> brush;
        RO_CHECKED("ICompositionColorBrush::QueryInterface<ICompositionBrush>",
                   defaultBrush->QueryInterface<ICompositionBrush>(&brush));

        backgroundVisual_->put_Brush(brush.Get());
    }

    blurBehind_ = enabled;
}

void CompositionLayer::ensureDevice(ID3D11Device* device) {
    if (device_.Get() == device) {
        return;
    }

    device_ = device;
    device_->GetImmediateContext(&deviceContext_);

    ComPtr<ICompositorInterop> compositorInterop;
    RO_CHECKED("ICompositor::QueryInterface<ICompositorInterop>",
               compositor_->QueryInterface<ICompositorInterop>(&compositorInterop));

    ComPtr<ICompositorDesktopInterop> compositorDesktopInterop;
    RO_CHECKED("ICompositor::QueryInterface<ICompositorDesktopInterop>",
               compositor_->QueryInterface<ICompositorDesktopInterop>(&compositorDesktopInterop));

    ComPtr<ICompositionGraphicsDevice> compositionGraphicsDevice;
    RO_CHECKED("ICompositorInterop::CreateGraphicsDevice",
               compositorInterop->CreateGraphicsDevice(device, &compositionGraphicsDevice));

    ComPtr<ICompositionDrawingSurface> drawingSurface;
    RO_CHECKED("ICompositionGraphicsDevice::CreateDrawingSurface",
               compositionGraphicsDevice->CreateDrawingSurface(
                   {0, 0},
                   DirectXPixelFormat::DirectXPixelFormat_B8G8R8A8UIntNormalized,
                   DirectXAlphaMode::DirectXAlphaMode_Premultiplied,
                   &drawingSurface));

    ComPtr<ICompositionSurface> drawingSurface_ICompositionSurface;
    RO_CHECKED("ICompositionDrawingSurface::QueryInterface<ICompositionSurface>",
               drawingSurface->QueryInterface<ICompositionSurface>(&drawingSurface_ICompositionSurface));

    RO_CHECKED("ICompositionDrawingSurface::QueryInterface<ICompositionDrawingSurfaceInterop>",
               drawingSurface->QueryInterface<ICompositionDrawingSurfaceInterop>(&drawingSurfaceInterop_));

    ComPtr<ICompositionSurfaceBrush> contentBrush;
    RO_CHECKED("ICompositor::CreateSurfaceBrushWithSurface",
               compositor_->CreateSurfaceBrushWithSurface(drawingSurface_ICompositionSurface.Get(), &contentBrush));

    ComPtr<ICompositionBrush> contentBrush_ICompositionBrush;
    RO_CHECKED("ICompositionSurfaceBrush::QueryInterface<ICompositionBrush>",
               contentBrush->QueryInterface<ICompositionBrush>(&contentBrush_ICompositionBrush));

    contentVisual_->put_Brush(contentBrush_ICompositionBrush.Get());
}

void CompositionLayer::present(ID3D11Device* device, HANDLE textureSharedHandle, int width, int height) {
    ensureDevice(device);
    ID3D11Texture2D* texture = nullptr;

    if (width_ != width || height_ != height) {
        width_ = width;
        height_ = height;

        ComPtr<IVisual> visual;
        RO_CHECKED("ISpriteVisual::QueryInterface<IVisual>",
                   contentVisual_->QueryInterface<IVisual>(&visual));
        visual->put_Size({(float)width, (float)height});

        RO_CHECKED("ICompositionDrawingSurfaceInterop::Resize",
                   drawingSurfaceInterop_->Resize({width, height}));
    }

    POINT offset;
    ComPtr<ID3D11Texture2D> drawingSurfaceTexture;
    RO_CHECKED("ICompositionDrawingSurfaceInterop::BeginDraw",
               drawingSurfaceInterop_->BeginDraw(nullptr, __uuidof(ID3D11Texture2D), (void**)&drawingSurfaceTexture, &offset));

    ComPtr<ID3D11Resource> sourceTextureResource;
    RO_CHECKED("ID3D11Device::OpenSharedResource",
               device->OpenSharedResource(textureSharedHandle, __uuidof(ID3D11Resource), (void**)&sourceTextureResource));

    ComPtr<ID3D11Texture2D> sourceTexture;
    RO_CHECKED("ID3D11Resource::QueryInterface<ID3D11Texture2D>",
               sourceTextureResource->QueryInterface(__uuidof(ID3D11Texture2D), (void**)&sourceTexture));

    deviceContext_->CopySubresourceRegion(
        drawingSurfaceTexture.Get(), 0, offset.x, offset.y, 0, sourceTexture.Get(), 0, nullptr);

    RO_CHECKED("ICompositionDrawingSurfaceInterop::EndDraw",
               drawingSurfaceInterop_->EndDraw());
}
