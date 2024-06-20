package com.mcserby.agent.moe;

public record Pair<T>(T first, T second) {
    public static <T> Pair<T> of(T first, T second) {
        return new Pair<>(first, second);
    }
}
