package org.agmas.noellesroles.init.events;

import io.wifi.starrailexpress.event.ItemGameplayEvents;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.content.item.StalkerKnifeItem;
import org.agmas.noellesroles.init.ModItems;

/** 飞刀共冷却及跟踪者刀击反馈，替代对自有网络接收器的失效 INVOKE 注入。 */
public final class KnifeCooldownRoleEvents {
    private static boolean registered;

    private KnifeCooldownRoleEvents() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ItemGameplayEvents.BEFORE_KNIFE_COOLDOWN.register(KnifeCooldownRoleEvents::beforeCooldown);
    }

    private static void beforeCooldown(ServerPlayer player, int targetId) {
        player.getCooldowns().addCooldown(ModItems.THROWING_KNIFE, GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE));
        // 保留原两种刀的检查顺序、冷却减免和反馈，不以死亡成功与否改变触发时点。
        if (player.getMainHandItem().getItem() == ModItems.STALKER_KNIFE_OFFHAND) {
            player.getCooldowns().addCooldown(ModItems.STALKER_KNIFE_OFFHAND,
                    GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE) - 200);
        } else if (player.getMainHandItem().getItem() == ModItems.STALKER_KNIFE) {
            player.getCooldowns().addCooldown(ModItems.STALKER_KNIFE,
                    GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE) - 200);
        } else {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 15, 0));
        var level = player.serverLevel();
        StalkerKnifeItem.performDashOnHit(level, player, level.getEntity(targetId));
    }
}
