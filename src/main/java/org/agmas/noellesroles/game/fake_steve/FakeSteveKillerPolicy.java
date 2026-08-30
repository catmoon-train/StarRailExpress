package org.agmas.noellesroles.game.fake_steve;

import java.util.List;

/** Risk and loadout policy for possessed bodies whose original role can kill. */
public final class FakeSteveKillerPolicy {
    /** 0.1 seconds at Minecraft's 20 ticks per second. */
    public static final int PSYCHO_ATTACK_COOLDOWN_TICKS = 2;
    public enum Purchase {
        PSYCHO,
        BLACKOUT,
        KNIFE,
        GUN
    }

    private static final List<Purchase> PURCHASE_PRIORITY = List.of(
            Purchase.KNIFE, Purchase.PSYCHO, Purchase.BLACKOUT, Purchase.GUN);

    public static final double MIN_GUN_RANGE = 1.5D;
    public static final double MAX_GUN_RANGE = 24.0D;
    /** ~20 degrees of aiming error is tolerated before the shot is released. */
    public static final double GUN_AIM_COSINE = 0.94D;
    public static final double MELEE_RANGE_SQR = 9.0D;
    /** Strikes are opportunistic: the body never chases a target across the train. */
    public static final double STRIKE_RADIUS_SQR = 144.0D;

    private FakeSteveKillerPolicy() {
    }

    public static List<Purchase> purchasePriority() {
        return PURCHASE_PRIORITY;
    }

    /** A psycho body decides every tick; a disguised one keeps the human 5-tick cadence. */
    public static int decisionCadenceTicks(boolean psychoActive) {
        return psychoActive ? 1 : 5;
    }

    /** Carrying a Derringer is an explicit break from the normal disguise loop. */
    public static boolean entersDerringerBerserk(boolean carriesDerringer) {
        return carriesDerringer;
    }

    /** Psycho and a carried Derringer both make the body act without disguise delays. */
    public static boolean isBerserk(boolean psychoActive, boolean derringerBerserk) {
        return psychoActive || derringerBerserk;
    }

    /** Psycho never pays for witnesses or risk: the check is skipped entirely. */
    public static boolean ignoresRisk(boolean psychoActive) {
        return psychoActive;
    }

    public static boolean ignoresRisk(boolean psychoActive, boolean derringerBerserk) {
        return ignoresRisk(isBerserk(psychoActive, derringerBerserk));
    }

    /** No single behaviour may run forever, especially a held knife. */
    public static long modeBudgetTicks(AgentMode mode, boolean psychoActive) {
        if (mode == AgentMode.RECOVER || mode == AgentMode.DISGUISE_IDLE) {
            return Long.MAX_VALUE;
        }
        if (psychoActive) {
            return switch (mode) {
                case STARE -> 40L;
                case STALK -> 240L;
                default -> 200L;
            };
        }
        return switch (mode) {
            case STARE -> 160L;
            case STALK -> 300L;
            case ASSIMILATE -> 240L;
            case DISGUISE_TASK -> 600L;
            default -> 400L;
        };
    }

    public static boolean modeExpired(long now, long modeStartedTick, long budgetTicks) {
        return budgetTicks != Long.MAX_VALUE && now - modeStartedTick >= budgetTicks;
    }

    /** A charge that never converts into a strike is dropped so the body stops idling. */
    public static boolean knifeChargeExpired(long now, long chargeStartedTick) {
        return chargeStartedTick > 0L && now - chargeStartedTick >= 60L;
    }

    /** Weapons stay out of sight while any human can see the body. */
    public static boolean shouldConcealWeapon(boolean holdingWeapon, boolean exposed,
            boolean charging) {
        return holdingWeapon && exposed && !charging;
    }

    public static boolean shouldSkipTaskForStrike(boolean taskAvailable, boolean armed,
            boolean unwitnessed, double targetDistance) {
        return shouldInterruptTask(taskAvailable, armed, unwitnessed, targetDistance);
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

    /** A revolver is a ranged option: it must not be silently restricted to melee range. */
    public static boolean canFireGun(double distance, boolean visible, boolean unwitnessed) {
        return distance >= MIN_GUN_RANGE && distance <= MAX_GUN_RANGE && visible && unwitnessed;
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

    /** A body carrying both a knife and a gun chains kills without pausing. */
    static int recoveryTicksAfterKill(boolean psychoActive, boolean dualWield) {
        return psychoActive || dualWield ? 0 : 40;
    }

    static boolean canHuntThroughWitnesses(boolean psychoActive, boolean witnessed) {
        return psychoActive || !witnessed;
    }

    static boolean shouldPsychoInterruptTask(boolean psychoArmed, boolean targetPresent) {
        return psychoArmed && targetPresent;
    }

    static int psychoAttackCooldownTicks() {
        return PSYCHO_ATTACK_COOLDOWN_TICKS;
    }
}
