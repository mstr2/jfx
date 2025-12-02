#pragma once

#include "RoActivationSupport.h"
#include <windows.graphics.effects.interop.h>

class GaussianBlurEffect : public Microsoft::WRL::RuntimeClass<
        Microsoft::WRL::RuntimeClassFlags<Microsoft::WRL::RuntimeClassType::WinRtClassicComMix>,
        ABI::Windows::Graphics::Effects::IGraphicsEffect,
        ABI::Windows::Graphics::Effects::IGraphicsEffectSource,
        ABI::Windows::Graphics::Effects::IGraphicsEffectD2D1Interop> {
public:
    GaussianBlurEffect(Microsoft::WRL::ComPtr<ABI::Windows::Graphics::Effects::IGraphicsEffectSource>);

    virtual HRESULT STDMETHODCALLTYPE get_Name(HSTRING* name) override;
    virtual HRESULT STDMETHODCALLTYPE put_Name(HSTRING name) override;
    virtual HRESULT STDMETHODCALLTYPE GetEffectId(GUID*) override;
    virtual HRESULT STDMETHODCALLTYPE GetNamedPropertyMapping(LPCWSTR, UINT*, ABI::Windows::Graphics::Effects::GRAPHICS_EFFECT_PROPERTY_MAPPING*) override;
    virtual HRESULT STDMETHODCALLTYPE GetPropertyCount(UINT*) override;
    virtual HRESULT STDMETHODCALLTYPE GetProperty(UINT, ABI::Windows::Foundation::IPropertyValue**) override;
    virtual HRESULT STDMETHODCALLTYPE GetSource(UINT, ABI::Windows::Graphics::Effects::IGraphicsEffectSource**) override;
    virtual HRESULT STDMETHODCALLTYPE GetSourceCount(UINT*) override;

private:
    Microsoft::WRL::ComPtr<ABI::Windows::Graphics::Effects::IGraphicsEffectSource> source;
};

class SaturationEffect : public Microsoft::WRL::RuntimeClass<
    Microsoft::WRL::RuntimeClassFlags<Microsoft::WRL::RuntimeClassType::WinRtClassicComMix>,
    ABI::Windows::Graphics::Effects::IGraphicsEffect,
    ABI::Windows::Graphics::Effects::IGraphicsEffectSource,
    ABI::Windows::Graphics::Effects::IGraphicsEffectD2D1Interop> {
public:
    SaturationEffect(Microsoft::WRL::ComPtr<ABI::Windows::Graphics::Effects::IGraphicsEffectSource>);

    virtual HRESULT STDMETHODCALLTYPE get_Name(HSTRING* name) override;
    virtual HRESULT STDMETHODCALLTYPE put_Name(HSTRING name) override;
    virtual HRESULT STDMETHODCALLTYPE GetEffectId(GUID*) override;
    virtual HRESULT STDMETHODCALLTYPE GetNamedPropertyMapping(LPCWSTR, UINT*, ABI::Windows::Graphics::Effects::GRAPHICS_EFFECT_PROPERTY_MAPPING*) override;
    virtual HRESULT STDMETHODCALLTYPE GetPropertyCount(UINT*) override;
    virtual HRESULT STDMETHODCALLTYPE GetProperty(UINT, ABI::Windows::Foundation::IPropertyValue**) override;
    virtual HRESULT STDMETHODCALLTYPE GetSource(UINT, ABI::Windows::Graphics::Effects::IGraphicsEffectSource**) override;
    virtual HRESULT STDMETHODCALLTYPE GetSourceCount(UINT*) override;

private:
    Microsoft::WRL::ComPtr<ABI::Windows::Graphics::Effects::IGraphicsEffectSource> source;
};