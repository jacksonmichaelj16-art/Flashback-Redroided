package com.whaltermc.mixin;

import com.moulberry.flashback.exporting.FlashbackFFmpegFrameRecorder;
import com.llamalad7.mixinextras.sugar.Local;
import com.whaltermc.EncoderBackpressure;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_rescale_ts;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame;
import static org.bytedeco.ffmpeg.global.avutil.AVERROR_EAGAIN;
import static org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NV12;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_set;

@Mixin(FlashbackFFmpegFrameRecorder.class)
public abstract class FlashbackFFmpegFrameRecorderMixin {

    @Shadow
    private AVCodec video_codec;

    @Shadow
    private AVCodecContext video_c;

    @Shadow
    private Map<String, String> videoOptions;

    @Shadow
    private AVPacket video_pkt;

    @Shadow
    private AVStream video_st;

    @Shadow
    private void writePacket(AVPacket packet) throws FlashbackFFmpegFrameRecorder.Exception {
        throw new AssertionError("Mixin shadow");
    }

    @Redirect(
            method = "recordImage",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/bytedeco/ffmpeg/global/avcodec;avcodec_send_frame(" +
                            "Lorg/bytedeco/ffmpeg/avcodec/AVCodecContext;" +
                            "Lorg/bytedeco/ffmpeg/avutil/AVFrame;)I"
            )
    )
    private int flashbackRedroided$sendVideoFrame(AVCodecContext context, AVFrame frame)
            throws FlashbackFFmpegFrameRecorder.Exception {
        // Retrying this native call keeps the original frame data and PTS intact.
        // recordImage advances the PTS only after the frame has been accepted.
        return EncoderBackpressure.sendFrame(
                () -> avcodec_send_frame(context, frame),
                () -> flashbackRedroided$drainVideoPacket(context), AVERROR_EAGAIN());
    }

    @Unique
    private boolean flashbackRedroided$drainVideoPacket(AVCodecContext context)
            throws FlashbackFFmpegFrameRecorder.Exception {
        av_packet_unref(video_pkt);
        try {
            int result = avcodec_receive_packet(context, video_pkt);
            if (result == AVERROR_EAGAIN() || result == AVERROR_EOF()) {
                return false;
            }
            if (result < 0) {
                throw new FlashbackFFmpegFrameRecorder.Exception(
                        "avcodec_receive_packet() error " + result + ": Error during video encoding.");
            }
            av_packet_rescale_ts(video_pkt, context.time_base(), video_st.time_base());
            video_pkt.stream_index(video_st.index());
            writePacket(video_pkt);
            return true;
        } finally {
            av_packet_unref(video_pkt);
        }
    }

    @Inject(
            method = "startUnsafe",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lorg/bytedeco/ffmpeg/global/avcodec;avcodec_open2(" +
                            "Lorg/bytedeco/ffmpeg/avcodec/AVCodecContext;" +
                            "Lorg/bytedeco/ffmpeg/avcodec/AVCodec;" +
                            "Lorg/bytedeco/ffmpeg/avutil/AVDictionary;)I"
            )
    )
    private void flashbackRedroided$configureMediaCodec(
            CallbackInfo ci,
            @Local AVDictionary options
    ) {
        if (video_codec == null || video_codec.name() == null) {
            return;
        }

        String codecName = video_codec.name().getString();

        if (!codecName.endsWith("_mediacodec")) {
            return;
        }

        if (options != null && videoOptions != null && !videoOptions.containsKey("ndk_codec")) {
            av_dict_set(options, "ndk_codec", "1", 0);
        }

        if (video_c != null) {
            video_c.pix_fmt(AV_PIX_FMT_NV12);
        }
    }

    @ModifyVariable(
            method = "recordImage",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2,
            order = 900
    )
    private int flashbackRedroided$useNv12(int pixelFormat) {
        if (pixelFormat != AV_PIX_FMT_YUV420P) {
            return pixelFormat;
        }

        if (video_codec == null || video_codec.name() == null) {
            return pixelFormat;
        }

        if (!video_codec.name().getString().endsWith("_mediacodec")) {
            return pixelFormat;
        }

        return AV_PIX_FMT_NV12;
    }

    @Inject(
            method = "recordImage",
            at = @At("HEAD"),
            order = 800
    )
    private void flashbackRedroided$convertYuv420pToNv12(
            int width,
            int height,
            int pixelFormat,
            ByteBuffer image,
            CallbackInfo ci
    ) {
        if (pixelFormat != AV_PIX_FMT_YUV420P) {
            return;
        }

        if (video_codec == null || video_codec.name() == null) {
            return;
        }

        if (!video_codec.name().getString().endsWith("_mediacodec")) {
            return;
        }

        int ySize = width * height;
        int chromaSize = ySize / 4;
        int requiredSize = ySize + chromaSize + chromaSize;

        ByteBuffer source = image.duplicate();
        source.clear();

        if (source.capacity() < requiredSize) {
            return;
        }

        byte[] u = new byte[chromaSize];
        byte[] v = new byte[chromaSize];

        source.position(ySize);
        source.get(u);

        source.position(ySize + chromaSize);
        source.get(v);

        ByteBuffer destination = image.duplicate();
        destination.clear();
        destination.position(ySize);

        for (int i = 0; i < chromaSize; i++) {
            destination.put(u[i]);
            destination.put(v[i]);
        }
    }
}
