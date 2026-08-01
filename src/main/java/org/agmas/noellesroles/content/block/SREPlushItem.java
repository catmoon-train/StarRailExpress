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

package org.agmas.noellesroles.content.block;

import java.util.List;

import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.init.SREFumoBlocks;

import io.wifi.starrailexpress.client.util.SREClientUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class SREPlushItem extends BlockItem {
    protected Block block = null;

    public SREPlushItem(Block block, Properties properties) {
        super(block, properties);
        this.block = block;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        ItemStack stack = player.getItemInHand(interactionHand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
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

    @Override
    public Component getName(ItemStack itemStack) {
        if (itemStack.has(SREDataComponentTypes.TEXTURE)) {
            return Component.translatable("block.noellesroles.custom_player_plush.texture");
        } else if (itemStack.has(DataComponents.PROFILE)) {
            ResolvableProfile resolvableProfile = itemStack.get(DataComponents.PROFILE);
            var name = resolvableProfile.name().orElse(null);
            if (name != null && !name.isBlank()) {
                return Component.translatable("block.noellesroles.custom_player_plush.player", name);
            }
        }
        return super.getName(itemStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (stack.has(DataComponents.NOTE_BLOCK_SOUND)) {
            var sound = stack.get(DataComponents.NOTE_BLOCK_SOUND);
            if (sound != null) {
                Component subtitle = null;
                if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
                    subtitle = SREClientUtils.getSoundEventSubtitle(sound);
                }
                if (subtitle != null) {
                    tooltip.add(Component
                            .translatable("tooltip.sre.custom_player_plush.sound_with_translatable",
                                    subtitle, sound.toString())
                            .withStyle(ChatFormatting.GRAY));
                } else {
                    tooltip.add(Component.translatable("tooltip.sre.custom_player_plush.sound", sound.toString())
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }
}
