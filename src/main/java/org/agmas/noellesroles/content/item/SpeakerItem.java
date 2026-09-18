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

package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 便携音响。右键打开操作面板；开启后循环播放选中曲目，并在物品栏中显示为扛在肩上。
 */
public class SpeakerItem extends Item {
    public static final String TAG_PLAYING = "SpeakerPlaying";
    public static final String TAG_TRACK = "SpeakerTrack";
    public static final String TAG_VOLUME = "SpeakerVolume";
    public static final int DEFAULT_VOLUME = 100;

    /** 客户端设置，避免物品类直接引用 Screen。 */
    public static BiConsumer<ItemStack, InteractionHand> openScreenCallback = null;

    public SpeakerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() && openScreenCallback != null) {
            openScreenCallback.accept(stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.speaker.tooltip").withStyle(ChatFormatting.GRAY));
        if (isPlaying(stack)) {
            tooltip.add(Component.translatable("item.noellesroles.speaker.tooltip.playing").withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable("item.noellesroles.speaker.tooltip.volume", getVolume(stack))
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPlaying(stack) || super.isFoil(stack);
    }

    public static boolean isSpeaker(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.SPEAKER);
    }

    public static boolean isPlaying(ItemStack stack) {
        return isSpeaker(stack) && readTag(stack).getBoolean(TAG_PLAYING);
    }

    public static String getTrackId(ItemStack stack) {
        if (!isSpeaker(stack)) {
            return "";
        }
        return readTag(stack).getString(TAG_TRACK);
    }

    public static int getVolume(ItemStack stack) {
        if (!isSpeaker(stack)) {
            return DEFAULT_VOLUME;
        }
        CompoundTag tag = readTag(stack);
        if (!tag.contains(TAG_VOLUME)) {
            return DEFAULT_VOLUME;
        }
        return Mth.clamp(tag.getInt(TAG_VOLUME), 0, 100);
    }

    public static void writeState(ItemStack stack, String trackId, boolean playing) {
        writeState(stack, trackId, playing, getVolume(stack));
    }

    public static void writeState(ItemStack stack, String trackId, boolean playing, int volume) {
        if (!isSpeaker(stack)) {
            return;
        }
        CompoundTag tag = readTag(stack);
        tag.putString(TAG_TRACK, trackId == null ? "" : trackId);
        tag.putBoolean(TAG_PLAYING, playing);
        tag.putInt(TAG_VOLUME, Mth.clamp(volume, 0, 100));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static ItemStack findInInventory(Player player) {
        ItemStack playing = ItemStack.EMPTY;
        ItemStack first = ItemStack.EMPTY;
        ItemStack main = player.getMainHandItem();
        if (isSpeaker(main)) {
            if (isPlaying(main)) {
                return main;
            }
            first = main;
        }
        ItemStack off = player.getOffhandItem();
        if (isSpeaker(off)) {
            if (isPlaying(off)) {
                return off;
            }
            if (first.isEmpty()) {
                first = off;
            }
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isSpeaker(stack)) {
                continue;
            }
            if (first.isEmpty()) {
                first = stack;
            }
            if (isPlaying(stack)) {
                playing = stack;
                break;
            }
        }
        return playing.isEmpty() ? first : playing;
    }

    public static boolean hasPlayingSpeaker(Player player) {
        return isPlaying(findInInventory(player));
    }

    private static CompoundTag readTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
