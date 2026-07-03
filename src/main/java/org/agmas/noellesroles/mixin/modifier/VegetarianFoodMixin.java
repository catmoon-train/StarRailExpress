package org.agmas.noellesroles.mixin.modifier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 素食主义者效果：
 * - 吃肉：黑暗 + 缓慢I
 * - 吃非肉类：速度I + 跳跃提升I
 */
@Mixin(Player.class)
public class VegetarianFoodMixin {

    @Inject(
            method = {"eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V",
                    shift = At.Shift.AFTER
            )}
    )
    private void onVegetarianEat(Level level, ItemStack stack, FoodProperties foodProperties, CallbackInfoReturnable<ItemStack> cir) {
        if (level.isClientSide) return;
        
        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer)) return;
        
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
        if (!modifiers.isModifier(player.getUUID(), TraitorAndModifiers.VEGETARIAN)) return;
        
        if (TraitorAndModifiers.isMeat(stack)) {
            // 吃肉：黑暗等级5 + 缓慢I
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 5, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, false));
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("modifier.noellesroles.vegetarian.meat_effect"), true);
        } else {
            // 吃非肉类：速度I + 跳跃提升I
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 0, false, false, false));
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("modifier.noellesroles.vegetarian.plant_effect"), true);
        }
    }
}
