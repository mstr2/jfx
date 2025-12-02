#pragma once

#include "CompositionLayer.h"
#include <DispatcherQueue.h>
#include <windows.ui.composition.h>

class Compositor final : public Microsoft::WRL::RuntimeClass<
        Microsoft::WRL::RuntimeClassFlags<Microsoft::WRL::RuntimeClassType::WinRtClassicComMix>, IUnknown> {
public:
    Compositor();
    Compositor(Compositor const&) = delete;

    CompositionLayer* createCompositionLayer(HWND);
    void run(JNIEnv*, jobject, jmethodID);
    void tick(ABI::Windows::Foundation::IAsyncActionCompletedHandler*, JNIEnv*, jobject, jmethodID);

    volatile bool shutdownRequested_;

private:
	Microsoft::WRL::ComPtr<ABI::Windows::System::IDispatcherQueueController> dispatcherQueueController_;
	Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ICompositionBrush> backgroundBrush_;
	Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ICompositor> compositor_;
};
