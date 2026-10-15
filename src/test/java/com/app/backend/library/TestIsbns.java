package com.app.backend.library;

import java.util.concurrent.ThreadLocalRandom;

public final class TestIsbns {

    private TestIsbns() {}

    public static String randomIsbn13() {
        StringBuilder digits = new StringBuilder("978");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 9; i++) {
            digits.append(random.nextInt(10));
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int value = digits.charAt(i) - '0';
            sum += i % 2 == 0 ? value : value * 3;
        }
        return digits.append((10 - sum % 10) % 10).toString();
    }
}
