/*
 * Copyright (C) 2015, 2016 Apple Inc. All rights reserved.
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

#include "config.h"
#include "CustomElementRegistry.h"

#include "CustomElementReactionQueue.h"
#include "Document.h"
#include "DocumentInlines.h"
#include "ElementRareData.h"
#include "ElementTraversal.h"
#include "JSCustomElementInterface.h"
#include "JSDOMPromiseDeferred.h"
#include "LocalDOMWindow.h"
#include "MathMLNames.h"
#include "QualifiedName.h"
#include "Quirks.h"
#include "ShadowRoot.h"
#include "TypedElementDescendantIteratorInlines.h"
#include <JavaScriptCore/JSCJSValueInlines.h>
#include <wtf/text/AtomString.h>

namespace WebCore {

Ref<CustomElementRegistry> CustomElementRegistry::create(LocalDOMWindow& window, ScriptExecutionContext* scriptExecutionContext)
{
    return adoptRef(*new CustomElementRegistry(window, scriptExecutionContext));
}

CustomElementRegistry::CustomElementRegistry(LocalDOMWindow& window, ScriptExecutionContext* scriptExecutionContext)
    : ContextDestructionObserver(scriptExecutionContext)
    , m_window(window)
{
}

CustomElementRegistry::~CustomElementRegistry() = default;

Document* CustomElementRegistry::document() const
{
    return m_window ? m_window->document() : nullptr;
}

// https://dom.spec.whatwg.org/#concept-shadow-including-tree-order
static void enqueueUpgradeInShadowIncludingTreeOrder(ContainerNode& node, JSCustomElementInterface& elementInterface)
{
    for (RefPtr element = ElementTraversal::firstWithin(node); element; element = ElementTraversal::next(*element)) {
        if (element->isCustomElementUpgradeCandidate() && element->tagQName().matches(elementInterface.name()))
            element->enqueueToUpgrade(elementInterface);
        if (RefPtr shadowRoot = element->shadowRoot()) {
            if (shadowRoot->mode() != ShadowRootMode::UserAgent)
                enqueueUpgradeInShadowIncludingTreeOrder(*shadowRoot, elementInterface);
        }
    }
}

RefPtr<DeferredPromise> CustomElementRegistry::addElementDefinition(Ref<JSCustomElementInterface>&& elementInterface)
{
    static MainThreadNeverDestroyed<const AtomString> extendsLi("extends-li"_s);

    AtomString localName = elementInterface->name().localName();
    ASSERT(!m_nameMap.contains(localName));
    m_nameMap.add(localName, elementInterface.copyRef());
    {
        Locker locker { m_constructorMapLock };
        m_constructorMap.add(elementInterface->constructor(), elementInterface.ptr());
    }

    if (elementInterface->isShadowDisabled())
        m_disabledShadowSet.add(localName);

    if (RefPtr document = this->document()) {
        // ungap/@custom-elements detection for quirk (rdar://problem/111008826).
        if (localName == extendsLi.get())
            document->quirks().setNeedsConfigurableIndexedPropertiesQuirk();
        enqueueUpgradeInShadowIncludingTreeOrder(*document, elementInterface.get());
    }

    return m_promiseMap.take(localName);
}

JSCustomElementInterface* CustomElementRegistry::findInterface(const Element& element) const
{
    return findInterface(element.tagQName());
}

JSCustomElementInterface* CustomElementRegistry::findInterface(const QualifiedName& name) const
{
    if (name.namespaceURI() != HTMLNames::xhtmlNamespaceURI)
        return nullptr;
    return m_nameMap.get(name.localName());
}

JSCustomElementInterface* CustomElementRegistry::findInterface(const AtomString& name) const
{
    return m_nameMap.get(name);
}

RefPtr<JSCustomElementInterface> CustomElementRegistry::findInterface(const JSC::JSObject* constructor) const
{
    Locker locker { m_constructorMapLock };
    return m_constructorMap.get(constructor);
}

bool CustomElementRegistry::containsConstructor(const JSC::JSObject* constructor) const
{
    Locker locker { m_constructorMapLock };
    return m_constructorMap.contains(constructor);
}

JSC::JSValue CustomElementRegistry::get(const AtomString& name)
{
    if (RefPtr elementInterface = m_nameMap.get(name))
        return elementInterface->constructor();
    return JSC::jsUndefined();
}

String CustomElementRegistry::getName(JSC::JSValue constructorValue)
{
    auto* constructor = constructorValue.getObject();
    if (!constructor)
        return String { };
    RefPtr elementInterface = findInterface(constructor);
    if (!elementInterface)
        return String { };
    return elementInterface->name().localName();
}

static void upgradeElementsInShadowIncludingDescendants(ContainerNode& root)
{
    for (Ref element : descendantsOfType<Element>(root)) {
        if (element->isCustomElementUpgradeCandidate())
            CustomElementReactionQueue::tryToUpgradeElement(element);
        if (RefPtr shadowRoot = element->shadowRoot())
            upgradeElementsInShadowIncludingDescendants(*shadowRoot);
    }
}

void CustomElementRegistry::upgrade(Node& root)
{
    auto* containerNode = dynamicDowncast<ContainerNode>(root);
    if (!containerNode)
        return;

    RefPtr element = dynamicDowncast<Element>(*containerNode);
    if (element && element->isCustomElementUpgradeCandidate())
        CustomElementReactionQueue::tryToUpgradeElement(*element);

    upgradeElementsInShadowIncludingDescendants(*containerNode);
}

template<typename Visitor>
void CustomElementRegistry::visitJSCustomElementInterfaces(Visitor& visitor) const
{
    Locker locker { m_constructorMapLock };
    for (const auto& iterator : m_constructorMap)
        iterator.value->visitJSFunctions(visitor);
}

template void CustomElementRegistry::visitJSCustomElementInterfaces(JSC::AbstractSlotVisitor&) const;
template void CustomElementRegistry::visitJSCustomElementInterfaces(JSC::SlotVisitor&) const;

}
