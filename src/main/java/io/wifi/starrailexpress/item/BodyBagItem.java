package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
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

public class BodyBagItem extends Item {
    public BodyBagItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        if (entity instanceof PlayerBodyEntity body) {
            body.discard();
            if (!user.level().isClientSide) {
                user.level().playSound(null, body.getX(), body.getY() + .1f, body.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.5f, 1f + user.level().random.nextFloat() * .1f - .05f);
            }
            if (!user.isCreative()) {
                if (SRE.REPLAY_MANAGER != null) {
                    SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                }
                user.getItemInHand(hand).shrink(1);
                user.getCooldowns().addCooldown(this, GameConstants.ITEM_COOLDOWNS.get(this));
            }

            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
