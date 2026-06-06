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

import org.agmas.noellesroles.client.ClientSkincrawlerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

/**
 * 窃皮者名字替换 Mixin
 * <p>
 * 参考变形者 MorphlingRoleNameRendererMixin：
 * 当其他玩家的鼠标指针指向窃皮者时，显示被偷皮肤玩家的名字，
 * 而不是窃皮者自己的名字。
 */
@Mixin(RoleNameRenderer.class)
public abstract class SkincrawlerRoleNameRendererMixin {

    private static Component getDisplayName$PlayerInfo(PlayerInfo playerInfo) {
        MutableComponent mutableComponent = PlayerTeam.formatNameForTeam(playerInfo.getTeam(),
                Component.literal(playerInfo.getProfile().getName()));
        return (mutableComponent);
    }

    @WrapOperation(method = "renderHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getDisplayName()Lnet/minecraft/network/chat/Component;"))
    private static Component renderSkincrawlerName(Player instance, Operation<Component> original) {
        if (instance == null)
            return original.call(instance);

        // 窃皮者偷了皮肤后，显示被偷皮肤玩家的名字
        UUID stolenTarget = ClientSkincrawlerState.stolenSkinFor(instance.getUUID());
        if (stolenTarget != null) {
            PlayerInfo targetInfo = ClientSkinCache.getCachedPlayerInfo(stolenTarget);
            if (targetInfo != null && targetInfo.getProfile() != null && targetInfo.getProfile().getId() != null) {
                return getDisplayName$PlayerInfo(targetInfo);
            }
            if (stolenTarget.equals(Minecraft.getInstance().player.getUUID())) {
                return Minecraft.getInstance().player.getDisplayName();
            }
        }

        return original.call(instance);
    }

}
