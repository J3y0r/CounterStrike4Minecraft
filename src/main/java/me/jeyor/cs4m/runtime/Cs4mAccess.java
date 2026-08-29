package me.jeyor.cs4m.runtime;

import java.util.function.Supplier;

public final class Cs4mAccess {
    private static Supplier<Cs4mServer> runtime = () -> null;

    private Cs4mAccess() {
    }

    public static void bind(Supplier<Cs4mServer> supplier) {
        runtime = supplier;
    }

    public static Cs4mServer runtime() {
        return runtime.get();
    }
}
