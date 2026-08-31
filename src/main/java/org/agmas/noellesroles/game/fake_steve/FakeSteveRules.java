package org.agmas.noellesroles.game.fake_steve;

/** Pure balance rules shared by the server director and tests. */
public final class FakeSteveRules {
    public static final int SAFE_SANITY = 70;
    public static final int CORPSE_RISK = 25;
    public static final int FACE_TO_FACE_TICKS = 5 * 20;
    public static final int ASSIMILATION_TICKS = 3 * 20;
    public static final long HUNT_ROOM_RECALL_INTERVAL_TICKS = 90L * 20L;

    private FakeSteveRules() {
    }

    public static int apparitionRisk(int sanity, int nearbyBodies) {
        int clampedSanity = Math.max(0, Math.min(100, sanity));
        int bodies = Math.max(0, nearbyBodies);
        return Math.max(0, SAFE_SANITY - clampedSanity) + CORPSE_RISK * bodies;
    }

    public static boolean hasWon(int livingFakeSteves, int livingPlayers) {
        return livingPlayers > 0 && livingFakeSteves >= 0
                && livingFakeSteves * 100 > livingPlayers * 60;
    }

    /** The 60% threshold starts the endgame hunt; it is no longer an instant win. */
    public static boolean shouldStartHunt(int livingFakeSteves, int livingPlayers) {
        return hasWon(livingFakeSteves, livingPlayers);
    }

    /** The faction wins the hunt only after no living human remains. */
    public static boolean shouldDeclareHuntVictory(int livingHumans) {
        return livingHumans == 0;
    }

    public static boolean shouldRecallHuntPlayers(long currentTick, long nextRecallTick) {
        return currentTick >= nextRecallTick;
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
