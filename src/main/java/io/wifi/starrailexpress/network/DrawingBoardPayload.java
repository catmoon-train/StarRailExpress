package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.item.DrawingBoardItem;
import io.wifi.starrailexpress.utils.ai.DrawingBoardRecognizer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 画板网络数据包
 * 只包含 C2S (客户端到服务端) 数据包
 */
public class DrawingBoardPayload {

    public static final String ID = "drawing_board";

    // C2S: 客户端保存画板数据
    public record DrawBoardSavePayload(int selectedColor, byte[] pixels) implements CustomPacketPayload {
        public static final Type<DrawBoardSavePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, ID + "_save"));
        public static final StreamCodec<FriendlyByteBuf, DrawBoardSavePayload> CODEC = StreamCodec.ofMember(
                DrawBoardSavePayload::encode,
                DrawBoardSavePayload::decode);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByte(selectedColor);
            buf.writeByteArray(pixels);
        }

        public static DrawBoardSavePayload decode(FriendlyByteBuf buf) {
            int color = buf.readByte();
            byte[] pixels = buf.readByteArray();
            return new DrawBoardSavePayload(color, pixels);
        }
    }

    // C2S: 客户端请求识别并消耗画板
    public record DrawBoardRecognizePayload() implements CustomPacketPayload {
        public static final Type<DrawBoardRecognizePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, ID + "_recognize"));
        public static final StreamCodec<FriendlyByteBuf, DrawBoardRecognizePayload> CODEC = StreamCodec.ofMember(
                DrawBoardRecognizePayload::encode,
                DrawBoardRecognizePayload::decode);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public void encode(FriendlyByteBuf buf) {
            // No data needed
        }

        public static DrawBoardRecognizePayload decode(FriendlyByteBuf buf) {
            return new DrawBoardRecognizePayload();
        }
    }

    public static void register() {
        // 注册 C2S 数据包类型
        PayloadTypeRegistry.playC2S().register(DrawBoardSavePayload.TYPE, DrawBoardSavePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DrawBoardRecognizePayload.TYPE, DrawBoardRecognizePayload.CODEC);

        // 服务端处理器：客户端保存画板数据
        ServerPlayNetworking.registerGlobalReceiver(DrawBoardSavePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            Level level = player.level();

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
        ServerPlayNetworking.registerGlobalReceiver(DrawBoardRecognizePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            Level level = player.level();

            // 找到玩家手中的画板
            ItemStack stack = findDrawingBoardInHands(player);
            if (stack.isEmpty()) return;

            // 获取像素数据
            byte[][] pixels = DrawingBoardItem.getPixelData(stack);

            // 进行识别
            int category = DrawingBoardRecognizer.getInstance().recognize(pixels);
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
