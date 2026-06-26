package org.agmas.noellesroles.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

import org.agmas.noellesroles.client.ClientAmonState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

/**
 * 阿蒙名字替换 Mixin：夺舍后，其他玩家看到的名牌显示为被夺舍宿主的名字。
 * 参考 {@link SkincrawlerRoleNameRendererMixin}。
 */
@Mixin(RoleNameRenderer.class)
public abstract class AmonRoleNameRendererMixin {

    private static Component amon$getDisplayName(PlayerInfo playerInfo) {
        MutableComponent mutableComponent = PlayerTeam.formatNameForTeam(playerInfo.getTeam(),
                Component.literal(playerInfo.getProfile().getName()));
        return mutableComponent;
    }

    @WrapOperation(method = "renderHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getDisplayName()Lnet/minecraft/network/chat/Component;"))
    private static Component renderAmonName(Player instance, Operation<Component> original) {
        if (instance == null) {
            return original.call(instance);
        }
        UUID disguiseTarget = ClientAmonState.disguiseTargetFor(instance.getUUID());
        if (disguiseTarget != null) {
            PlayerInfo targetInfo = ClientSkinCache.getCachedPlayerInfo(disguiseTarget);
            if (targetInfo != null && targetInfo.getProfile() != null && targetInfo.getProfile().getId() != null) {
                return amon$getDisplayName(targetInfo);
            }
            if (disguiseTarget.equals(Minecraft.getInstance().player.getUUID())) {
                return Minecraft.getInstance().player.getDisplayName();
            }
        }
        return original.call(instance);
    }
}
