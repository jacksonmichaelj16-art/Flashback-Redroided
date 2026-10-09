import com.whaltermc.EncoderBackpressure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone regression tests; no Minecraft or native Android runtime required. */
public class EncoderBackpressureTest {
    private static final int AGAIN = -11;

    public static void main(String[] args) throws Exception {
        checkImmediateResult(0);
        checkImmediateResult(-22);

        List<String> events = new ArrayList<>();
        AtomicInteger sends = new AtomicInteger();
        int result = EncoderBackpressure.sendFrame(() -> {
            events.add("send");
            return sends.incrementAndGet() < 3 ? AGAIN : 0;
        }, () -> {
            events.add("write pending packet");
            return true;
        }, AGAIN);
        check(result == 0, "Frame must eventually be accepted");
        check(events.equals(List.of("send", "write pending packet", "send",
                "write pending packet", "send")), "Drain output before each retry");

        sends.set(0);
        result = EncoderBackpressure.sendFrame(() -> {
            sends.incrementAndGet();
            return AGAIN;
        }, () -> false, AGAIN);
        check(result == AGAIN && sends.get() == 1, "No progress must fail without spinning or dropping a frame");

        sends.set(0);
        result = EncoderBackpressure.sendFrame(
                () -> sends.incrementAndGet() == 1 ? AGAIN : -22, () -> true, AGAIN);
        check(result == -22 && sends.get() == 2, "Preserve a fatal error after draining");

        IOException failure = new IOException("Packet write failed");
        sends.set(0);
        try {
            EncoderBackpressure.sendFrame(() -> {
                sends.incrementAndGet();
                return AGAIN;
            }, () -> { throw failure; }, AGAIN);
            throw new AssertionError("Drain errors must propagate");
        } catch (IOException actual) {
            check(actual == failure && sends.get() == 1, "Do not retry after a packet error");
        }
        System.out.println("Passed 6 encoder backpressure regression cases");
    }

    private static void checkImmediateResult(int expected) throws Exception {
        int result = EncoderBackpressure.sendFrame(() -> expected, () -> {
            throw new AssertionError("Only EAGAIN should drain output");
        }, AGAIN);
        check(result == expected, "Preserve immediate success/fatal errors");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
