package io.wifi.starrailexpress.rules;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 实体 / 玩家碰撞与推挤相关规则。
 * 从 {@link io.wifi.starrailexpress.SRE} 的静态列表中按类别剥离归一化而来。
 */
public class CollisionRules {
    /** 满足任一条件的玩家之间不可以碰撞。 */
    public static List<Predicate<Player>> cantCollide = new ArrayList<>();
    /** 满足任一条件的实体不会被推挤。 */
    public static List<Predicate<Entity>> cantPushableBy = new ArrayList<>();
    /** 满足任一条件的实体之间可以碰撞。 */
    public static List<Predicate<Entity>> canCollideEntity = new ArrayList<>();
}
