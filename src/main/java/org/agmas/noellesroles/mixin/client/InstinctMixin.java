package org.agmas.noellesroles.mixin.client;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.OnGetInstinctHighlight;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.game.roles.special.better_vigilante.BetterVigilantePlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SREClient.class)
public abstract class InstinctMixin {

    @Shadow
    public static KeyMapping instinctKeybind;

    @Inject(method = "isInstinctEnabled", at = @At("HEAD"), cancellable = true)
    private static void b(CallbackInfoReturnable<Boolean> cir) {
        Player player = Minecraft.getInstance().player;
        if (player == null)
            return;

        // 检查玩家是否正在被操纵师控制 - 如果是，禁止使用杀手本能
        if (noellesroles$isPlayerBeingControlled(player)) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent.isRole(player, ModRoles.BETTER_VIGILANTE)) {
            var betterC = BetterVigilantePlayerComponent.KEY.get(player);
            if (betterC.lastStandActivated) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    /**
     * 鬼祟效果：当目标玩家8格范围内时，禁用杀手直觉高亮
     */
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void onInit(CallbackInfo cir) {
        // 注册直觉高亮事件监听器
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!(target instanceof Player targetPlayer)) {
                return -1; // 不改变
            }
            
            Player localPlayer = Minecraft.getInstance().player;
            if (localPlayer == null || localPlayer.level().isClientSide) {
                return -1;
            }
            
            // 如果当前玩家是杀手，检查目标玩家是否有鬼祟修饰符
            if (SREClient.isKiller()) {
                // 获取世界组件
                SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(localPlayer.level());
                if (gameWorld != null) {
                    try {
                        // 获取目标的修饰符组件
                        var modifiers = WorldModifierComponent.KEY.get(targetPlayer.level());
                        if (modifiers != null && modifiers.isModifier(targetPlayer.getUUID(), TraitorAndModifiers.SNEAKY)) {
                            // 检查目标是否在当前杀手8格范围内
                            double dist = localPlayer.distanceTo(targetPlayer);
                            if (dist <= 8.0) {
                                // 鬼祟生效：禁用直觉高亮
                                return -2;
                            }
                        }
                    } catch (Exception e) {
                        // 静默处理错误
                    }
                }
            }
            
            return -1; // 不改变
        });
    }

    /**
     * 检查玩家是否正在被操纵师控制
     */
    @Unique
    private static boolean noellesroles$isPlayerBeingControlled(Player player) {
        if (player == null)
            return false;

        // 遍历所有玩家，检查是否有操纵师正在控制当前玩家
        for (Player otherPlayer : player.level().players()) {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(otherPlayer.level());
            if (gameWorldComponent.isRole(otherPlayer, ModRoles.MANIPULATOR)) {
                ManipulatorPlayerComponent manipulatorComponent = ManipulatorPlayerComponent.KEY.get(otherPlayer);
                if (manipulatorComponent.isControlling &&
                        manipulatorComponent.target != null &&
                        manipulatorComponent.target.equals(player.getUUID())) {
                    return true;
                }
            }
        }
        return false;
    }
}
