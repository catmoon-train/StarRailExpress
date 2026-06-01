package org.agmas.noellesroles.mixin.client.roles.morphling;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.game.roles.killer.morphling.MorphlingPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.UUID;

@Mixin(RoleNameRenderer.class)
public abstract class MorphlingRoleNameRendererMixin {

    private static Component getDisplayName$PlayerInfo(PlayerInfo playerInfo) {
        MutableComponent mutableComponent = PlayerTeam.formatNameForTeam(playerInfo.getTeam(),
                Component.literal(playerInfo.getProfile().getName()));
        return (mutableComponent);
    }

    private static UUID getShuffledTarget(Player player) {
        var worldModifiers = WorldModifierComponent.KEY.get(player.level());
        if (worldModifiers != null && worldModifiers.isModifier(player, SEModifiers.JEB_)) {
            return NoellesrolesClient.JEB_SHUFFLED_PLAYER_ENTRIES_CACHE.get(player.getUUID());
        }
        if (SREClient.moodComponent == null) {
            return null;
        }
        if (!NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE.containsKey(player.getUUID())) {
            return null;
        }
        if ((ConfigWorldComponent.KEY.get(player.level())).insaneSeesMorphs
                && SREClient.moodComponent.isLowerThanDepressed()) {
            return NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE.get(player.getUUID());
        }
        return null;
    }

    @WrapOperation(method = "renderHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getDisplayName()Lnet/minecraft/network/chat/Component;"))
    private static Component renderRoleHud(Player instance, Operation<Component> original) {
        if (instance == null)
            return original.call(instance);
        if (getShuffledTarget(instance) != null) {
            return Component.literal("??!?!").withStyle(ChatFormatting.OBFUSCATED);
        }
        if (instance.isInvisible()) {
            return Component.literal("");
        }
        var mocca = MorphlingPlayerComponent.KEY.get(instance);
        if ((mocca).getMorphTicks() > 0) {
            PlayerInfo targetInfo = ClientSkinCache.getCachedPlayerInfo(mocca.disguise);
            if (targetInfo != null && targetInfo.getProfile() != null && targetInfo.getProfile().getId() != null) {
                return getDisplayName$PlayerInfo(targetInfo);
            } else {
                // Log.info(LogCategory.GENERAL, "Morphling disguise is null!!!");
            }
            if (mocca.disguise.equals(Minecraft.getInstance().player.getUUID())) {
                return Minecraft.getInstance().player.getDisplayName();
            }
        }
        return original.call(instance);
    }

}
