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

package org.agmas.noellesroles.mixin.roles.hunter;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 猎人专属：手持弓时自动获得力量 V 附魔。
 * 游侠（ELF）和其他 {@code canKillWithBowAndCrossbow} 职业不需要此效果。
 */
@Mixin(Player.class)
public class HunterBowMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void noellesroles$hunterBowPowerEnchant(CallbackInfo ci) {
        if (SRE.isLobby) return;
        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel serverLevel = serverPlayer.serverLevel();

        if (!SREGameWorldComponent.KEY.get(serverLevel).isRole(player.getUUID(), ModRoles.HUNTER)) return;

        ItemStack mainHandItem = player.getMainHandItem();
        if (!mainHandItem.is(Items.BOW)) return;

        boolean hasPower = false;
        for (var entry : mainHandItem.getEnchantments().entrySet()) {
            String enchantmentId = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
            if (enchantmentId.contains("minecraft:power")) {
                hasPower = true;
                if (entry.getValue() != 5) {
                    mainHandItem.remove(DataComponents.ENCHANTMENTS);
                    hasPower = false;
                }
                break;
            }
        }
        if (!hasPower) {
            mainHandItem.enchant(serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                    .filter(holder -> holder.is(Enchantments.POWER)).findFirst().get(), 5);
        }
    }
}
