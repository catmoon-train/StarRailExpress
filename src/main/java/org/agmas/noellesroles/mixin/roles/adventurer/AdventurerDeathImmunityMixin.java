/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.mixin.roles.adventurer;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role_data.innocence.AdventurerRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * 拦截所有职业的 forceDeath 环境致死，对设置了 {@code environmentalImmunity=true} 的角色免疫。
 * 原仅作用于冒险家，现已泛化为通用能力。
 */
@Mixin(GameUtils.class)
public abstract class AdventurerDeathImmunityMixin {

    private static final int MESSAGE_COOLDOWN_TICKS = 70;
    private static final Map<java.util.UUID, Map<ResourceLocation, Integer>> playerMessageCooldowns = new HashMap<>();

    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;Z"
            + "Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/resources/ResourceLocation;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void noellesroles$environmentalDeathImmunity(
            Player victim, boolean spawnBody, Player _killer,
            ResourceLocation deathReason, boolean forceDeath, CallbackInfo ci) {
        if (!forceDeath) return;
        if (!AdventurerRoleData.isEnvironmentalDeath(deathReason)) return;
        if (!(victim instanceof ServerPlayer sp)) return;

        var gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (gameWorld == null) return;
        SRERole role = gameWorld.getRole(sp);
        if (role == null || !role.hasEnvironmentalImmunity()) return;

        // 节流提示信息
        var cooldowns = playerMessageCooldowns.computeIfAbsent(victim.getUUID(), k -> new HashMap<>());
        if (!cooldowns.containsKey(deathReason)) {
            cooldowns.put(deathReason, MESSAGE_COOLDOWN_TICKS);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.adventurer.immunity")
                            .withStyle(net.minecraft.ChatFormatting.GREEN), true);
        }
        // 减少所有冷却
        cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));
        cooldowns.values().removeIf(v -> v <= 0);

        ci.cancel();
    }
}
