import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/** Tiny in-memory activity feed shown in the status bar and the routing console log. */
final class ActivityLog {
    private static final List<String> entries = new ArrayList<String>();
    private static final List<Consumer<String>> listeners = new ArrayList<Consumer<String>>();

    private ActivityLog() { }

    static void add(String message) {
        String line = "[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + message;
        entries.add(line);
        for (Consumer<String> l : new ArrayList<Consumer<String>>(listeners)) l.accept(line);
    }

    static void addListener(Consumer<String> listener) { listeners.add(listener); }

    static List<String> entries() { return new ArrayList<String>(entries); }
}
