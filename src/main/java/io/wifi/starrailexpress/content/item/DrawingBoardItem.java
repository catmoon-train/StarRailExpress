package io.wifi.starrailexpress.content.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * 画板物品
 * 右键打开 16x16 像素画布编辑器
 */
public class DrawingBoardItem extends Item {

    public static final int CANVAS_SIZE = 16;
    public static final int MAX_COLORS = 16;

    public DrawingBoardItem() {
        super(new Properties().stacksTo(4));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 无论服务端还是客户端都应该成功，防止物品被消耗
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static byte[][] getPixelData(ItemStack stack) {
        byte[][] pixels = new byte[CANVAS_SIZE][CANVAS_SIZE];
        // 初始化为白色 (1)
        for (int y = 0; y < CANVAS_SIZE; y++) {
            for (int x = 0; x < CANVAS_SIZE; x++) {
                pixels[y][x] = 1;  // 默认白色
            }
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("pixels")) {
                byte[] data = tag.getByteArray("pixels");
                for (int i = 0; i < Math.min(data.length, CANVAS_SIZE * CANVAS_SIZE); i++) {
                    pixels[i / CANVAS_SIZE][i % CANVAS_SIZE] = data[i];
                }
            }
        }
        return pixels;
    }

    public static void savePixelData(ItemStack stack, byte[][] pixels) {
        byte[] data = new byte[CANVAS_SIZE * CANVAS_SIZE];
        for (int y = 0; y < CANVAS_SIZE; y++) {
            for (int x = 0; x < CANVAS_SIZE; x++) {
                data[y * CANVAS_SIZE + x] = pixels[y][x];
            }
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag;
        if (customData != null) {
            tag = customData.copyTag();
        } else {
            tag = new CompoundTag();
        }
        tag.putByteArray("pixels", data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getSelectedColor(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            return tag.getInt("selectedColor");
        }
        return 0;
    }

    public static void setSelectedColor(ItemStack stack, int colorIndex) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag;
        if (customData != null) {
            tag = customData.copyTag();
        } else {
            tag = new CompoundTag();
        }
        tag.putInt("selectedColor", colorIndex);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
