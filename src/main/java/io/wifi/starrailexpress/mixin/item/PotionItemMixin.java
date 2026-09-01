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

package io.wifi.starrailexpress.mixin.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.wifi.starrailexpress.SRE;

@Mixin(PotionItem.class)
public class PotionItemMixin {

    @Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
    public void finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity,
            CallbackInfoReturnable<ItemStack> cir) {
        if (SRE.isLobby) {
            return;
        }
        PotionItem self = (PotionItem) (Object) this;
        Player player = livingEntity instanceof Player ? (Player) livingEntity : null;
        if (player instanceof ServerPlayer sp) {
            CriteriaTriggers.CONSUME_ITEM.trigger(sp, itemStack);
        }

        if (!level.isClientSide) {
            PotionContents potionContents = (PotionContents) itemStack.getOrDefault(DataComponents.POTION_CONTENTS,
                    PotionContents.EMPTY);
            potionContents.forEachEffect((mobEffectInstance) -> {
                if (((MobEffect) mobEffectInstance.getEffect().value()).isInstantenous()) {
                    ((MobEffect) mobEffectInstance.getEffect().value()).applyInstantenousEffect(player, player,
                            livingEntity, mobEffectInstance.getAmplifier(), (double) 1.0F);
                } else {
                    livingEntity.addEffect(mobEffectInstance);
                }

            });
        }

        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(self));
            itemStack.consume(1, player);
        }

        livingEntity.gameEvent(GameEvent.DRINK);
        cir.setReturnValue(itemStack);
    }
}
