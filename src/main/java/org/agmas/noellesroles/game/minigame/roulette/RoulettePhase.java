package org.agmas.noellesroles.game.minigame.roulette;

/** 时长与关键帧均以服务端 tick 为准，客户端仅做插值。 */
public enum RoulettePhase {
    WAITING(0, -1), INTRO(24, -1), RELOAD(48, -1), SUPPLY(40, -1), READY(0, -1),
    SHOOT(28, 9), ITEM(26, 13), ADRENALINE(42, 28), HANDOFF(12, -1), ENDING(24, -1);

    private final int duration;
    private final int impact;
    RoulettePhase(int duration, int impact) { this.duration = duration; this.impact = impact; }
    public int duration() { return duration; }
    public int impact() { return impact; }
    public String titleKey() { return "roulette.phase." + name().toLowerCase(java.util.Locale.ROOT); }
    public boolean blocksInput() { return this != WAITING && this != READY; }
    public static RoulettePhase byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : WAITING;
    }
}
