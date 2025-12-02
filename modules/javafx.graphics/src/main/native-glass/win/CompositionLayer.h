#pragma once

#include "RoActivationSupport.h"
#include <windows.ui.composition.h>
#include <windows.ui.composition.interop.h>
#include <windows.ui.composition.desktop.h>
#include <d3d9.h>
#include <d3d11.h>

class CompositionLayer final : public Microsoft::WRL::RuntimeClass<
        Microsoft::WRL::RuntimeClassFlags<Microsoft::WRL::RuntimeClassType::WinRtClassicComMix>, IUnknown> {
public:
    CompositionLayer(
        Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ICompositor>,
        Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::Desktop::IDesktopWindowTarget>,
        Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ICompositionTarget>,
        Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ICompositionBrush>,
        Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ISpriteVisual>,
        Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ISpriteVisual>);

    CompositionLayer(CompositionLayer const&) = delete;

    void setBlurBehind(bool);
    void present(ID3D11Device*, HANDLE, int width, int height);

private:
    void ensureDevice(ID3D11Device*);
    void render();

    bool blurBehind_;
    int width_, height_;
    Microsoft::WRL::ComPtr<ID3D11Device> device_;
    Microsoft::WRL::ComPtr<ID3D11DeviceContext> deviceContext_;
    Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ICompositor> compositor_;
    Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::Desktop::IDesktopWindowTarget> desktopWindowTarget_;
    Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ICompositionTarget> compositionTarget_;
	Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ICompositionDrawingSurfaceInterop> drawingSurfaceInterop_;
	Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ICompositionBrush> backgroundBrush_;
	Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ISpriteVisual> backgroundVisual_;
	Microsoft::WRL::ComPtr<ABI::Windows::UI::Composition::ISpriteVisual> contentVisual_;
};
