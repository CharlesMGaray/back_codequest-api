package com.codequest.api.trilha;

final class AdaptiveMastery {

    private AdaptiveMastery() {
    }

    static int update(int current, boolean correct) {
        return (int) Math.round(current * 0.7 + (correct ? 100 : 0) * 0.3);
    }

    static String level(int score) {
        if (score < 60) {
            return "REVISAR";
        }
        return score < 80 ? "EM_DESENVOLVIMENTO" : "DOMINADA";
    }

    static int reviewIntervalDays(int score) {
        if (score < 60) {
            return 1;
        }
        return score < 80 ? 3 : 7;
    }
}
