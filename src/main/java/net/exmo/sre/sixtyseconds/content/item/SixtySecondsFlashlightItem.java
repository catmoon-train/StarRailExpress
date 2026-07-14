package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsWhisperSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 手电筒：
 * <ul>
 *   <li><b>被动</b>（手持）：夜间免疫低语怪掉 san + 获得夜视（见 {@code SixtySecondsWhisperSystem}）。</li>
 *   <li><b>主动</b>（右键）：用强光<b>驱散</b>周围
 *       {@link SixtySecondsBalance#FLASHLIGHT_DISPEL_RADIUS} 格内的低语怪，
 *       消耗 {@link SixtySecondsBalance#FLASHLIGHT_DISPEL_DURABILITY} 点耐久（电量），耗尽即损坏。</li>
 * </ul>
 * 附近没有低语怪时<b>不扣电量</b>——避免手持时误右键白白耗电。
 */
public class SixtySecondsFlashlightItem extends Item {

    public SixtySecondsFlashlightItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack); // 客户端：等服务端结算
        }
        int dispelled = SixtySecondsWhisperSystem.dispelNear(serverLevel, serverPlayer,
                SixtySecondsBalance.FLASHLIGHT_DISPEL_RADIUS);
        if (dispelled <= 0) {
            // 附近没有低语怪：不耗电、不进冷却
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.flashlight_no_whisper")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.pass(stack);
        }
        if (!serverPlayer.isCreative()) {
            stack.hurtAndBreak(SixtySecondsBalance.FLASHLIGHT_DISPEL_DURABILITY, serverPlayer,
                    LivingEntity.getSlotForHand(hand));
        }
        serverPlayer.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.flashlight_dispel", dispelled)
                .withStyle(ChatFormatting.AQUA), true);
        serverPlayer.getCooldowns().addCooldown(this, SixtySecondsBalance.FLASHLIGHT_DISPEL_COOLDOWN);
        return InteractionResultHolder.success(stack);
    }
}
