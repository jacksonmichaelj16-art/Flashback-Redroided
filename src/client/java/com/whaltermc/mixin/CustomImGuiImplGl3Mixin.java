package com.whaltermc.mixin;

import com.whaltermc.MobileCompat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;

/**
 * Fixes ImGui text/icons rendering as solid black blocks under MobileGlues.
 *
 * Symptom: every ImGui glyph is a black rectangle and every "light" element (checkmarks,
 * separators) is black, while panel alpha is still correct. That is what GL returns when the
 * font atlas is sampled as an INCOMPLETE texture (0,0,0,1).
 *
 * Two things can cause that on MobileGlues, so both are handled:
 *  1. A Minecraft sampler object (mipmap min-filter) is still bound to unit 0. Desktop ImGui
 *     unbinds it only when GLCapabilities.GL_ARB_sampler_objects is set, which MobileGlues may
 *     not report. We unbind it unconditionally before ImGui draws.
 *  2. The atlas has only mip level 0. We pin BASE_LEVEL/MAX_LEVEL to 0 so the texture is
 *     complete even if a mipmapped min-filter ends up applied to it.
 */
@Mixin(
        targets = "com.moulberry.flashback.editor.ui.CustomImGuiImplGl3",
        remap = false
)
public abstract class CustomImGuiImplGl3Mixin {

    @Unique
    private static final Logger flashbackRedroided$LOG = LoggerFactory.getLogger("flashback-redroided/imgui-gl");
    @Unique
    private static boolean flashbackRedroided$samplerFailed;
    @Unique
    private static boolean flashbackRedroided$loggedDraw;

    /** Font atlas upload: make the atlas texture complete and log what the driver says. */
    @Redirect(
            method = "updateFontsTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL32;glTexImage2D(IIIIIIIILjava/nio/ByteBuffer;)V"
            ),
            remap = false
    )
    private void flashbackRedroided$uploadFontAtlas(int target, int level, int internalFormat,
                                                    int width, int height, int border,
                                                    int format, int type, ByteBuffer pixels) {
        GL32.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);

        if (!MobileCompat.isMobile()) {
            return;
        }

        GL32.glTexParameteri(target, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL32.glTexParameteri(target, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL32.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL32.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL32.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL32.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        flashbackRedroided$LOG.info("[mobile] font atlas {}x{} fmt=0x{} type=0x{} glError=0x{} renderer={} version={}",
                width, height, Integer.toHexString(format), Integer.toHexString(type),
                Integer.toHexString(GL11.glGetError()),
                GL11.glGetString(GL11.GL_RENDERER), GL11.glGetString(GL11.GL_VERSION));
    }

    /** First texture bind of each draw command: make sure no leftover sampler object overrides it. */
    @Redirect(
            method = "renderDrawData",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL32;glBindTexture(II)V",
                    ordinal = 0
            ),
            remap = false
    )
    private void flashbackRedroided$bindDrawTexture(int target, int texture) {
        GL32.glBindTexture(target, texture);

        if (!MobileCompat.isMobile() || flashbackRedroided$samplerFailed) {
            return;
        }

        try {
            GL33.glBindSampler(0, 0);
        } catch (Throwable t) {
            flashbackRedroided$samplerFailed = true;
            flashbackRedroided$LOG.warn("[mobile] glBindSampler unavailable, skipping sampler unbind", t);
        }

        if (!flashbackRedroided$loggedDraw) {
            flashbackRedroided$loggedDraw = true;
            flashbackRedroided$LOG.info("[mobile] first ImGui draw texture={} glError=0x{}",
                    texture, Integer.toHexString(GL11.glGetError()));
        }
    }
        }
