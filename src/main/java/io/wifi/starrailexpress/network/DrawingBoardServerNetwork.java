package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.content.item.DrawingBoardItem;
import io.wifi.starrailexpress.utils.ai.DrawingBoardRecognizer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 画板的服务端网络处理
 * 只在服务端加载，负责注册服务端处理器
 */
public class DrawingBoardServerNetwork {

    /**
     * 服务端初始化：注册处理器
     */
    public static void register() {
        // 注册 C2S 数据包类型
        PayloadTypeRegistry.playC2S().register(DrawingBoardPayload.DrawBoardSavePayload.TYPE, DrawingBoardPayload.DrawBoardSavePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DrawingBoardPayload.DrawBoardRecognizePayload.TYPE, DrawingBoardPayload.DrawBoardRecognizePayload.CODEC);

        // 服务端处理器：客户端保存画板数据
        ServerPlayNetworking.registerGlobalReceiver(DrawingBoardPayload.DrawBoardSavePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();

            // 找到玩家手中的画板
            ItemStack stack = findDrawingBoardInHands(player);
            if (stack.isEmpty()) return;

            // 保存像素数据
            byte[][] pixels = new byte[16][16];
            byte[] data = payload.pixels();
            for (int i = 0; i < 256; i++) {
                pixels[i / 16][i % 16] = data[i];
            }
            DrawingBoardItem.savePixelData(stack, pixels);
            DrawingBoardItem.setSelectedColor(stack, payload.selectedColor());
        });

        // 服务端处理器：客户端请求识别并消耗画板
        ServerPlayNetworking.registerGlobalReceiver(DrawingBoardPayload.DrawBoardRecognizePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();

            // 找到玩家手中的画板
            ItemStack stack = findDrawingBoardInHands(player);
            if (stack.isEmpty()) return;

            // 使用客户端发送的识别结果（包含保底机制）
            int category = payload.category();
            boolean recognized = category != DrawingBoardRecognizer.UNKNOWN;

            // 如果识别成功，消耗画板并给予对应物品
            if (recognized) {
                Item item = DrawingBoardRecognizer.getItemForCategory(category);
                if (item != null) {
                    ItemStack itemStack = new ItemStack(item);
                    // 非创造模式下，先检查快捷栏是否有空间
                    if (!player.getAbilities().instabuild) {
                        boolean hotbarFull = true;
                        for (int i = 0; i < 9; i++) {
                            if (player.getInventory().getItem(i).isEmpty()) {
                                hotbarFull = false;
                                break;
                            }
                        }
                        if (hotbarFull) {
                            // 快捷栏满了，不消耗画板也不给予物品
                            context.player().displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable(
                                            "starrailexpress.drawing_board.recognize.inventory_full"
                                    ),
                                    true // actionbar
                            );
                            return;
                        }
                        // 给予物品到快捷栏
                        player.getInventory().add(itemStack);
                        // 消耗画板
                        stack.shrink(1);
                    } else {
                        // 创造模式直接给予物品
                        player.getInventory().add(itemStack);
                    }
                    // actionbar 提示识别成功并给出物品
                    context.player().displayClientMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    "starrailexpress.drawing_board.recognize.success_with",
                                    itemStack.getDisplayName()
                            ),
                            true // actionbar
                    );
                }
            } else {
                // actionbar 提示识别失败
                context.player().displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "starrailexpress.drawing_board.recognize.fail"
                        ),
                        true // actionbar
                );
            }
        });
    }

    private static ItemStack findDrawingBoardInHands(ServerPlayer player) {
        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (mainHand.getItem() instanceof DrawingBoardItem) {
            return mainHand;
        }
        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (offHand.getItem() instanceof DrawingBoardItem) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }
}
