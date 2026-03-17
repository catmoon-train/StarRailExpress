package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class H2SO4AcidItem extends Item {
    public H2SO4AcidItem(Properties properties) {
        super(properties);
    }

    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity,
            InteractionHand hand) {
        if (user.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        if (entity instanceof PlayerBodyEntity body) {
            body.discard();
            if (!user.level().isClientSide) {
                user.level().playSound(null, body.getX(), body.getY() + 0.10000000149011612, body.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F,
                        1.0F + user.level().random.nextFloat() * 0.1F - 0.05F);
            }

            if (!user.isCreative()) {
                if (SRE.REPLAY_MANAGER != null) {
                    SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                }
                user.getCooldowns().addCooldown(this, 60 * 20);
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }
}
