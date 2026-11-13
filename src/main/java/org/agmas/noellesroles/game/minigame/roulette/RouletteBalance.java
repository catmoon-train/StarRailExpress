package org.agmas.noellesroles.game.minigame.roulette;

/** 普通桌与恶魔专属局共用的初始生命和补给折算，不改变已有库存或待领取队列。 */
public final class RouletteBalance {
    public static final int HEALTH = 3;
    public static final int DEVIL_HEALTH = HEALTH + 1;
    private RouletteBalance() {}
    public static int supply(int requested) { return Math.max(0, (int) Math.round(requested * .7D)); }
}
