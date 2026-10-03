package com.whaltermc.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(
        targets = "com.moulberry.flashback.editor.ui.CustomImGuiImplB3D",
        remap = false
)
public abstract class CustomImGuiImplB3DMixin {

    @ModifyArgs(
            method = "renderDrawData",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderPass;enableScissor(IIII)V"
            ),
            remap = false
    )
    private void flashbackRedroided$fixInvalidScissor(Args args) {
        int width = args.get(2);
        int height = args.get(3);

        if (width <= 0 || height <= 0) {
            args.set(2, 1);
            args.set(3, 1);
        }
    }
}