package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.cca.PlayerAFKComponent;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

public class AFKEventHandler {

    /**
     * 注册所有AFK检测相关的事件处理器
     */
    public static void register() {
        // 当玩家使用物品时更新AFK计时器
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player != null) {
                PlayerAFKComponent afkComponent = PlayerAFKComponent.KEY.maybeGet(player).orElse(null);
                if (afkComponent != null) {
                    afkComponent.updateActivity();
                    // 调用角色的技能使用方法
                    io.wifi.starrailexpress.api.RoleMethodDispatcher.callOnItemUse(player, world, hand);
                }
            }
            return InteractionResultHolder.pass(ItemStack.EMPTY);
        });

        // 当玩家与方块交互时更新AFK计时器
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player != null) {
                PlayerAFKComponent afkComponent = PlayerAFKComponent.KEY.maybeGet(player).orElse(null);
                if (afkComponent != null) {
                    afkComponent.updateActivity();
                    return io.wifi.starrailexpress.api.RoleMethodDispatcher.callOnUseBlock(player, world, hand, hitResult);
                }
            }
            return InteractionResult.PASS;
        });

        // 当玩家与实体交互时更新AFK计时器
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player != null) {
                PlayerAFKComponent afkComponent = PlayerAFKComponent.KEY.maybeGet(player).orElse(null);
                if (afkComponent != null) {
                    afkComponent.updateActivity();
                }
            }
            return InteractionResult.PASS;
        });
    }
}