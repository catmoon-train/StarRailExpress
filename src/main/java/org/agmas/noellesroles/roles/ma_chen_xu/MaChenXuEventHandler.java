package org.agmas.noellesroles.roles.ma_chen_xu;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.MaChenXuPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;

/**
 * 马晨絮事件处理器
 */
public class MaChenXuEventHandler {

    /**
     * 注册事件监听器
     */
    public static void register() {
        // 监听玩家移动事件（通过使用物品来检测）
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                handlePlayerMovement(serverPlayer);
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });

        // 监听玩家破坏方块事件（也可以用来检测移动）
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                handlePlayerMovement(serverPlayer);
            }
            return true;
        });
    }

    /**
     * 处理玩家移动
     */
    private static void handlePlayerMovement(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;

        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent == null)
            return;

        if (!gameWorldComponent.isRole(player, ModRoles.MA_CHEN_XU))
            return;

        MaChenXuPlayerComponent component = MaChenXuPlayerComponent.KEY.get(player);
        if (component == null)
            return;

        // 检查玩家是否在奔跑
        if (player.isSprinting()) {
            component.chargeSwiftWind();
        }
    }
}