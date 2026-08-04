package com.virgoagario.swagbench.forge;

final class GcSettler {
    private static final long SETTLE_MILLIS = 100L;

    private GcSettler() {
    }

    static void settleAfterWarmup() {
        System.gc();
        try {
            Thread.sleep(SETTLE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
