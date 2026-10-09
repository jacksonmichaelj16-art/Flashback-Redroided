import com.whaltermc.DirectFrameReadback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Native GL regression: run with a display (Xvfb is sufficient) and LWJGL natives. */
public class DirectFrameReadbackTest {
    public static void main(String[] args) {
        check(glfwInit(), "GLFW initialization");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        long window = glfwCreateWindow(32, 32, "Readback test", 0, 0);
        check(window != 0, "GL context creation");
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            runChecks();
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    private static void runChecks() {
        int width = 7, height = 5, size = width * height * 4;
        ByteBuffer source = MemoryUtil.memAlloc(size);
        ByteBuffer output = MemoryUtil.memAlloc(size + 32);
        int texture = glGenTextures();
        int readFbo = glGenFramebuffers(), drawFbo = glGenFramebuffers();
        int packBuffer = glGenBuffers();
        try {
            glBindTexture(GL_TEXTURE_2D, texture);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
            glBufferData(GL_PIXEL_PACK_BUFFER, 64L, GL_STREAM_READ);
            glPixelStorei(GL_PACK_ALIGNMENT, 8);
            glPixelStorei(GL_PACK_ROW_LENGTH, 11);
            glPixelStorei(GL_PACK_SKIP_ROWS, 2);
            glPixelStorei(GL_PACK_SKIP_PIXELS, 3);

            for (int frame = 0; frame < 3; frame++) {
                for (int i = 0; i < size; i++) source.put(i, (byte) (i * 17 + frame * 61));
                for (int i = 0; i < output.capacity(); i++) output.put(i, (byte) 0x5a);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                        GL_RGBA, GL_UNSIGNED_BYTE, source);
                output.position(16).limit(16 + size);
                DirectFrameReadback.readRgba(texture, width, height, output);
                check(output.position() == 16, "Destination position preserved");
                for (int i = 0; i < size; i++) {
                    check(output.get(16 + i) == source.get(i), "Frame " + frame + ", byte " + i);
                }
                output.clear();
                for (int i = 0; i < 16; i++) {
                    check(output.get(i) == (byte) 0x5a && output.get(16 + size + i) == (byte) 0x5a,
                            "No writes outside destination");
                }
                checkState(readFbo, drawFbo, packBuffer);
            }

            try {
                DirectFrameReadback.readRgba(0, width, height, output);
                throw new AssertionError("Unattached framebuffer should fail");
            } catch (IllegalStateException expected) {
                checkState(readFbo, drawFbo, packBuffer);
            }
            try {
                DirectFrameReadback.readRgba(texture, width, height, output.limit(size - 1));
                throw new AssertionError("Undersized destination should fail");
            } catch (IllegalArgumentException expected) {
                checkState(readFbo, drawFbo, packBuffer);
            }
            System.out.println("PASS: 3 exact RGBA frames, row order, buffer bounds, pack/FBO state and failure cleanup; "
                    + glGetString(GL_RENDERER));
        } finally {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glDeleteBuffers(packBuffer);
            glDeleteFramebuffers(readFbo);
            glDeleteFramebuffers(drawFbo);
            glDeleteTextures(texture);
            MemoryUtil.memFree(source);
            MemoryUtil.memFree(output);
        }
    }

    private static void checkState(int readFbo, int drawFbo, int packBuffer) {
        check(glGetInteger(GL_READ_FRAMEBUFFER_BINDING) == readFbo, "Read FBO restored");
        check(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == drawFbo, "Draw FBO preserved");
        check(glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING) == packBuffer, "Pack buffer restored");
        check(glGetInteger(GL_PACK_ALIGNMENT) == 8, "Alignment restored");
        check(glGetInteger(GL_PACK_ROW_LENGTH) == 11, "Row length restored");
        check(glGetInteger(GL_PACK_SKIP_ROWS) == 2, "Skipped rows restored");
        check(glGetInteger(GL_PACK_SKIP_PIXELS) == 3, "Skipped pixels restored");
        check(glGetError() == GL_NO_ERROR, "No GL errors");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
