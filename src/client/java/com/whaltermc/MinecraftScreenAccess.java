package com.whaltermc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/** Screen access moved from Minecraft.screen to Minecraft.gui.screen() in 26.2. */
public final class MinecraftScreenAccess {
    private MinecraftScreenAccess() {}

    private static final ClassValue<MethodHandle> GETTERS = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                MethodHandle getter;
                try {
                    getter = lookup.unreflectGetter(type.getField("screen"));
                } catch (NoSuchFieldException ignored) {
                    Field gui = type.getField("gui");
                    getter = MethodHandles.filterReturnValue(
                            lookup.unreflectGetter(gui),
                            lookup.unreflect(gui.getType().getMethod("screen")));
                }
                return getter.asType(MethodType.methodType(Object.class, Object.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot locate Minecraft's current screen", e);
            }
        }
    };

    public static boolean hasScreen(Object minecraft) {
        try {
            return (Object) GETTERS.get(minecraft.getClass()).invokeExact(minecraft) != null;
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot read Minecraft's current screen", e);
        }
    }
}
