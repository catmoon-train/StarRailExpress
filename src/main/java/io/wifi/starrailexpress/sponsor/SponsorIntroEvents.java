package io.wifi.starrailexpress.sponsor;

import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.packet.OpenIntroPayload;

/**
 * 让带 {@link SREDataComponentTypes#SPONSOR_INTRO} 标记的赞助者 plush 右键时打开游戏介绍 GUI，
 * 而不是把方块放下。复用信封原本的 {@link OpenIntroPayload}（客户端据此打开 {@code RoleIntroduceScreen}）。
 */
public final class SponsorIntroEvents {
    private SponsorIntroEvents() {
    }

    public static void register() {
        // 右键方块：拦截放置，改为打开介绍 GUI
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (isSponsorIntro(stack)) {
                if (!world.isClientSide() && player instanceof ServerPlayer sp) {
                    ServerPlayNetworking.send(sp, new OpenIntroPayload());
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        // 右键空气：同样打开介绍 GUI
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (isSponsorIntro(stack)) {
                if (!world.isClientSide() && player instanceof ServerPlayer sp) {
                    ServerPlayNetworking.send(sp, new OpenIntroPayload());
                }
                return InteractionResultHolder.success(stack);
            }
            return InteractionResultHolder.pass(stack);
        });
    }

    private static boolean isSponsorIntro(ItemStack stack) {
        return stack.has(SREDataComponentTypes.SPONSOR_INTRO);
    }
}
