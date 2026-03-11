package org.agmas.noellesroles.item;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.packet.PostmanC2SPacket;

/**
 * 传递盒物品
 *
 * 功能：
 * - 邮差专属物品，在商店以350金币购买
 * - 指针对准玩家并右键使用，打开传递界面
 * - 双方可以放入一样物品并交换
 *
 * 注意：实际的使用逻辑在客户端的 DeliveryBoxItemClient 中通过 Mixin 实现
 */
public class DeliveryBoxItem extends Item {
    
    public DeliveryBoxItem(Properties settings) {
        super(settings);
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide) {

            // 客户端：检查是否瞄准玩家
            Minecraft client = Minecraft.getInstance();
            HitResult hitResult = client.hitResult;

            if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHit = (EntityHitResult) hitResult;
                Entity target = entityHit.getEntity();

                if (target instanceof Player targetPlayer && !targetPlayer.equals(user)) {
                    // 瞄准了其他玩家，发送打开传递的网络包
                    // 服务端会处理打开界面的逻辑
                    ClientPlayNetworking.send(new PostmanC2SPacket(
                            PostmanC2SPacket.Action.OPEN_DELIVERY,
                            targetPlayer.getUUID()
                    ));

                    return InteractionResultHolder.sidedSuccess(stack, true);
                }
            }

            // 没有瞄准玩家
            user.displayClientMessage(
                    Component.translatable("message.noellesroles.postman.no_target")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand), world.isClientSide());
    }
    
    @Override
    public boolean isFoil(ItemStack stack) {
        // 不添加附魔光效
        return false;
    }
}