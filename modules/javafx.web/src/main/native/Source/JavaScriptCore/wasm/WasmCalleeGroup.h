/*
 * Copyright (C) 2017-2018 Apple Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY APPLE INC. ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL APPLE INC. OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#pragma once

#if ENABLE(WEBASSEMBLY)

#include "MacroAssemblerCodeRef.h"
#include "MemoryMode.h"
#include "WasmCallee.h"
#include "WasmCallsiteCollection.h"
#include "WasmJS.h"
#include <wtf/CrossThreadCopier.h>
#include <wtf/FixedVector.h>
#include <wtf/Lock.h>
#include <wtf/RefPtr.h>
#include <wtf/SharedTask.h>
#include <wtf/ThreadSafeRefCounted.h>
#include <wtf/text/WTFString.h>

namespace JSC {

class VM;

namespace Wasm {

class EntryPlan;
struct ModuleInformation;
struct UnlinkedWasmToWasmCall;

class CalleeGroup final : public ThreadSafeRefCounted<CalleeGroup> {
public:
    friend class CallsiteCollection;
    typedef void CallbackType(Ref<CalleeGroup>&&, bool);
    using AsyncCompilationCallback = RefPtr<WTF::SharedTask<CallbackType>>;
    static Ref<CalleeGroup> createFromLLInt(VM&, MemoryMode, ModuleInformation&, RefPtr<LLIntCallees>);
    static Ref<CalleeGroup> createFromIPInt(VM&, MemoryMode, ModuleInformation&, RefPtr<IPIntCallees>);
    static Ref<CalleeGroup> createFromExisting(MemoryMode, const CalleeGroup&);

    void waitUntilFinished();
    void compileAsync(VM&, AsyncCompilationCallback&&);

    bool compilationFinished()
    {
        return m_compilationFinished.load();
    }
    bool runnable() { return compilationFinished() && !m_errorMessage; }

    // Note, we do this copy to ensure it's thread safe to have this
    // called from multiple threads simultaneously.
    String errorMessage()
    {
        ASSERT(!runnable());
        return crossThreadCopy(m_errorMessage);
    }

    unsigned functionImportCount() const { return m_wasmToWasmExitStubs.size(); }

    // These two callee getters are only valid once the callees have been populated.

    JSEntrypointCallee& jsEntrypointCalleeFromFunctionIndexSpace(unsigned functionIndexSpace)
    {
        ASSERT(runnable());
        RELEASE_ASSERT(functionIndexSpace >= functionImportCount());
        unsigned calleeIndex = functionIndexSpace - functionImportCount();

        auto callee = m_jsEntrypointCallees.get(calleeIndex);
        RELEASE_ASSERT(callee);
        return *callee;
    }

    Callee& wasmEntrypointCalleeFromFunctionIndexSpace(const AbstractLocker&, unsigned functionIndexSpace)
    {
        ASSERT(runnable());
        RELEASE_ASSERT(functionIndexSpace >= functionImportCount());
        unsigned calleeIndex = functionIndexSpace - functionImportCount();
#if ENABLE(WEBASSEMBLY_OMGJIT)
        if (!m_omgCallees.isEmpty() && m_omgCallees[calleeIndex])
            return *m_omgCallees[calleeIndex].get();
        if (!m_bbqCallees.isEmpty() && m_bbqCallees[calleeIndex])
            return *m_bbqCallees[calleeIndex].get();
#endif
        if (Options::useWasmIPInt())
            return m_ipintCallees->at(calleeIndex).get();
        return m_llintCallees->at(calleeIndex).get();
    }

#if ENABLE(WEBASSEMBLY_BBQJIT)
    BBQCallee& wasmBBQCalleeFromFunctionIndexSpace(unsigned functionIndexSpace)
    {
        // We do not look up without locking because this function is called from this BBQCallee itself.
        ASSERT(runnable());
        RELEASE_ASSERT(functionIndexSpace >= functionImportCount());
        unsigned calleeIndex = functionIndexSpace - functionImportCount();
        ASSERT(m_bbqCallees[calleeIndex]);
        return *m_bbqCallees[calleeIndex].get();
    }

    BBQCallee* bbqCallee(const AbstractLocker&, unsigned functionIndex)
    {
        if (m_bbqCallees.isEmpty())
            return nullptr;
        return m_bbqCallees[functionIndex].get();
    }

    void setBBQCallee(const AbstractLocker&, unsigned functionIndex, Ref<BBQCallee>&& callee)
    {
        if (m_bbqCallees.isEmpty())
            m_bbqCallees = FixedVector<RefPtr<BBQCallee>>(m_calleeCount);
        m_bbqCallees[functionIndex] = WTFMove(callee);
    }

#endif

#if ENABLE(WEBASSEMBLY_OMGJIT)
    OMGCallee* omgCallee(const AbstractLocker&, unsigned functionIndex)
    {
        if (m_omgCallees.isEmpty())
            return nullptr;
        return m_omgCallees[functionIndex].get();
    }

    void setOMGCallee(const AbstractLocker&, unsigned functionIndex, Ref<OMGCallee>&& callee)
    {
        if (m_omgCallees.isEmpty())
            m_omgCallees = FixedVector<RefPtr<OMGCallee>>(m_calleeCount);
        m_omgCallees[functionIndex] = WTFMove(callee);
    }
#endif

    CodePtr<WasmEntryPtrTag>* entrypointLoadLocationFromFunctionIndexSpace(unsigned functionIndexSpace)
    {
        RELEASE_ASSERT(functionIndexSpace >= functionImportCount());
        unsigned calleeIndex = functionIndexSpace - functionImportCount();
        return &m_wasmIndirectCallEntryPoints[calleeIndex];
    }

    // This is the callee used by LLInt/IPInt, not by the JS->Wasm entrypoint
    Wasm::Callee* wasmCalleeFromFunctionIndexSpace(unsigned functionIndexSpace)
    {
        RELEASE_ASSERT(functionIndexSpace >= functionImportCount());
        unsigned calleeIndex = functionIndexSpace - functionImportCount();
        return m_wasmIndirectCallWasmCallees[calleeIndex].get();
    }

    CodePtr<WasmEntryPtrTag> wasmToWasmExitStub(unsigned functionIndex)
    {
        return m_wasmToWasmExitStubs[functionIndex].code();
    }

    bool isSafeToRun(MemoryMode);

    MemoryMode mode() const { return m_mode; }

    CallsiteCollection& callsiteCollection() { return m_callsiteCollection; }
    const CallsiteCollection& callsiteCollection() const { return m_callsiteCollection; }

    ~CalleeGroup();
private:
    friend class Plan;
#if ENABLE(WEBASSEMBLY_BBQJIT)
    friend class BBQPlan;
#endif
#if ENABLE(WEBASSEMBLY_OMGJIT)
    friend class OMGPlan;
    friend class OSREntryPlan;
#endif

    CalleeGroup(VM&, MemoryMode, ModuleInformation&, RefPtr<LLIntCallees>);
    CalleeGroup(VM&, MemoryMode, ModuleInformation&, RefPtr<IPIntCallees>);
    CalleeGroup(MemoryMode, const CalleeGroup&);
    void setCompilationFinished();
    unsigned m_calleeCount;
    MemoryMode m_mode;
#if ENABLE(WEBASSEMBLY_OMGJIT)
    FixedVector<RefPtr<OMGCallee>> m_omgCallees;
#endif
#if ENABLE(WEBASSEMBLY_BBQJIT)
    FixedVector<RefPtr<BBQCallee>> m_bbqCallees;
#endif
    RefPtr<IPIntCallees> m_ipintCallees;
    RefPtr<LLIntCallees> m_llintCallees;
    HashMap<uint32_t, RefPtr<JSEntrypointCallee>, DefaultHash<uint32_t>, WTF::UnsignedWithZeroKeyHashTraits<uint32_t>> m_jsEntrypointCallees;
    FixedVector<CodePtr<WasmEntryPtrTag>> m_wasmIndirectCallEntryPoints;
    FixedVector<RefPtr<Wasm::Callee>> m_wasmIndirectCallWasmCallees;
    FixedVector<MacroAssemblerCodeRef<WasmEntryPtrTag>> m_wasmToWasmExitStubs;
    RefPtr<EntryPlan> m_plan;
    CallsiteCollection m_callsiteCollection;
    std::atomic<bool> m_compilationFinished { false };
    String m_errorMessage;
public:
    Lock m_lock;
};

} } // namespace JSC::Wasm

#endif // ENABLE(WEBASSEMBLY)
