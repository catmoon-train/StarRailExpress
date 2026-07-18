package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsInventoryLimit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 背包扩容模块——右键使用解锁额外背包槽位。
 * <ul>
 *   <li>每个模块解锁 {@link #slots} 格（总上限 {@link SixtySecondsInventoryLimit#MAX_EXTRA_UNLOCK}）。</li>
 *   <li>超出上限时使用无效，不会消耗。</li>
 *   <li>仅 60s 模式游戏日期间生效；准备阶段不叠加扩容。</li>
 * </ul>
 */
public class SixtySecondsUnlockItem extends Item {

    private final int slots;

    public SixtySecondsUnlockItem(Properties properties, int slots) {
        super(properties);
        this.slots = slots;
    }

    public int getUnlockSlots() {
        return slots;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide || !(user instanceof ServerPlayer player)) {
            return InteractionResultHolder.consume(stack);
        }
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        int current = stats.extraUnlockedSlots;
        int after = current + slots;
        if (after > SixtySecondsInventoryLimit.MAX_EXTRA_UNLOCK) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.unlock_slots_max",
                    SixtySecondsInventoryLimit.MAX_EXTRA_UNLOCK), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_DIDGERIDOO, SoundSource.PLAYERS, 0.5F, 0.5F);
            return InteractionResultHolder.fail(stack);
        }
        stats.extraUnlockedSlots = after;
        stats.sync();
        stack.shrink(1);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.unlock_slots_success",
                slots, after, SixtySecondsInventoryLimit.MAX_EXTRA_UNLOCK), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.5F);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable(
                "item.noellesroles.sixty_seconds_unlock_slots.desc", slots));
        tooltipComponents.add(Component.translatable(
                "item.noellesroles.sixty_seconds_unlock_slots.max",
                SixtySecondsInventoryLimit.MAX_EXTRA_UNLOCK));
    }
}
