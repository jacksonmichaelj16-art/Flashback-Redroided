import com.whaltermc.MinecraftScreenAccess;

public class MinecraftScreenAccessTest {
    public static final class Client261 {
        public Object screen;
    }

    public static final class Gui262 {
        private Object currentScreen;
        public Object screen() { return currentScreen; }
    }

    public static final class Client262 {
        public final Gui262 gui = new Gui262();
    }

    public static void main(String[] args) {
        Client261 older = new Client261();
        Client262 newer = new Client262();
        check(!MinecraftScreenAccess.hasScreen(older), "26.1 no screen");
        check(!MinecraftScreenAccess.hasScreen(newer), "26.2 no screen");
        older.screen = new Object();
        newer.gui.currentScreen = new Object();
        check(MinecraftScreenAccess.hasScreen(older), "26.1 menu opened");
        check(MinecraftScreenAccess.hasScreen(newer), "26.2 menu opened");
        older.screen = null;
        newer.gui.currentScreen = null;
        check(!MinecraftScreenAccess.hasScreen(older), "26.1 menu closed");
        check(!MinecraftScreenAccess.hasScreen(newer), "26.2 menu closed");
        try {
            MinecraftScreenAccess.hasScreen(new Object());
            throw new AssertionError("Unsupported screen layout must be reported");
        } catch (IllegalStateException expected) {
            check(expected.getCause() instanceof NoSuchFieldException, "Keep lookup failure cause");
        }
        System.out.println("Passed screen lookup and open/close transitions for both API layouts");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
