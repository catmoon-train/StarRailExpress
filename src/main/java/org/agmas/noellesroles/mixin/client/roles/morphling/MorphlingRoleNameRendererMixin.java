package org.agmas.noellesroles.mixin.client.roles.morphling;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.roles.morphling.MorphlingPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.UUID;

@Mixin(RoleNameRenderer.class)
public abstract class MorphlingRoleNameRendererMixin {

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

        if (getShuffledTarget(instance) != null) {
            return Component.literal("??!?!").withStyle(ChatFormatting.OBFUSCATED);
        }
        if (instance.isInvisible()) {
            return Component.literal("");
        }
        var deathPenalty = ModComponents.DEATH_PENALTY.get(Minecraft.getInstance().player);
        boolean hasPenalty = false;
        if (deathPenalty != null)
            hasPenalty = deathPenalty.hasPenalty();
        // boolean hasPenalty =
        // ModComponents.DEATH_PENALTY.get(Minecraft.getInstance().player).hasPenalty();

        if (hasPenalty) {
            // return
            // Component.translatable("message.noellesroles.penalty.limit.player_name");
        }
        if ((MorphlingPlayerComponent.KEY.get(instance)).getMorphTicks() > 0) {
            if (instance.level().getPlayerByUUID(MorphlingPlayerComponent.KEY.get(instance).disguise) != null) {
                return instance.level().getPlayerByUUID((MorphlingPlayerComponent.KEY.get(instance)).disguise)
                        .getDisplayName();
            } else {
                Log.info(LogCategory.GENERAL, "Morphling disguise is null!!!");
            }
            if (MorphlingPlayerComponent.KEY.get(instance).disguise.equals(Minecraft.getInstance().player.getUUID())) {
                return Minecraft.getInstance().player.getDisplayName();
            }
        }
        return original.call(instance);
    }

}
