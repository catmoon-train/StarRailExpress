package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import java.util.Arrays;
import java.util.List;

/** 已学会的诡术才进入抽取池；不再区分主诡术与自动释放的小诡术。 */
public enum GhostArt {
    VEIL("veil", 1), PUSH("push", 1), SHROUD("shroud", 1),
    EFFIGY("effigy", 2), ECHO("echo", 2),
    WAIL("wail", 3), PARASITE("parasite", 3),
    SEIZE("seize", 4);

    public final String id;
    public final int stage;

    GhostArt(String id, int stage) { this.id = id; this.stage = stage; }

    public String nameKey() { return "hud.noellesroles.ma_chen_xu.skill." + id; }
    public static List<GhostArt> unlocked(int stage) {
        return Arrays.stream(values()).filter(art -> art.stage <= stage).toList();
    }

    /** 隐身每局只成功抽取一次；已学会列表不变，其余诡物仍可重复抽取。 */
    public static List<GhostArt> drawPool(int stage, boolean shroudDrawn) {
        return unlocked(stage).stream().filter(art -> art != SHROUD || !shroudDrawn).toList();
    }
}
