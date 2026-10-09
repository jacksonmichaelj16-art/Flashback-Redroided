package com.whaltermc.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.moulberry.flashback.exporting.ImageFrame;
import com.moulberry.flashback.exporting.SaveableFramebuffer;
import com.whaltermc.DirectFrameReadback;
import com.whaltermc.MobileCompat;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SaveableFramebuffer.class, remap = false)
public abstract class SaveableFramebufferMixin {
    @Shadow @Final private int width;
    @Shadow @Final private int height;
    @Shadow private ImageFrame downloaded;
    @Shadow private boolean isDownloading;

    @Inject(method = "startDownload", cancellable = true, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;"))
    private void flashbackRedroided$readColorFrame(GpuTexture texture, CallbackInfo ci,
                                                 @Local ImageFrame.Format format) {
        if (!MobileCompat.isMobile() || format != ImageFrame.Format.RGBA_U8
                || !(texture instanceof GlTexture glTexture)) {
            return;
        }
        // The original method has already checked its lifecycle and determined
        // the format. Publish owned pixels only after synchronous readback succeeds.
        ImageFrame frame = null;
        try {
            frame = new ImageFrame(width, height, format, false);
            DirectFrameReadback.readRgba(glTexture.glId(), width, height,
                    MemoryUtil.memByteBuffer(frame.pixels, Math.toIntExact(frame.size)));
            downloaded = frame;
        } catch (RuntimeException | Error e) {
            if (frame != null) frame.close();
            isDownloading = false;
            throw e;
        }
        ci.cancel();
    }
}
