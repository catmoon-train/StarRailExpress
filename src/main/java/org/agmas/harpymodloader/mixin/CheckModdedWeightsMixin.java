package org.agmas.harpymodloader.mixin;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.ScoreboardRoleSelectorComponent;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modded_murder.ModdedWeights;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

@Mixin(ScoreboardRoleSelectorComponent.class)
public class CheckModdedWeightsMixin {
    @Inject(method = "checkWeights", at = @At("HEAD"), cancellable = true)
    public void a(CommandSourceStack source, CallbackInfo ci) {

        Harpymodloader.refreshRoles();

        HashMap<Role, Double> roleTotals = new HashMap<>();

        for (Role role : TMMRoles.ROLES.values()) {
            if (Harpymodloader.SPECIAL_ROLES.contains(role)) continue;
            for (ServerPlayer player : source.getLevel().players()) {
                if (!roleTotals.containsKey(role)) roleTotals.put(role, 0.0);
                double playerTotal = Math.exp((double) (-ModdedWeights.roleRounds.get(role).getOrDefault(player.getUUID(), 0) * 4));
                roleTotals.put(role, roleTotals.get(role) + playerTotal);
            }
        }

        MutableComponent text = Component.literal("Role Weights:").withStyle(ChatFormatting.GRAY);

        for(ServerPlayer player : source.getLevel().players()) {
            text = text.append("\n").append(player.getDisplayName());
            for (Role role : TMMRoles.ROLES.values()) {
                if (Harpymodloader.SPECIAL_ROLES.contains(role)) continue;
                Integer roleRounds = ModdedWeights.roleRounds.get(role).getOrDefault(player.getUUID(), 0);
                double roleWeight = Math.exp((-roleRounds * 4));
                double rolePercentage = roleWeight / roleTotals.getOrDefault(role,1.0) * (double) 100.0F;
                text.append(Component.literal("\n  ").append(role.identifier()+"").append(Component.literal(" (")).withColor(role.color()).append(Component.literal("%d".formatted(roleRounds)).withColor(8421504)).append(Component.literal("): ").withColor(role.color())).append(Component.literal("%.2f%%".formatted(rolePercentage)).withColor(8421504)));
            }
        }

        source.sendSystemMessage(text);
        ci.cancel();
    }
}
