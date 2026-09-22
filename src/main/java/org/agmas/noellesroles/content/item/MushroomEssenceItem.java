package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.MushroomEssenceEntity;
import org.agmas.noellesroles.init.ModEntities;

public class MushroomEssenceItem extends Item {
    private final boolean poisonous;

    public MushroomEssenceItem(Properties properties, boolean poisonous) {
        super(properties);
        this.poisonous = poisonous;
    }

    public boolean isPoisonous() {
        return poisonous;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            MushroomEssenceEntity entity = new MushroomEssenceEntity(ModEntities.MUSHROOM_ESSENCE, level, poisonous);
            entity.setOwner(player);
            entity.setPosRaw(player.getX(), player.getEyeY() - 0.1, player.getZ());
            entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.5F, 1.0F);
            level.addFreshEntity(entity);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), TMMSounds.ITEM_GRENADE_THROW,
                SoundSource.NEUTRAL, 0.5F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
