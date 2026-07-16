package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsRockets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * RPG：发射一枚<b>火箭投射物</b>（服务端模拟，火焰尾迹全员可见）——命中方块/玩家/怪物或飞满射程后
 * <b>范围爆炸</b>：半径内玩家扣枪伤走倒地路径（<b>包括自己</b>，小心近射！）、怪物即死。
 * 每发消耗 {@link SixtySecondsBalance#GUN_RPG_AMMO_COST} 发子弹；清晨/准备阶段禁 PvP 时爆炸不伤玩家。
 * 冷却与其它枪械全局共享（{@link SixtySecondsGunItem}）。弹道见 {@link SixtySecondsRockets}。
 */
public class SixtySecondsRpgItem extends SixtySecondsGunItem {

    public SixtySecondsRpgItem(Properties properties) {
        // 每发消耗 1 个火箭炮（专用弹药）
        super(properties, SixtySecondsBalance.GUN_RPG_COOLDOWN, SixtySecondsBalance.GUN_RPG_RANGE,
                SixtySecondsBalance.GUN_RPG_DAMAGE, 1, false,
                () -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ROCKET, 0, 0);
    }

    /** 不做即时命中：发射火箭投射物，命中与爆炸由 {@link SixtySecondsRockets} 逐 tick 结算。 */
    @Override
    protected void resolveHit(ServerPlayer shooter, ServerLevel level, Entity hit) {
        SixtySecondsRockets.fire(shooter);
    }
}
