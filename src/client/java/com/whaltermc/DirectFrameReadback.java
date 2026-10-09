package com.whaltermc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/** Synchronous RGBA readback for launchers with unreliable pixel-pack buffer mapping. */
public final class DirectFrameReadback {
    private DirectFrameReadback() {}

    public static void readRgba(int texture, int width, int height, ByteBuffer pixels) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Frame dimensions must be positive");
        }
        long size = Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
        if (!pixels.isDirect() || pixels.isReadOnly() || pixels.remaining() < size) {
            throw new IllegalArgumentException("Frame destination must be a writable direct RGBA buffer");
        }

        int previousFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int framebuffer = GL30.glGenFramebuffers();
        try {
            // Restore the actual GL bindings below so Minecraft's cached state
            // remains correct, including when its read and draw FBOs differ.
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, texture, 0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Export read framebuffer is incomplete: 0x"
                        + Integer.toHexString(status));
            }
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("OpenGL error before export readback: 0x"
                        + Integer.toHexString(error));
            }
            // With no pack buffer bound, this returns only once CPU pixels are ready.
            // Keep the same bottom-up row order as Minecraft's existing readback.
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("OpenGL export readback failed: 0x"
                        + Integer.toHexString(error));
            }
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPackBuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousFramebuffer);
            GL30.glDeleteFramebuffers(framebuffer);
        }
    }
}
