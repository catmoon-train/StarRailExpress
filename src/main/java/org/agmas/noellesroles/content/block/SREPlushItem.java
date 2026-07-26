package org.agmas.noellesroles.content.block;

import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.init.SREFumoBlocks;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class SREPlushItem extends BlockItem {

    public SREPlushItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        ItemStack stack = player.getItemInHand(interactionHand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        ResourceLocation sound = NRSounds.BAKA_BAKA.getLocation();
        if (stack.has(DataComponents.NOTE_BLOCK_SOUND)) {
            sound = stack.get(DataComponents.NOTE_BLOCK_SOUND);
        }

        if (stack.is(SREFumoBlocks.MILK_DRAGON_PLUSH_ITEM)) {
            sound = NRSounds.WO_SHI_NAI_LONG.getLocation();
        }
        level.playSound(null, player.blockPosition(), SoundEvent.createVariableRangeEvent(sound), SoundSource.BLOCKS);
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.success(stack);
    }
}
