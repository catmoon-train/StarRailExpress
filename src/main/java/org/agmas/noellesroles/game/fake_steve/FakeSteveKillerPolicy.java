package org.agmas.noellesroles.game.fake_steve;

import java.util.List;

/** Risk and loadout policy for possessed bodies whose original role can kill. */
public final class FakeSteveKillerPolicy {
    public enum Purchase {
        PSYCHO,
        BLACKOUT,
        KNIFE,
        GUN
    }

    private static final List<Purchase> PURCHASE_PRIORITY = List.of(
            Purchase.KNIFE, Purchase.PSYCHO, Purchase.BLACKOUT, Purchase.GUN);

    private FakeSteveKillerPolicy() {
    }

    public static List<Purchase> purchasePriority() {
        return PURCHASE_PRIORITY;
    }

    public static boolean shouldUseSkill(boolean killer, boolean safeWindow, boolean targetPresent) {
        return killer && safeWindow && targetPresent;
    }

    public static boolean shouldInterruptTask(boolean taskAvailable, boolean armed,
            boolean unwitnessed, double targetDistance) {
        return taskAvailable && armed && unwitnessed && targetDistance <= 8.0D;
    }

    public static List<Purchase> crowdPurchasePlan(int nearbyHumans) {
        return nearbyHumans >= 2 ? List.of(Purchase.PSYCHO, Purchase.BLACKOUT) : List.of();
    }

    public static boolean canStrikeWithKnife(long now, long chargedAtTick) {
        return chargedAtTick > 0L && now >= chargedAtTick;
    }

    public static boolean shouldHolsterAfterKnifeKill(long now, long holsterAtTick) {
        return holsterAtTick > 0L && now >= holsterAtTick;
    }

    /** Killer-role possession only hunts ordinary, non-killer humans. */
    public static boolean canActivelyHunt(boolean targetIsImpostor, boolean targetIsKillerRole) {
        return !targetIsImpostor && !targetIsKillerRole;
    }

    static boolean canActivelyHunt(boolean targetIsImpostor, boolean targetIsKillerRole,
                                   boolean targetIsKillerNeutral) {
        return !targetIsImpostor && !targetIsKillerRole && !targetIsKillerNeutral;
    }

    static boolean countsAsHostileWitness(boolean impostor, boolean killerRole,
                                          boolean killerNeutral) {
        return !impostor && !killerRole && !killerNeutral;
    }

    static boolean shouldDropKillerRevolver(boolean originalKiller, boolean gunKill,
                                            boolean heldRevolver) {
        return originalKiller && gunKill && heldRevolver;
    }

    static int recoveryTicksAfterKill(boolean psychoActive) {
        return psychoActive ? 0 : 40;
    }

    static boolean canHuntThroughWitnesses(boolean psychoActive, boolean witnessed) {
        return psychoActive || !witnessed;
    }

    static boolean shouldPsychoInterruptTask(boolean psychoArmed, boolean targetPresent) {
        return psychoArmed && targetPresent;
    }
}
