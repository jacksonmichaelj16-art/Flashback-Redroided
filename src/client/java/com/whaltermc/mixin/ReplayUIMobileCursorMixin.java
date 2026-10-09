package com.whaltermc.mixin;

import com.moulberry.flashback.Flashback;
import com.whaltermc.MinecraftScreenAccess;
import com.whaltermc.MobileCompat;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.moulberry.flashback.editor.ui.ReplayUI", remap = false)
public abstract class ReplayUIMobileCursorMixin {

    @Shadow
    private static boolean initialized;

    @Inject(method = "drawOverlay", at = @At("HEAD"), cancellable = true)
    private static void flashbackRedroided$deferInputCallbacks(CallbackInfo ci) {
        // Initialization replaces the launcher's Minecraft input callbacks. Do not
        // install the editor backend on the title/loading screens on Android.
        if (MobileCompat.isMobile() && !initialized
                && (!Flashback.isInReplay() || MinecraftScreenAccess.hasScreen(Minecraft.getInstance()))) {
            ci.cancel();
        }
    }

    @Inject(method = "isActiveInternal", at = @At("HEAD"), cancellable = true)
    private static void flashbackRedroided$minecraftMenuInput(CallbackInfoReturnable<Boolean> cir) {
        // Let the normal active-state transition release editor grabs and route
        // callbacks back to Minecraft while a pause/options/etc. screen is open.
        if (MobileCompat.isMobile() && MinecraftScreenAccess.hasScreen(Minecraft.getInstance())) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(
            method = "transitionActiveState",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetInputMode(JII)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$setCursorMode(long window, int mode, int value) {
        if (mode == GLFW.GLFW_CURSOR) {
            MobileCompat.requestCursorMode(window, value);
        } else {
            GLFW.glfwSetInputMode(window, mode, value);
        }
    }

    @Redirect(
            method = "transitionActiveState",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetCursorPos(JDD)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$setCursorPos(long window, double x, double y) {
        MobileCompat.setCursorPos(window, x, y);
    }
}
