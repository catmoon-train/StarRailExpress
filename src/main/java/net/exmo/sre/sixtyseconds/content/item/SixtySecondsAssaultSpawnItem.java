package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.NotNull;

/**
 * 夜袭者召唤哨（弱 / 中等 / 强，见 {@link SixtySecondsDefenseSystem.AssaultTier}）：
 * 对着地面右键，在点击处生成一只对应强度的夜袭者——归属锚点门离生成点最近的队伍、照常寻路冲门；
 * 手动召唤的怪不随清晨消散、白天也持续行动（配合「晚上不自动刷新夜袭者」的默认开关做人工夜袭/测试）。
 * 仅 60s 模式进行中可用；非创造消耗 1 个。
 */
public class SixtySecondsAssaultSpawnItem extends Item {
    private final SixtySecondsDefenseSystem.AssaultTier tier;

    public SixtySecondsAssaultSpawnItem(Properties properties, SixtySecondsDefenseSystem.AssaultTier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!SixtySecondsMod.isActive(level)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.cmd_not_running"), true);
            return InteractionResult.FAIL;
        }
        BlockPos spawn = context.getClickedPos().relative(context.getClickedFace());
        if (!SixtySecondsDefenseSystem.spawnManualAssault(level, spawn, tier)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.assault_spawn_failed"), true);
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        level.playSound(null, spawn, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.8F);
        return InteractionResult.CONSUME;
    }
}
