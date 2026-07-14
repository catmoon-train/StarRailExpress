package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.init.ModItems;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易淋浴器：右键洗澡——消耗背包里 1 瓶小瓶水，污染值 ×{@link SixtySecondsBalance#SHOWER_POLLUTION_MULT}
 * （-50%）。<b>每人每个游戏日只能洗一次</b>（按 dayNumber 记账，开局重置）。
 */
public class SixtySecondsShowerBlock extends Block {
    /** 玩家 uuid → 最近一次洗澡的游戏日。 */
    private static final Map<UUID, Integer> LAST_USED_DAY = new ConcurrentHashMap<>();
    private static final String LANG = "message.noellesroles.sixty_seconds.shower.";

    public SixtySecondsShowerBlock(Properties properties) {
        super(properties);
    }

    public static void reset() {
        LAST_USED_DAY.clear();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        shower(level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        shower(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private static void shower(Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !SixtySecondsMod.isActive(level)) {
            return;
        }
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(serverPlayer);
        if (stats.monster || stats.downed) {
            return;
        }
        Integer lastDay = LAST_USED_DAY.get(serverPlayer.getUUID());
        if (lastDay != null && lastDay == stats.dayNumber) {
            serverPlayer.displayClientMessage(Component.translatable(LANG + "already")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!consumeWater(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.translatable(LANG + "no_water")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        LAST_USED_DAY.put(serverPlayer.getUUID(), stats.dayNumber);
        int before = stats.pollution;
        stats.pollution = (int) Math.floor(stats.pollution * SixtySecondsBalance.SHOWER_POLLUTION_MULT);
        stats.sync();
        // 洒水演出：水花粒子 + 溅水声
        serverLevel.sendParticles(ParticleTypes.FALLING_WATER,
                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 24, 0.35, 0.3, 0.35, 0.0);
        serverLevel.sendParticles(ParticleTypes.SPLASH,
                serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(), 16, 0.3, 0.5, 0.3, 0.0);
        serverLevel.playSound(null, pos, SoundEvents.PLAYER_SPLASH, SoundSource.BLOCKS, 0.7F, 1.1F);
        serverPlayer.displayClientMessage(Component.translatable(LANG + "used", before, stats.pollution)
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** 从背包扣 1 瓶小瓶水（创造模式不消耗）；没有返回 false。 */
    private static boolean consumeWater(ServerPlayer player) {
        if (player.isCreative()) {
            return true;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.SIXTY_SECONDS_WATER_SMALL)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }
}
