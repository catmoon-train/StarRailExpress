package org.agmas.noellesroles.modifier;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.modifier.expedition.ExpeditionComponent;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.Color;

/**
 * NoellesRoles 修饰符注册类
 */
public class NRModifiers {

    /** 远征队修饰符 */
    public static Modifier EXPEDITION = HMLModifiers.registerModifier(new Modifier(
            Noellesroles.id("expedition"),
            new Color(210, 180, 140).getRGB(), // 棕色 - 代表远征
            null,
            null,
            false,
            false));

    /**
     * 初始化修饰符系统
     */
    public static void init() {
        EXPEDITION.civilianOnly = true;
        assignModifierComponents();
    }

    /**
     * 分配修饰符组件
     */
    public static void assignModifierComponents() {
        // 远征队修饰符分配事件
        ModifierAssigned.EVENT.register((player, modifier) -> {
            if (!modifier.equals(EXPEDITION)) {
                return;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            var level = serverPlayer.serverLevel();
            var gameWorld = SREGameWorldComponent.KEY.get(level);

            // 检查玩家是否是好人阵营（包括平民和警长阵营）
            // 并且不能是杀手阵营或中立阵营
            SRERole role = gameWorld.getRole(player);
            if (role != null && role.isInnocent() && !role.canUseKiller() && !role.isNeutrals()) {
                // 只排除小透明
                if (!gameWorld.isRole(player, ModRoles.GHOST)) {
                    // 给玩家分配远征队组件
                    var expeditionComponent = ExpeditionComponent.KEY.get(player);
                    expeditionComponent.sync();

                    Noellesroles.LOGGER.info("Expedition modifier assigned to player: " + player.getName().getString());
                }
            } else {
                Noellesroles.LOGGER.info("Expedition modifier not assigned to killer/neutral: " + player.getName().getString());
            }
        });

        // // 角色分配事件 - 检查是否需要移除远征队修饰符
        // ModdedRoleAssigned.EVENT.register((player, role) -> {
        //     if (!(player instanceof ServerPlayer)) {
        //         return;
        //     }

        //     // 检查玩家是否有远征队修饰符
        //     var expeditionComponent = ExpeditionComponent.KEY.get(player);
        //     if (expeditionComponent != null && expeditionComponent.isExpedition()) {
        //         // 检查新角色是否是好人阵营
        //         // 如果不是好人阵营（是杀手或中立），则清除远征队组件
        //         if (role != null && (!role.isInnocent() || role.canUseKiller() || role.isNeutrals())) {
        //             // 清除远征队组件
        //             expeditionComponent.clear();
        //             expeditionComponent.sync();

        //             // 注意：由于 Harpymodloader 的修饰符系统限制，我们只能清除组件功能
        //             // 修饰符本身仍然保留在系统中，但不会生效
        //             // 这是为了防止某些角色（如赌徒、慕恋者）变成杀手后仍保留远征队能力

        //             Noellesroles.LOGGER.info("Expedition modifier effect disabled for player due to role change: " + player.getName().getString() + ", new role: " + role.identifier());
        //         }
        //     }
        // });

        // 远征队修饰符移除事件
        ModifierRemoved.EVENT.register((player, modifier) -> {
            if (modifier.equals(EXPEDITION)) {
                var expeditionComponent = ExpeditionComponent.KEY.get(player);
                if (expeditionComponent != null) {
                    expeditionComponent.clear();
                    expeditionComponent.sync();
                }
            }
        });

        // 玩家重置事件 - 清理远征队修饰符组件
        ResetPlayerEvent.EVENT.register(player -> {
            try {
                var expeditionComponent = ExpeditionComponent.KEY.get(player);
                if (expeditionComponent != null) {
                    expeditionComponent.clear();
                    expeditionComponent.sync();
                }
            } catch (Exception e) {
                // 玩家可能没有 expedition 组件，忽略错误
            }
        });
    }
}
