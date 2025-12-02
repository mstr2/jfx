#include "GraphicsEffect.h"
#include "windows.foundation.h"

using namespace Microsoft::WRL;
using namespace ABI::Windows::Foundation;
using namespace ABI::Windows::Graphics::Effects;

namespace {
    const UINT D2D1_GAUSSIANBLUR_OPTIMIZATION_SPEED = 0;
    const UINT D2D1_GAUSSIANBLUR_OPTIMIZATION_BALANCED = 1;
    const UINT D2D1_GAUSSIANBLUR_OPTIMIZATION_QUALITY = 2;
    const UINT D2D1_GAUSSIANBLUR_OPTIMIZATION_FORCE_DWORD = 3;

    const UINT D2D1_BORDER_MODE_SOFT = 0;
    const UINT D2D1_BORDER_MODE_HARD = 1;
    const UINT D2D1_BORDER_MODE_FORCE_DWORD = 2;

    const UINT D2D1_GAUSSIANBLUR_PROP_STANDARD_DEVIATION = 0;
    const UINT D2D1_GAUSSIANBLUR_PROP_OPTIMIZATION = 1;
    const UINT D2D1_GAUSSIANBLUR_PROP_BORDER_MODE = 2;
    const UINT D2D1_GAUSSIANBLUR_PROP_FORCE_DWORD = 3;

    const UINT D2D1_SATURATION_PROP_SATURATION = 0;
    const UINT D2D1_SATURATION_PROP_FORCE_DWORD = 1;

    #define SAFE_RETURN(ARG, VALUE) if (ARG != nullptr) *ARG = VALUE; return S_OK;

    class PropertyValueImpl : public Microsoft::WRL::RuntimeClass<ABI::Windows::Foundation::IPropertyValue, IInspectable> {
    public:
        PropertyValueImpl(FLOAT value) : flt(value), type(ABI::Windows::Foundation::PropertyType::PropertyType_Single) {}
        PropertyValueImpl(UINT32 value) : uint32(value), type(ABI::Windows::Foundation::PropertyType::PropertyType_UInt32) {}

        virtual HRESULT STDMETHODCALLTYPE get_Type(ABI::Windows::Foundation::PropertyType* value) override {
            SAFE_RETURN(value, type)
        };

        virtual HRESULT STDMETHODCALLTYPE get_IsNumericScalar(boolean* value) override {
            SAFE_RETURN(value, true)
        }

        virtual HRESULT STDMETHODCALLTYPE GetUInt8(BYTE* value) override {
            SAFE_RETURN(value, uint8);
        }

        virtual HRESULT STDMETHODCALLTYPE GetInt16(INT16* value) override {
            SAFE_RETURN(value, int16);
        }

        virtual HRESULT STDMETHODCALLTYPE GetUInt16(UINT16* value) override {
            SAFE_RETURN(value, uint16);
        }

        virtual HRESULT STDMETHODCALLTYPE GetInt32(INT32* value) override {
            SAFE_RETURN(value, int32);
        }

        virtual HRESULT STDMETHODCALLTYPE GetUInt32(UINT32* value) override {
            SAFE_RETURN(value, uint32);
        }

        virtual HRESULT STDMETHODCALLTYPE GetInt64(INT64* value) override {
            SAFE_RETURN(value, int64);
        }

        virtual HRESULT STDMETHODCALLTYPE GetUInt64(UINT64* value) override {
            SAFE_RETURN(value, uint64);
        }

        virtual HRESULT STDMETHODCALLTYPE GetSingle(FLOAT* value) override {
            SAFE_RETURN(value, flt);
        }

        virtual HRESULT STDMETHODCALLTYPE GetDouble(DOUBLE* value) override {
            SAFE_RETURN(value, dbl);
        }

        virtual HRESULT STDMETHODCALLTYPE GetChar16(WCHAR* value) override {
            SAFE_RETURN(value, char16);
        }

        virtual HRESULT STDMETHODCALLTYPE GetBoolean(boolean* value) override {
            SAFE_RETURN(value, bln);
        }

        virtual HRESULT STDMETHODCALLTYPE GetString(HSTRING* value) override {
            SAFE_RETURN(value, hstr);
        }

        virtual HRESULT STDMETHODCALLTYPE GetGuid(GUID* value) override {
            SAFE_RETURN(value, guid);
        }

        virtual HRESULT STDMETHODCALLTYPE GetDateTime(ABI::Windows::Foundation::DateTime* value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetTimeSpan(ABI::Windows::Foundation::TimeSpan* value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetPoint(ABI::Windows::Foundation::Point* value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetSize(ABI::Windows::Foundation::Size* value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetRect(ABI::Windows::Foundation::Rect* value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetUInt8Array(UINT32* __valueSize, BYTE** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetInt16Array(UINT32* __valueSize, INT16** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetUInt16Array(UINT32* __valueSize, UINT16** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetInt32Array(UINT32* __valueSize, INT32** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetUInt32Array(UINT32* __valueSize, UINT32** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetInt64Array(UINT32* __valueSize, INT64** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetUInt64Array(UINT32* __valueSize, UINT64** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetSingleArray(UINT32* __valueSize, FLOAT** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetDoubleArray(UINT32* __valueSize, DOUBLE** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetChar16Array(UINT32* __valueSize, WCHAR** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetBooleanArray(UINT32* __valueSize, ::boolean** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetStringArray(UINT32* __valueSize, HSTRING** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetInspectableArray(UINT32* __valueSize, IInspectable*** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetGuidArray(UINT32* __valueSize, GUID** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetDateTimeArray(UINT32* __valueSize, ABI::Windows::Foundation::DateTime** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetTimeSpanArray(UINT32* __valueSize, ABI::Windows::Foundation::TimeSpan** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetPointArray(UINT32* __valueSize, ABI::Windows::Foundation::Point** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetSizeArray(UINT32* __valueSize, ABI::Windows::Foundation::Size** value) override {
            return E_NOTIMPL;
        }

        virtual HRESULT STDMETHODCALLTYPE GetRectArray(UINT32* __valueSize, ABI::Windows::Foundation::Rect** value) override {
            return E_NOTIMPL;
        }

    private:
        ABI::Windows::Foundation::PropertyType type;

        union {
            BYTE uint8;
            INT16 int16;
            UINT16 uint16;
            INT32 int32;
            UINT32 uint32;
            INT64 int64;
            UINT64 uint64;
            FLOAT flt;
            DOUBLE dbl;
            WCHAR char16;
            boolean bln;
            HSTRING hstr;
            GUID guid;
        };
    };
}

GaussianBlurEffect::GaussianBlurEffect(ComPtr<IGraphicsEffectSource> source) : source(source) {
}

HRESULT GaussianBlurEffect::get_Name(HSTRING* name) {
    *name = nullptr;
    return S_OK;
}

HRESULT GaussianBlurEffect::put_Name(HSTRING name) {
    return S_OK;
}

HRESULT GaussianBlurEffect::GetEffectId(GUID* pid) {
    if (pid != nullptr) {
        *pid = GUID { 0x1FEB6D69, 0x2FE6, 0x4AC9, 0x8C, 0x58, 0x1D, 0x7F, 0x93, 0xE7, 0xA6, 0xA5 };
    }

    return S_OK;
}

HRESULT GaussianBlurEffect::GetPropertyCount(UINT* count) {
    if (count != nullptr) {
        *count = 3;
    }

    return S_OK;
}

HRESULT GaussianBlurEffect::GetProperty(UINT index, IPropertyValue** value) {
    switch (index) {
    case D2D1_GAUSSIANBLUR_PROP_STANDARD_DEVIATION: {
        ComPtr<PropertyValueImpl> pvalue = Make<PropertyValueImpl>(30.0f);
        pvalue->AddRef();
        *value = pvalue.Get();
        return S_OK;
    }

    case D2D1_GAUSSIANBLUR_PROP_OPTIMIZATION: {
        ComPtr<PropertyValueImpl> pvalue = Make<PropertyValueImpl>((UINT)D2D1_GAUSSIANBLUR_OPTIMIZATION_BALANCED);
        pvalue->AddRef();
        *value = pvalue.Get();
        return S_OK;
    }

    case D2D1_GAUSSIANBLUR_PROP_BORDER_MODE: {
        ComPtr<PropertyValueImpl> pvalue = Make<PropertyValueImpl>((UINT)D2D1_BORDER_MODE_HARD);
        pvalue->AddRef();
        *value = pvalue.Get();
        return S_OK;
    }
    }

    return E_INVALIDARG;
}

HRESULT GaussianBlurEffect::GetSource(UINT index, IGraphicsEffectSource** source) {
    if (index >= 1) {
        return E_INVALIDARG;
    }

    if (source != nullptr) {
        this->source->AddRef();
        *source = this->source.Get();
    }

    return S_OK;
}

HRESULT GaussianBlurEffect::GetSourceCount(UINT* count) {
    if (count != nullptr) {
        *count = 1;
    }

    return S_OK;
}

HRESULT GaussianBlurEffect::GetNamedPropertyMapping(LPCWSTR, UINT*, GRAPHICS_EFFECT_PROPERTY_MAPPING*) {
    return E_NOTIMPL;
}

SaturationEffect::SaturationEffect(ComPtr<IGraphicsEffectSource> source) : source(source) {
}

HRESULT SaturationEffect::get_Name(HSTRING* name) {
    *name = nullptr;
    return S_OK;
}

HRESULT SaturationEffect::put_Name(HSTRING name) {
    return S_OK;
}

HRESULT SaturationEffect::GetEffectId(GUID* pid) {
    if (pid != nullptr) {
        *pid = GUID { 0x5CB2D9CF, 0x327D, 0x459F, 0xA0, 0xCE, 0x40, 0xC0, 0xB2, 0x08, 0x6B, 0xF7 };
    }

    return S_OK;
}

HRESULT SaturationEffect::GetPropertyCount(UINT* count) {
    if (count != nullptr) {
        *count = 1;
    }

    return S_OK;
}

HRESULT SaturationEffect::GetProperty(UINT index, IPropertyValue** value) {
    switch (index) {
        case D2D1_SATURATION_PROP_SATURATION: {
            ComPtr<PropertyValueImpl> pvalue = Make<PropertyValueImpl>(2.0f);
            pvalue->AddRef();
            *value = pvalue.Get();
            return S_OK;
        }
    }

    return E_INVALIDARG;
}

HRESULT SaturationEffect::GetSource(UINT index, IGraphicsEffectSource** source) {
    if (index >= 1) {
        return E_INVALIDARG;
    }

    if (source != nullptr) {
        this->source->AddRef();
        *source = this->source.Get();
    }

    return S_OK;
}

HRESULT SaturationEffect::GetSourceCount(UINT* count) {
    if (count != nullptr) {
        *count = 1;
    }

    return S_OK;
}

HRESULT SaturationEffect::GetNamedPropertyMapping(LPCWSTR, UINT*, GRAPHICS_EFFECT_PROPERTY_MAPPING*) {
    return E_NOTIMPL;
}