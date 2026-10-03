package com.whaltermc.mixin;

import com.whaltermc.MobileCompat;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
        targets = "com.moulberry.flashback.editor.ui.CustomImGuiImplGlfw",
        remap = false
)
public abstract class CustomImGuiImplGlfwMixin {

    @Inject(
            method = "updateKeyModifiers",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void flashbackRedroided$updateKeyModifiers(long window, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(
            method = "glfwKeyToImGuiKey",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void flashbackRedroided$updateImGui192KeyValues(
            int glfwKey,
            CallbackInfoReturnable<Integer> cir
    ) {
        int imguiKey = cir.getReturnValue();
        cir.setReturnValue(com.whaltermc.FlashbackImGuiKeyMapper.map(imguiKey));
    }

    @Redirect(
            method = {"ungrab", "setGrabbed"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetInputMode(JII)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$setGrabCursorMode(long window, int mode, int value) {
        if (mode == GLFW.GLFW_CURSOR) {
            MobileCompat.requestCursorMode(window, value);
        } else {
            GLFW.glfwSetInputMode(window, mode, value);
        }
    }

    @Redirect(
            method = "updateMouseCursor",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetInputMode(JII)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$setCursorModeIfChanged(long window, int mode, int value) {
        if (mode == GLFW.GLFW_CURSOR) {
            MobileCompat.requestCursorMode(window, value);
        } else {
            GLFW.glfwSetInputMode(window, mode, value);
        }
    }

    @Redirect(
            method = {"ungrab", "updateMousePosAndButtons"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetCursorPos(JDD)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$setCursorPos(long window, double x, double y) {
        MobileCompat.setCursorPos(window, x, y);
    }

    @Redirect(
            method = {"updateReleaseAllKeys", "updateMouseCursor"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetCursor(JJ)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$setCursorShape(long window, long cursor) {
        MobileCompat.setCursorShape(window, cursor);
    }
}
