package com.whaltermc;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FlashbackTransformer {

    private static final String FROM = "imgui/moulberry90/";
    private static final String TO = "imgui/moulberry92/";

    private static final String IMGUI = TO + "ImGui";
    private static final String KEY_SHIM = "com/whaltermc/ImGuiKeyShim";

    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final String SAFETY = "com/whaltermc/GlfwSafety";
    private static final String COMPAT = "com/whaltermc/GlfwCompat";

    private static final java.util.Set<String> COMPAT_CALLS = java.util.Set.of(
            "glfwGetWindowAttrib(JI)I",
            "glfwGetMouseButton(JI)I",
            "glfwGetInputMode(JI)I",
            "glfwGetCursorPos(J[D[D)V",
            "glfwGetCursorPos(JLjava/nio/DoubleBuffer;Ljava/nio/DoubleBuffer;)V"
    );

    private static final String FFMPEG_PKG = "org/bytedeco/ffmpeg/";
    private static final String FF_COMPAT = "com/whaltermc/FFmpegCompat";
    private static final String CTX = "org/bytedeco/ffmpeg/avcodec/AVCodecContext";
    private static final String FRAME = "org/bytedeco/ffmpeg/avutil/AVFrame";
    private static final String AVUTIL = "org/bytedeco/ffmpeg/global/avutil";
    private static final String SWR = "org/bytedeco/ffmpeg/global/swresample";
    private static final String SWR_CTX = "org/bytedeco/ffmpeg/swresample/SwrContext";

    private record FfRule(boolean virtual, String compatName) {}

    private static final java.util.Map<String, FfRule> FF_RULES = java.util.Map.ofEntries(
            java.util.Map.entry(CTX + ".channels(I)L" + CTX + ";", new FfRule(true, "ctxSetChannels")),
            java.util.Map.entry(CTX + ".channels()I", new FfRule(true, "ctxGetChannels")),
            java.util.Map.entry(CTX + ".channel_layout(J)L" + CTX + ";", new FfRule(true, "ctxSetLayout")),
            java.util.Map.entry(CTX + ".channel_layout()J", new FfRule(true, "ctxGetLayout")),
            java.util.Map.entry(FRAME + ".channels(I)L" + FRAME + ";", new FfRule(true, "frameSetChannels")),
            java.util.Map.entry(FRAME + ".channels()I", new FfRule(true, "frameGetChannels")),
            java.util.Map.entry(FRAME + ".channel_layout(J)L" + FRAME + ";", new FfRule(true, "frameSetLayout")),
            java.util.Map.entry(FRAME + ".channel_layout()J", new FfRule(true, "frameGetLayout")),
            java.util.Map.entry(AVUTIL + ".av_get_default_channel_layout(I)J", new FfRule(false, "defaultLayout")),
            java.util.Map.entry(AVUTIL + ".av_get_channel_layout_nb_channels(J)I", new FfRule(false, "nbChannels")),
            java.util.Map.entry(SWR + ".swr_alloc_set_opts(L" + SWR_CTX + ";JIIJIIILorg/bytedeco/javacpp/Pointer;)L" + SWR_CTX + ";",
                    new FfRule(false, "swrAllocSetOpts"))
    );

    private FlashbackTransformer() {}

    public static byte[] transform(byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            return classBytes;
        }

        boolean hasImGui = contains(classBytes, FROM);
        boolean hasGlfw = contains(classBytes, GLFW);
        boolean hasFfmpeg = contains(classBytes, FFMPEG_PKG);

        if (!hasImGui && !hasGlfw && !hasFfmpeg) {
            return classBytes;
        }

        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, 0);

        ClassVisitor chain = new GlfwGuard(writer);
        chain = new FfmpegRedirect(chain);
        chain = new KeyRedirect(chain);

        if (hasImGui) {
            chain = new ClassRemapper(chain, new Remapper() {
                @Override
                public String map(String internalName) {
                    if (internalName != null && internalName.startsWith(FROM)) {
                        return TO + internalName.substring(FROM.length());
                    }
                    return internalName;
                }

                @Override
                public Object mapValue(Object value) {
                    if (value instanceof String s
                            && (s.contains("imgui.moulberry90") || s.contains("imgui/moulberry90"))) {
                        return s.replace("imgui.moulberry90", "imgui.moulberry92")
                                .replace("imgui/moulberry90", "imgui/moulberry92");
                    }
                    return super.mapValue(value);
                }
            });
        }

        reader.accept(chain, 0);
        return writer.toByteArray();
    }

    private static final class KeyRedirect extends ClassVisitor {

        KeyRedirect(ClassVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv == null) return null;

            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String mName,
                                            String mDesc, boolean itf) {
                    if (opcode == Opcodes.INVOKESTATIC && IMGUI.equals(owner) && isKeyCall(mName, mDesc)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, KEY_SHIM, mName, mDesc, false);
                        return;
                    }
                    super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                }
            };
        }

        private static boolean isKeyCall(String name, String desc) {
            return switch (name) {
                case "isKeyDown", "isKeyReleased" -> desc.equals("(I)Z");
                case "isKeyPressed" -> desc.equals("(I)Z") || desc.equals("(IZ)Z");
                default -> false;
            };
        }
    }

    private static final class FfmpegRedirect extends ClassVisitor {

        FfmpegRedirect(ClassVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv == null) return null;

            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String mName,
                                            String mDesc, boolean itf) {
                    if (owner.startsWith(FFMPEG_PKG)
                            && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESTATIC)) {
                        FfRule rule = FF_RULES.get(owner + "." + mName + mDesc);
                        if (rule != null && rule.virtual() == (opcode == Opcodes.INVOKEVIRTUAL)) {
                            String newDesc = rule.virtual()
                                    ? "(L" + owner + ";" + mDesc.substring(1)
                                    : mDesc;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, FF_COMPAT,
                                    rule.compatName(), newDesc, false);
                            return;
                        }
                    }
                    super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                }
            };
        }
    }

    private record Stub(String glfwName, String desc, String stubName) {}

    private static final class GlfwGuard extends ClassVisitor {

        private final Map<String, Stub> stubs = new LinkedHashMap<>();
        private boolean isInterface;
        private String className;

        GlfwGuard(ClassVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.className = name;
            this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv == null) return null;

            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String mName,
                                            String mDesc, boolean itf) {
                    if (opcode == Opcodes.INVOKESTATIC && GLFW.equals(owner) && !itf) {
                        Stub stub = stubs.computeIfAbsent(mName + mDesc,
                                k -> new Stub(mName, mDesc,
                                        "flashback$redroided$glfw$" + mName + "$" + stubs.size()));
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, className,
                                stub.stubName(), stub.desc(), isInterface);
                        return;
                    }
                    super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                }
            };
        }

        @Override
        public void visitEnd() {
            for (Stub stub : stubs.values()) {
                emitStub(stub);
            }
            super.visitEnd();
        }

        private void emitStub(Stub stub) {
            Type[] args = Type.getArgumentTypes(stub.desc());
            Type ret = Type.getReturnType(stub.desc());

            MethodVisitor mv = super.visitMethod(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    stub.stubName(), stub.desc(), null, null);
            mv.visitCode();

            Label start = new Label();
            Label end = new Label();
            Label handler = new Label();
            mv.visitTryCatchBlock(start, end, handler, "java/lang/LinkageError");

            mv.visitLabel(start);
            int slot = 0;
            Object[] frameLocals = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                mv.visitVarInsn(args[i].getOpcode(Opcodes.ILOAD), slot);
                slot += args[i].getSize();
                frameLocals[i] = frameType(args[i]);
            }
            String target = COMPAT_CALLS.contains(stub.glfwName() + stub.desc()) ? COMPAT : GLFW;
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, target, stub.glfwName(), stub.desc(), false);
            mv.visitLabel(end);
            mv.visitInsn(returnOpcode(ret));

            mv.visitLabel(handler);
            mv.visitFrame(Opcodes.F_NEW, frameLocals.length, frameLocals,
                    1, new Object[]{"java/lang/LinkageError"});
            mv.visitVarInsn(Opcodes.ASTORE, slot);
            mv.visitLdcInsn(stub.glfwName());
            mv.visitVarInsn(Opcodes.ALOAD, slot);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SAFETY, "missing",
                    "(Ljava/lang/String;Ljava/lang/Throwable;)V", false);
            pushZero(mv, ret);
            mv.visitInsn(returnOpcode(ret));

            mv.visitMaxs(slot + 2, slot + 1);
            mv.visitEnd();
        }

        private static Object frameType(Type t) {
            return switch (t.getSort()) {
                case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> Opcodes.INTEGER;
                case Type.FLOAT -> Opcodes.FLOAT;
                case Type.LONG -> Opcodes.LONG;
                case Type.DOUBLE -> Opcodes.DOUBLE;
                case Type.ARRAY -> t.getDescriptor();
                default -> t.getInternalName();
            };
        }

        private static int returnOpcode(Type ret) {
            return ret.getSort() == Type.VOID ? Opcodes.RETURN : ret.getOpcode(Opcodes.IRETURN);
        }

        private static void pushZero(MethodVisitor mv, Type ret) {
            switch (ret.getSort()) {
                case Type.VOID -> { }
                case Type.LONG -> mv.visitInsn(Opcodes.LCONST_0);
                case Type.FLOAT -> mv.visitInsn(Opcodes.FCONST_0);
                case Type.DOUBLE -> mv.visitInsn(Opcodes.DCONST_0);
                case Type.OBJECT, Type.ARRAY -> mv.visitInsn(Opcodes.ACONST_NULL);
                default -> mv.visitInsn(Opcodes.ICONST_0);
            }
        }
    }

    private static boolean contains(byte[] data, String ascii) {
        byte[] needle = ascii.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
