package org.agmas.noellesroles.mixin.modifier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class VegetarianFoodMixin {

    @Inject(method = "finishUsingItem", at = @At("TAIL"))
    public void onVegetarianEat(ItemStack stack, net.minecraft.world.level.Level level, LivingEntity entity,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (player.serverLevel().isClientSide) return;
        
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.serverLevel());
        if (!modifiers.isModifier(player.getUUID(), TraitorAndModifiers.VEGETARIAN)) return;
        
        Item item = stack.getItem();
        if (isMeat(item)) {
            // 吃肉：黑暗 + 缓慢I
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, false));
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
    
    private boolean isMeat(Item item) {
        // 生肉
        if (item == Items.BEEF || item == Items.PORKCHOP || 
            item == Items.CHICKEN || item == Items.RABBIT || 
            item == Items.MUTTON || item == Items.COD || 
            item == Items.SALMON || item == Items.TROPICAL_FISH || 
            item == Items.PUFFERFISH) {
            return true;
        }
        // 熟肉
        if (item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP || 
            item == Items.COOKED_CHICKEN || item == Items.COOKED_RABBIT || 
            item == Items.COOKED_MUTTON || item == Items.COOKED_COD || 
            item == Items.COOKED_SALMON) {
            return true;
        }
        // 腐肉和蜘蛛眼
        if (item == Items.ROTTEN_FLESH || item == Items.SPIDER_EYE) {
            return true;
        }
        // 兔肉煲
        if (item == Items.RABBIT_STEW) {
            return true;
        }
        return false;
    }
}
