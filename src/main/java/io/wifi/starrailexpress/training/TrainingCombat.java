package io.wifi.starrailexpress.training;

import io.wifi.starrailexpress.api.hit.HitType;
import io.wifi.starrailexpress.api.hit.SREHitManager;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.original.GunShootPayload;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** 假人只获得正常服务端攻击入口，不跳过职业、护盾、弹药或冷却规则。 */
public final class TrainingCombat {
    private TrainingCombat() { }
    public static boolean gun(ServerPlayer bot) {
        var target = target(bot, HitType.GUN, 20);
        if (target == null || !bot.getMainHandItem().is(TMMItemTags.GUNS)) return false;
        var role = SREGameWorldComponent.KEY.get(bot.level()).getRole(bot);
        if (role != null && !role.onUseGun(bot)) return false;
        var session = TrainingRules.session(bot);
        int hitRate = session == null ? 100 : session.settings().botHitRate;
        // 未命中仍走完整开枪流程（枪声、弹药、冷却、枪口特效），只是不结算对玩家的致死伤害。
        boolean hit = hitRate >= 100 || bot.getRandom().nextFloat() * 100 < hitRate;
        GunShootPayload.Receiver.shoot(bot, hit ? target.getId() : -1);
        return true;
    }
    public static boolean knife(ServerPlayer bot) {
        var target = target(bot, HitType.KNIFE, 3);
        if (target == null || !(bot.getMainHandItem().getItem() instanceof KnifeItem)) return false;
        var role = SREGameWorldComponent.KEY.get(bot.level()).getRole(bot);
        if (role != null && !role.onUseKnife(bot)) return false;
        KnifeStabPayload.Receiver.stab(bot, target.getId());
        return true;
    }
    private static ServerPlayer target(ServerPlayer bot, HitType type, double range) {
        var session = TrainingRules.session(bot);
        if (session == null || !session.owns(bot) || session.settings().paused || !session.authorized()
                || !TrainingRules.isAlive(bot) || bot.getCooldowns().isOnCooldown(bot.getMainHandItem().getItem())
                || bot.hasEffect(org.agmas.noellesroles.init.ModEffects.USED_BANED)
                || org.agmas.noellesroles.content.effects.StatusAilmentHandler.isIntellectLocked(bot)) return null;
        ServerPlayer owner = session.owner();
        if (!TrainingRules.isAlive(owner) || owner.serverLevel() != bot.serverLevel()
                || !session.area().box().contains(owner.position()) || bot.distanceToSqr(owner) > range * range) return null;
        HitResult hit = SREHitManager.getTarget(bot, type, range);
        return hit instanceof EntityHitResult entity && entity.getEntity() == owner && bot.hasLineOfSight(owner) ? owner : null;
    }
}
