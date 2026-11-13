package org.agmas.noellesroles.game.roles.innocence.decree;

import java.util.Locale;

/** 固定律令词库；价格和持续时间只由服务端使用这份定义决定。 */
public enum Decree {
    MURDER(275, 140), MOVEMENT(300), SPEECH(100), TELEPORT(100), INVISIBILITY(150),
    ITEM_USE(200), INVENTORY(125), SKILL(140), MORPH(150), GUN(175), KNIFE(175),
    BLINDNESS(80), SPRINT(100), REVIVAL(140), HOTBAR(125), LIGHT(125);

    private final int cost;
    private final int durationTicks;

    Decree(int cost) { this(cost, 220); }
    Decree(int cost, int durationTicks) {
        this.cost = cost;
        this.durationTicks = durationTicks;
    }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public int cost() { return cost; }
    public int durationTicks() { return durationTicks; }
    public String translationKey() { return "decree.noellesroles." + id(); }
    public static Decree byId(String id) {
        for (Decree decree : values()) if (decree.id().equals(id)) return decree;
        return null;
    }

    /** 球形领域，包含边界，光明律令只对施法者本人豁免。 */
    public boolean contains(double distanceSquared, boolean isCaster) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0 && distanceSquared <= 225
                && !(this == LIGHT && isCaster);
    }
}
