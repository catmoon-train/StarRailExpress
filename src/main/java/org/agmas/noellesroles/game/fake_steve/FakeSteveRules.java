package org.agmas.noellesroles.game.fake_steve;

/** Pure balance rules shared by the server director and tests. */
public final class FakeSteveRules {
    public static final int SAFE_SANITY = 70;
    public static final int CORPSE_RISK = 25;
    public static final int FACE_TO_FACE_TICKS = 5 * 20;
    public static final int ASSIMILATION_TICKS = 3 * 20;

    private FakeSteveRules() {
    }

    public static int apparitionRisk(int sanity, int nearbyBodies) {
        int clampedSanity = Math.max(0, Math.min(100, sanity));
        int bodies = Math.max(0, nearbyBodies);
        return Math.max(0, SAFE_SANITY - clampedSanity) + CORPSE_RISK * bodies;
    }

    public static boolean hasWon(int livingFakeSteves, int startingPlayers) {
        return startingPlayers > 0 && livingFakeSteves >= 0
                && livingFakeSteves * 100 > startingPlayers * 40;
    }

    public static boolean canAssimilate(int nearbyLivingFakes, int otherLivingHumans,
            int uninterruptedTicks) {
        return nearbyLivingFakes >= 2 && otherLivingHumans == 0
                && uninterruptedTicks >= ASSIMILATION_TICKS;
    }

    public static boolean hasFaceToFaceCommunication(int uninterruptedTicks) {
        return uninterruptedTicks >= FACE_TO_FACE_TICKS;
    }
}
