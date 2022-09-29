package com.neuromancer42.tea.core.project;

import com.neuromancer42.tea.core.bddbddb.RelSign;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Trgt is a value box with name and signatures
 *
 * @author Yifan Chen
 */

public class Trgt<T> {
    private final String name;
    private final TrgtInfo trgtInfo;
    protected T val;
    public Trgt(String name, TrgtInfo info) {
        this.name = name;
        this.trgtInfo = info;
    }

    public static <T> Trgt<T> createTrgt(String name, Class<T> type, String location) {
        TrgtInfo trgtInfo = new TrgtInfo(type, location);
        return new Trgt<>(name, trgtInfo);
    }

    public static Trgt<Object> createTrgt(String name, String location) {
        return createTrgt(name, Object.class, location);
    }


    public final T g() {
        return val;
    }

    public T get() {
        if (val == null) {
            Messages.fatal("Trgt %s: fetching trgt before setting it", name);
        }
        return val;
    }

    public void accept(T v) {
        if (val != null) {
            Messages.fatal("Trgt %s: multiple setting", name);
        }
        val = v;
    }

    public final Consumer<Object> consumer() {
        return v -> this.accept((T) v);
    }

    public final Supplier<Object> supplier() {
        return this::get;
    }

    public TrgtInfo getTrgtInfo() {
        return trgtInfo;
    }

    public String getName() {
        return name;
    }
}
