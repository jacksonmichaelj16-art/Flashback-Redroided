package com.whaltermc;

import java.util.function.IntSupplier;

/** Retries an unaccepted frame only after making progress on encoder output. */
public final class EncoderBackpressure {
    private EncoderBackpressure() {}

    @FunctionalInterface
    public interface PacketDrain<E extends Exception> {
        boolean drain() throws E;
    }

    public static <E extends Exception> int sendFrame(
            IntSupplier send, PacketDrain<E> drain, int again) throws E {
        int result = send.getAsInt();
        while (result == again) {
            // FFmpeg requires receiving output before retrying the same input.
            // If neither side can progress, preserve the error instead of spinning
            // forever or pretending that the frame was accepted.
            if (!drain.drain()) {
                return result;
            }
            result = send.getAsInt();
        }
        return result;
    }
}
