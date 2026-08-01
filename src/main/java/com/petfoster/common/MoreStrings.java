package com.petfoster.common;

public final class MoreStrings {

    private MoreStrings() {
    }

    public static String truncate(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        return str.length() <= maxLen ? str : str.substring(0, maxLen);
    }
}
