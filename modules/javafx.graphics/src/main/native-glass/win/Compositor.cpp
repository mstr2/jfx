#include <windows.ui.composition.interop.h>
#include "common.h"
#include "GlassApplication.h"
#include "Compositor.h"
#include "GraphicsEffect.h"
#include <cstdio>

#include "com_sun_glass_ui_win_WinCompositor.h"

using namespace Microsoft::WRL;

/*
 * Class:     com_sun_glass_ui_win_WinCompositor
 * Method:    _createCompositor
 */
JNIEXPORT jlong JNICALL Java_com_sun_glass_ui_win_WinCompositor__1createCompositor
    (JNIEnv *env, jclass)
{
    try {
        HRESULT res = RoInitialize(RO_INIT_SINGLETHREADED);
        if (FAILED(res)) {
            throw RoException("RoInitialize failed: ", res);
        }

        ComPtr<Compositor> instance = Make<Compositor>();
        instance->AddRef();
        return jlong(instance.Get());
    } catch (RoException const& ex) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), ex.message());
    }

    return 0;
}

/*
 * Class:     com_sun_glass_ui_win_WinCompositor
 * Method:    _releaseCompositor
 */
JNIEXPORT void JNICALL Java_com_sun_glass_ui_win_WinCompositor__1releaseCompositor
    (JNIEnv *env, jclass, jlong handle)
{
    Compositor* compositor = (Compositor*)handle;
    compositor->shutdownRequested_ = true;
    compositor->Release();
    RoUninitialize();
}

/*
 * Class:     com_sun_glass_ui_win_WinCompositor
 * Method:    _createCompositionLayer
 */
JNIEXPORT jlong JNICALL Java_com_sun_glass_ui_win_WinCompositor__1createCompositionLayer
    (JNIEnv *env, jclass, jlong compositor, jlong hwnd)
{
    try {
        return jlong(((Compositor*)compositor)->createCompositionLayer(HWND(hwnd)));
    } catch (RoException const& ex) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), ex.message());
    }

    return 0;
}

/*
 * Class:     com_sun_glass_ui_win_WinCompositor
 * Method:    _run
 */
JNIEXPORT void JNICALL Java_com_sun_glass_ui_win_WinCompositor__1run
    (JNIEnv *env, jclass, jlong compositor, jobject onTick)
{
    jclass onTickClass = (jclass)env->GetObjectClass(onTick);
    jmethodID method = env->GetMethodID(onTickClass, "run", "()V");

    try {
        ((Compositor*)compositor)->run(env, onTick, method);
    } catch (RoException const& ex) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), ex.message());
    }
}

/*
 * Class:     com_sun_glass_ui_win_WinCompositor
 * Method:    _isCompositionAvailable
 */
JNIEXPORT jboolean JNICALL Java_com_sun_glass_ui_win_WinCompositor__1isCompositionAvailable
    (JNIEnv *env, jclass)
{
    return isCoreMessagingSupported();
}

namespace {
    class Handler : public RuntimeClass<
            RuntimeClassFlags<RuntimeClassType::WinRtClassicComMix>,
            ABI::Windows::Foundation::IAsyncActionCompletedHandler> {
    public:
        Handler(ComPtr<Compositor> compositor, JNIEnv* env, jobject onTick, jmethodID method) :
            compositor_(compositor), env_(env), onTick_(onTick), method_(method) {}

        virtual HRESULT Invoke(ABI::Windows::Foundation::IAsyncAction*, AsyncStatus) override {
            compositor_->tick(this, env_, onTick_, method_);
            return S_OK;
        }

    private:
        ComPtr<Compositor> compositor_;
        JNIEnv* env_;
        jobject onTick_;
        jmethodID method_;
    };
}

Compositor::Compositor() : shutdownRequested_(false) {
    using namespace ABI::Windows::System;
    using namespace ABI::Windows::UI::Composition;
    using namespace ABI::Windows::Graphics::Effects;

    DispatcherQueueOptions options {
        sizeof(DispatcherQueueOptions), /* dwSize */
        DQTYPE_THREAD_CURRENT,          /* threadType */
        DQTAT_COM_NONE                  /* apartmentType */
    };

    RO_CHECKED("CreateDispatcherQueueController",
               CreateDispatcherQueueController(options, &dispatcherQueueController_));

    RO_CHECKED("RoActivateInstance",
               RoActivateInstance(hstring("Windows.UI.Composition.Compositor"), (IInspectable**)&compositor_));

    ComPtr<ICompositionEffectSourceParameterFactory> factory;
    RO_CHECKED("RoGetActivationFactory",
               RoGetActivationFactory(
                   hstring("Windows.UI.Composition.CompositionEffectSourceParameter"),
                   __uuidof(ICompositionEffectSourceParameterFactory),
                   (void**)&factory));

    ComPtr<ICompositionEffectSourceParameter> parameter;
    RO_CHECKED("ICompositionEffectSourceParameterFactory::Create",
               factory->Create(hstring("backdrop"), &parameter));

    ComPtr<IGraphicsEffectSource> effectSource;
    RO_CHECKED("ICompositionEffectSourceParameter<IGraphicsEffectSource>",
               parameter->QueryInterface<IGraphicsEffectSource>(&effectSource));

    ComPtr<GaussianBlurEffect> blurEffect = Make<GaussianBlurEffect>(effectSource);

    ComPtr<ICompositionEffectFactory> blurEffectFactory;
    RO_CHECKED("ICompositor::CreateEffectFactory",
               compositor_->CreateEffectFactory(blurEffect.Get(), &blurEffectFactory));

    ComPtr<ICompositor2> compositor2;
    RO_CHECKED("ICompositor::QueryInterface<ICompositor2>",
               compositor_->QueryInterface<ICompositor2>(&compositor2));

    ComPtr<ICompositionBackdropBrush> backdropBrush;
    RO_CHECKED("ICompositor2::CreateBackdropBrush",
               compositor2->CreateBackdropBrush(&backdropBrush));

    ComPtr<ICompositionBrush> backdropBrush_ICompositionBrush;
    RO_CHECKED("ICompositionBackdropBrush::QueryInterface<ICompositionBrush>",
               backdropBrush->QueryInterface<ICompositionBrush>(&backdropBrush_ICompositionBrush));

    ComPtr<SaturationEffect> saturationEffect = Make<SaturationEffect>(blurEffect);

    ComPtr<ICompositionEffectFactory> saturationEffectFactory;
    RO_CHECKED("ICompositor::CreateEffectFactory",
               compositor_->CreateEffectFactory(saturationEffect.Get(), &saturationEffectFactory));

    ComPtr<ICompositionEffectBrush> saturationBrush;
    RO_CHECKED("ICompositionEffectFactory::CreateBrush",
               saturationEffectFactory->CreateBrush(&saturationBrush));

    RO_CHECKED("ICompositionEffectBrush::SetSourceParameter",
               saturationBrush->SetSourceParameter(hstring("backdrop"), backdropBrush_ICompositionBrush.Get()));

    RO_CHECKED("ICompositionEffectBrush::QueryInterface<ICompositionBrush>",
               saturationBrush->QueryInterface<ICompositionBrush>(&backgroundBrush_));
}

CompositionLayer* Compositor::createCompositionLayer(HWND hwnd) {
    using namespace ABI::Windows::UI::Composition;
    using namespace ABI::Windows::UI::Composition::Desktop;

    ComPtr<ICompositorDesktopInterop> compositorDesktopInterop;
    RO_CHECKED("ICompositor::QueryInterface<ICompositorDesktopInterop>",
               compositor_->QueryInterface<ICompositorDesktopInterop>(&compositorDesktopInterop));

    ComPtr<IDesktopWindowTarget> desktopWindowTarget;
    RO_CHECKED("ICompositorDesktopInterop::CreateDesktopWindowTarget",
               compositorDesktopInterop->CreateDesktopWindowTarget(hwnd, FALSE, &desktopWindowTarget));

    ComPtr<ICompositionTarget> desktopWindowTarget_ICompositionTarget;
    RO_CHECKED("IDesktopWindowTarget::QueryInterface<ICompositionTarget>",
               desktopWindowTarget->QueryInterface<ICompositionTarget>(&desktopWindowTarget_ICompositionTarget));

    ComPtr<ISpriteVisual> contentVisual;
    RO_CHECKED("ICompositor::CreateSpriteVisual",
               compositor_->CreateSpriteVisual(&contentVisual));

    ComPtr<IVisual> contentVisual_IVisual;
    RO_CHECKED("ISpriteVisual::QueryInterface<IVisual>",
               contentVisual->QueryInterface<IVisual>(&contentVisual_IVisual));

    ComPtr<IVisual2> contentVisual_IVisual2;
    RO_CHECKED("ISpriteVisual::QueryInterface<IVisual2>",
               contentVisual->QueryInterface<IVisual2>(&contentVisual_IVisual2));

    ComPtr<IContainerVisual> containerVisual;
    RO_CHECKED("ICompositor::CreateContainerVisual",
               compositor_->CreateContainerVisual(&containerVisual));

    ComPtr<IVisual> containerVisual_IVisual;
    RO_CHECKED("IContainerVisual::QueryInterface<IVisual>",
               containerVisual->QueryInterface<IVisual>(&containerVisual_IVisual));

    ComPtr<IVisual2> containerVisual_IVisual2;
    RO_CHECKED("IContainerVisual::QueryInterface<IVisual2>",
               containerVisual->QueryInterface<IVisual2>(&containerVisual_IVisual2));

    ComPtr<ISpriteVisual> backgroundVisual;
    RO_CHECKED("ICompositor::CreateSpriteVisual",
               compositor_->CreateSpriteVisual(&backgroundVisual));

    ComPtr<IVisual> backgroundVisual_IVisual;
    RO_CHECKED("ISpriteVisual::QueryInterface<IVisual>",
               backgroundVisual->QueryInterface<IVisual>(&backgroundVisual_IVisual));

    ComPtr<IVisual2> backgroundVisual_IVisual2;
    RO_CHECKED("ISpriteVisual::QueryInterface<IVisual2>",
               backgroundVisual->QueryInterface<IVisual2>(&backgroundVisual_IVisual2));

    ComPtr<ICompositionColorBrush> defaultBrush;
    RO_CHECKED("ICompositor::CreateColorBrushWithColor",
               compositor_->CreateColorBrushWithColor({255, 255, 255, 255}, &defaultBrush));

    ComPtr<ICompositionBrush> brush;
    RO_CHECKED("ICompositionColorBrush::QueryInterface<ICompositionBrush>",
               defaultBrush->QueryInterface<ICompositionBrush>(&brush));

    backgroundVisual->put_Brush(brush.Get());
    backgroundVisual_IVisual2->put_RelativeSizeAdjustment({1, 1});
    containerVisual_IVisual2->put_RelativeSizeAdjustment({1, 1});

    ComPtr<IVisualCollection> children;
    containerVisual->get_Children(children.GetAddressOf());
    children->InsertAtTop(contentVisual_IVisual.Get());
    children->InsertAtBottom(backgroundVisual_IVisual.Get());

    desktopWindowTarget_ICompositionTarget->put_Root(containerVisual_IVisual.Get());

    ComPtr<CompositionLayer> instance = Make<CompositionLayer>(
        compositor_, desktopWindowTarget, desktopWindowTarget_ICompositionTarget,
        backgroundBrush_, backgroundVisual, contentVisual);

    instance->AddRef();
    return instance.Get();
}

void Compositor::run(JNIEnv* env, jobject onTick, jmethodID method) {
    using namespace ABI::Windows::Foundation;
    using namespace ABI::Windows::UI::Composition;

    ComPtr<ICompositor5> compositor5;
    RO_CHECKED("ICompositor::QueryInterface<ICompositor5>",
               compositor_->QueryInterface<ICompositor5>(&compositor5));

    ComPtr<IAsyncAction> handler;
    RO_CHECKED("ICompositor5::RequestCommitAsync", compositor5->RequestCommitAsync(&handler));

    ComPtr<IAsyncActionCompletedHandler> completedHandler = Make<Handler>(this, env, onTick, method);
    handler->put_Completed(completedHandler.Get());

    while (!shutdownRequested_) {
        MSG msg;
        GetMessage(&msg, 0, 0, 0);
        DispatchMessage(&msg);
    }
}

void Compositor::tick(
        ABI::Windows::Foundation::IAsyncActionCompletedHandler* completedHandler,
        JNIEnv* env, jobject onTick, jmethodID method) {
    using namespace ABI::Windows::Foundation;
    using namespace ABI::Windows::UI::Composition;

    if (shutdownRequested_) {
        return;
    }

    ComPtr<ICompositor5> compositor5;
    RO_CHECKED("ICompositor::QueryInterface<ICompositor5>",
               compositor_->QueryInterface<ICompositor5>(&compositor5));

    ComPtr<IAsyncAction> handler;
    RO_CHECKED("ICompositor5::RequestCommitAsync", compositor5->RequestCommitAsync(&handler));

    handler->put_Completed(completedHandler);

    env->CallVoidMethod(onTick, method);
}