package org.agmas.noellesroles.client.utils;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.client.screen.DraftPaperScreen;
import org.agmas.noellesroles.client.screen.PhoneDialScreen;
import org.agmas.noellesroles.packet.PostmanC2SPacket;

/**
 * 物品使用的客户端 UI 辅助方法。
 * 仅由各 Item 通过 Class.forName 反射调用，避免服务端加载 client-only 类。
 */
public final class ClientItemHelper {

    private ClientItemHelper() {}

    /** 打开稿纸书写界面 */
    public static void openDraftPaperScreen(ItemStack stack, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new DraftPaperScreen(stack, hand));
    }

    /** 打开电话拨号界面 */
    public static void openPhoneScreen(ItemStack stack, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new PhoneDialScreen(stack, hand));
    }

    /** 处理传递盒右键逻辑（瞄准玩家则发包，否则提示） */
    public static void handleDeliveryBoxUse(Player user, ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        HitResult hitResult = client.hitResult;

        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hitResult;
            Entity target = entityHit.getEntity();

            if (target instanceof Player targetPlayer && !targetPlayer.equals(user)) {
                ClientPlayNetworking.send(new PostmanC2SPacket(
                        PostmanC2SPacket.Action.OPEN_DELIVERY,
                        targetPlayer.getUUID()
                ));
            }
        } else {
            user.displayClientMessage(
                    Component.translatable("message.noellesroles.postman.no_target")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    true
            );
        }
    }
}
