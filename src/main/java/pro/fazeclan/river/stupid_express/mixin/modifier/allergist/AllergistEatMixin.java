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

package pro.fazeclan.river.stupid_express.mixin.modifier.allergist;

import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(Player.class)
public abstract class AllergistEatMixin extends LivingEntity {

    protected AllergistEatMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = {
            "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;" }, at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V", shift = At.Shift.AFTER) })
    private void allergistConsume(@NotNull Level world, ItemStack stack, FoodProperties foodComponent,
            CallbackInfoReturnable<ItemStack> cir) {
        if (world.isClientSide)
            return;

        Player player = (Player) (Object) this;
    
        if (!RoleUtils.isPlayerTheModifier(player, SEModifiers.ALLERGIST))
            return;

        // Random effect: 33% nothing, 33% slowness 2 for 5s, 33% speed 2 for 2s, 1%
        // death
        double random = ThreadLocalRandom.current().nextDouble() * 100;

        if (random < 33) {
            // Nothing happens
            return;
        } else if (random < 66) {
            // Clear poison once
            SREPlayerPoisonComponent poisonComponent = SREPlayerPoisonComponent.KEY.get(player);
            if ((poisonComponent).getPoisonTicks() > 0) {
                poisonComponent.cure(null);
                player.displayClientMessage(
                        Component.translatable(
                                "hud.stupid_express.allergist.cure_poison")
                                .withColor(SEModifiers.ALLERGIST.color()),
                        true);
            } else {
                player.displayClientMessage(
                        Component.translatable(
                                "hud.stupid_express.allergist.no_poison")
                                .withColor(SEModifiers.ALLERGIST.color()),
                        true);
            }
        } else if (random < 99) {
            // Speed 2 for 10 seconds (200 ticks)
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,
                    200,
                    1, false, false));

            player.displayClientMessage(
                    Component.translatable(
                            "hud.stupid_express.allergist.speed_boost")
                            .withColor(SEModifiers.ALLERGIST.color()),
                    true);
        } else {
            // Death
            GameUtils.killPlayer(player, true, null, StupidExpress.id("allergist"));

            player.displayClientMessage(
                    Component.translatable(
                            "hud.stupid_express.allergist.death")
                            .withColor(SEModifiers.ALLERGIST.color()),
                    true);
        }
    }
}
