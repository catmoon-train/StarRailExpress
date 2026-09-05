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

package org.agmas.noellesroles.content.item.angler;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerItemTags;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 只清除原版 HARMFUL 效果。错误竿变异后改为施加随机原版负面。
 */
public class VanillaCleanseMilkItem extends Item {
    private static final Holder<MobEffect>[] VANILLA_HARMS = new Holder[] {
            MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.CONFUSION, MobEffects.BLINDNESS,
            MobEffects.HUNGER, MobEffects.WEAKNESS, MobEffects.POISON, MobEffects.WITHER, MobEffects.DARKNESS
    };

    public VanillaCleanseMilkItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
            @NotNull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return 32;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level,
            @NotNull LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player && GameUtils.isGameRunning(player)) {
            if (AnglerItemTags.isInverted(stack)) {
                Holder<MobEffect> harm = VANILLA_HARMS[player.getRandom().nextInt(VANILLA_HARMS.length)];
                player.addEffect(ModEffects.of(harm, 8 * 20, 0, false, true, true));
            } else {
                List<Holder<MobEffect>> toRemove = new ArrayList<>();
                for (MobEffectInstance instance : player.getActiveEffects()) {
                    Holder<MobEffect> holder = instance.getEffect();
                    if (holder.value().getCategory() != MobEffectCategory.HARMFUL) {
                        continue;
                    }
                    ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(holder.value());
                    if (id != null && "minecraft".equals(id.getNamespace())) {
                        toRemove.add(holder);
                    }
                }
                for (Holder<MobEffect> holder : toRemove) {
                    player.removeEffect(holder);
                }
            }
            player.playSound(SoundEvents.GENERIC_DRINK, 1f, 1f);
            stack.shrink(1);
        }
        return stack;
    }
}
