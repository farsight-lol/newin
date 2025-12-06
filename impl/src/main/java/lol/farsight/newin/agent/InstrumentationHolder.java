package lol.farsight.newin.agent;

import org.jetbrains.annotations.NotNull;

import java.lang.instrument.Instrumentation;

public final class InstrumentationHolder {
    private InstrumentationHolder() {
        throw new UnsupportedOperationException();
    }

    private static Instrumentation cached = null;
    static volatile Instrumentation instrumentation;

    public static @NotNull Instrumentation get() {
        // we use the system classloader to load the class and then
        // get the instrumentation value, because the class loaded by
        // paper and the class loaded by the agent is the same, but it's
        // loaded by different classloaders so its treated differently.
        // the agent uses the system classloader so we do here as well

        if (cached != null)
            return cached;

        try {
            final var cl = ClassLoader.getSystemClassLoader()
                    .loadClass(InstrumentationHolder.class.getName());

            final var instrumentationField = cl.getDeclaredField("instrumentation");
            instrumentationField.setAccessible(true);

            cached = (Instrumentation) instrumentationField.get(null);

            return cached;
        } catch (final @NotNull ClassNotFoundException | @NotNull NoSuchFieldException | @NotNull IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}