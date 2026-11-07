package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.client.gui.screen.AdminDrawingBoardScreen;
import io.wifi.starrailexpress.client.gui.screen.DrawingBoardScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.NoteScreen;
import io.wifi.starrailexpress.content.item.AdminDrawingBoardItem;
import io.wifi.starrailexpress.content.item.DrawingBoardItem;
import io.wifi.starrailexpress.content.item.NoteItem;
import io.wifi.starrailexpress.event.ItemGameplayEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

/** 基础物品界面的客户端监听器，不处理职业、状态或活动规则。 */
@Environment(EnvType.CLIENT)
public final class ItemClientActions {
    private ItemClientActions() {
    }

    public static void initialize() {
        ItemGameplayEvents.BEFORE_USE.register((item, level, player, hand) -> {
            if (item instanceof DrawingBoardItem && level.isClientSide) {
                var stack = player.getItemInHand(hand);
                Minecraft.getInstance().execute(() -> {
                    if (stack.getItem() instanceof AdminDrawingBoardItem) {
                        Minecraft.getInstance().setScreen(new AdminDrawingBoardScreen(stack));
                    } else {
                        Minecraft.getInstance().setScreen(new DrawingBoardScreen(stack));
                    }
                });
            }
            // 原客户端注入也处理集成服务器调用，不增加逻辑端检查。
            if (item instanceof NoteItem && player.isShiftKeyDown()) {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new NoteScreen()));
            }
        });
    }
}
