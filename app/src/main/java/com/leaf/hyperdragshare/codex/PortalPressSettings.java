package com.leaf.hyperdragshare.codex;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;

/** Read-only adapter for the Portal 5.1.5 press mode exposed through Settings.System. */
public final class PortalPressSettings {
    public static final String KEY_SUPPORT_DOUBLE_PRESS_MODE = "support_double_press_mode";
    public static final String KEY_SUPPORT_LONG_PRESS_MODE = "support_long_press_mode";
    public static final int MODE_SINGLE_FINGER = 1;
    public static final int MODE_TWO_FINGER = 2;
    public static final String SOURCE_SYSTEM = "SYSTEM";
    public static final String SOURCE_MODULE = "MODULE";

    private PortalPressSettings() {}

    /** Returns an unavailable snapshot when either setting cannot be read or has an unknown value. */
    public static Snapshot read(Context context) {
        if (context == null) return Snapshot.unavailable();
        ContentResolver resolver = context.getContentResolver();
        try {
            return fromValues(
                    Settings.System.getInt(resolver, KEY_SUPPORT_DOUBLE_PRESS_MODE),
                    Settings.System.getInt(resolver, KEY_SUPPORT_LONG_PRESS_MODE));
        } catch (Settings.SettingNotFoundException | SecurityException | IllegalArgumentException error) {
            return Snapshot.unavailable();
        } catch (RuntimeException error) {
            return Snapshot.unavailable();
        }
    }

    static Snapshot read(Reader reader) {
        try {
            return fromValues(reader.getInt(KEY_SUPPORT_DOUBLE_PRESS_MODE),
                    reader.getInt(KEY_SUPPORT_LONG_PRESS_MODE));
        } catch (Settings.SettingNotFoundException | SecurityException | IllegalArgumentException error) {
            return Snapshot.unavailable();
        } catch (RuntimeException error) {
            return Snapshot.unavailable();
        }
    }

    private static Snapshot fromValues(int supportValue, int mode) {
        boolean supportsTwoFinger = supportValue != 0;
        if ((mode != MODE_SINGLE_FINGER && mode != MODE_TWO_FINGER)
                || (mode == MODE_TWO_FINGER && !supportsTwoFinger)) {
            return Snapshot.unavailable();
        }
        return new Snapshot(true, supportsTwoFinger, mode);
    }

    /** Observes both Portal mode values; close this when the owning screen/service stops. */
    public static Subscription observe(Context context, Handler handler, Runnable onChanged) {
        if (context == null || onChanged == null) return Subscription.noop();
        ContentResolver resolver = context.getContentResolver();
        Subscription observer = new Subscription(resolver, handler, onChanged);
        try {
            resolver.registerContentObserver(Settings.System.getUriFor(KEY_SUPPORT_DOUBLE_PRESS_MODE),
                    false, observer);
            resolver.registerContentObserver(Settings.System.getUriFor(KEY_SUPPORT_LONG_PRESS_MODE),
                    false, observer);
            return observer;
        } catch (RuntimeException error) {
            observer.close();
            return Subscription.noop();
        }
    }

    interface Reader {
        int getInt(String key) throws Settings.SettingNotFoundException;
    }

    public static final class Snapshot {
        public final boolean available;
        public final boolean supportsTwoFinger;
        /** Portal's selected mode, or 0 when unavailable. */
        public final int mode;
        public final boolean systemSelectedTwoFinger;
        /** Raw source ID for UI localization: SYSTEM or MODULE. */
        public final String sourceLabel;

        private Snapshot(boolean available, boolean supportsTwoFinger, int mode) {
            this.available = available;
            this.supportsTwoFinger = supportsTwoFinger;
            this.mode = mode;
            systemSelectedTwoFinger = available && mode == MODE_TWO_FINGER;
            sourceLabel = available ? SOURCE_SYSTEM : SOURCE_MODULE;
        }

        private static Snapshot unavailable() {
            return new Snapshot(false, false, 0);
        }

        public boolean resolveTwoFingerEnabled(boolean moduleFallback) {
            return available ? systemSelectedTwoFinger : moduleFallback;
        }

    }

    public static final class Subscription extends ContentObserver implements AutoCloseable {
        private final ContentResolver resolver;
        private final Runnable onChanged;
        private boolean closed;

        private Subscription(ContentResolver resolver, Handler handler, Runnable onChanged) {
            super(handler);
            this.resolver = resolver;
            this.onChanged = onChanged;
        }

        private static Subscription noop() {
            return new Subscription(null, null, null);
        }

        @Override public void onChange(boolean selfChange) {
            dispatchIfOpen();
        }

        @Override public void onChange(boolean selfChange, Uri uri) {
            dispatchIfOpen();
        }

        private void dispatchIfOpen() {
            synchronized (this) {
                if (!closed && onChanged != null) onChanged.run();
            }
        }

        @Override public synchronized void close() {
            if (closed) return;
            closed = true;
            if (resolver == null) return;
            try {
                resolver.unregisterContentObserver(this);
            } catch (RuntimeException ignored) {
                // The owning process may be stopping while the Settings resolver is going away.
            }
        }
    }
}
