/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import static org.truffleruby.core.array.ArrayHelpers.setSize;
import static org.truffleruby.core.array.ArrayHelpers.setStoreAndSize;

import org.truffleruby.Layouts;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.objects.shared.PropagateSharingNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild(value = "array", type = RubyNode.class)
@NodeChild(value = "value", type = RubyNode.class)
@ImportStatic(ArrayGuards.class)
public abstract class ArrayAppendOneNode extends RubyContextSourceNode {

    @Child private PropagateSharingNode propagateSharingNode = PropagateSharingNode.create();

    public static ArrayAppendOneNode create() {
        return ArrayAppendOneNodeGen.create(null, null);
    }

    public abstract DynamicObject executeAppendOne(DynamicObject array, Object value);

    // Append of the correct type

    @Specialization(
            guards = { "stores.acceptsValue(getStore(array), value)" },
            limit = "STORAGE_STRATEGIES")
    protected DynamicObject appendOneSameType(DynamicObject array, Object value,
            @CachedLibrary("getStore(array)") ArrayStoreLibrary stores,
            @Cached("createCountingProfile()") ConditionProfile extendProfile) {
        final Object store = Layouts.ARRAY.getStore(array);
        final int oldSize = Layouts.ARRAY.getSize(array);
        final int newSize = oldSize + 1;
        final int length = stores.capacity(store);

        propagateSharingNode.executePropagate(array, value);

        if (extendProfile.profile(newSize > length)) {
            final int capacity = ArrayUtils.capacityForOneMore(getContext(), length);
            final Object newStore = stores.expand(store, capacity);
            stores.write(newStore, oldSize, value);
            setStoreAndSize(array, newStore, newSize);
        } else {
            stores.write(store, oldSize, value);
            setSize(array, newSize);
        }
        return array;
    }

    // Append forcing a generalization

    @Specialization(
            guards = "!currentStores.acceptsValue(getStore(array), value)",
            limit = "ARRAY_STRATEGIES")
    protected DynamicObject appendOneGeneralizeNonMutable(DynamicObject array, Object value,
            @CachedLibrary("getStore(array)") ArrayStoreLibrary currentStores,
            @CachedLibrary(limit = "STORAGE_STRATEGIES") ArrayStoreLibrary newStores) {
        final int oldSize = Layouts.ARRAY.getSize(array);
        final int newSize = oldSize + 1;
        final Object currentStore = Layouts.ARRAY.getStore(array);
        final int oldCapacity = currentStores.capacity(currentStore);
        final int newCapacity = newSize > oldCapacity
                ? ArrayUtils.capacityForOneMore(getContext(), oldCapacity)
                : oldCapacity;
        // TODO (norswap, 03 Apr 2020): this is a performance warning (inlining a virtual call)
        final Object newStore = currentStores.generalizeForValue(currentStore, value).allocate(newCapacity);
        currentStores.copyContents(currentStore, 0, newStore, 0, oldSize);
        propagateSharingNode.executePropagate(array, value);
        newStores.write(newStore, oldSize, value);
        setStoreAndSize(array, newStore, newSize);
        return array;
    }
}
