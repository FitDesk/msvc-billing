package com.msvcbilling.utils;

import java.util.function.Consumer;

public class NPEUtil {

    public static <T> boolean applyIfNotNull(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
            return true;
        }
        return false;
    }

    public static boolean applyIfNotBlank(String value, Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
            return true;
        }
        return false;
    }


}
