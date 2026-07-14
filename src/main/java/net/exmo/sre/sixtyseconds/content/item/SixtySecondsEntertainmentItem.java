package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 娱乐物品（扑克/象棋/口琴/吉他/泰迪熊）：右键使用，给周围
 * {@link SixtySecondsBalance#ENTERTAINMENT_RADIUS} 格内的所有存活玩家（含自己）恢复理智。
 * 每种类型恢复量不同、耐久不同（注册时 {@code Properties.durability(n)} 给定），
 * 用一次掉 1 耐久，共享 {@link SixtySecondsBalance#ENTERTAINMENT_COOLDOWN_TICKS} 使用冷却。
 * 音色/文案按 {@link Kind} 区分。
 */
public class SixtySecondsEntertainmentItem extends Item {
    public enum Kind {
        POKER, CHESS, HARMONICA, GUITAR, TEDDY_BEAR
    }

    private final Kind kind;
    private final int sanityRestore;

    public SixtySecondsEntertainmentItem(Properties properties, Kind kind, int sanityRestore) {
        super(properties);
        this.kind = kind;
        this.sanityRestore = sanityRestore;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        SixtySecondsStatsComponent selfStats = SixtySecondsStatsComponent.KEY.get(serverPlayer);
        if (selfStats.monster || selfStats.downed) {
            return InteractionResultHolder.pass(stack);
        }
        // 周围所有存活玩家（含自己）恢复理智；怪物/倒地者无效
        double radiusSqr = SixtySecondsBalance.ENTERTAINMENT_RADIUS * SixtySecondsBalance.ENTERTAINMENT_RADIUS;
        int affected = 0;
        for (ServerPlayer other : serverLevel.players()) {
            if (other.distanceToSqr(serverPlayer) > radiusSqr
                    || io.wifi.starrailexpress.game.GameUtils.isPlayerEliminated(other)) {
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(other);
            if (stats.monster || stats.downed) {
                continue;
            }
            int before = stats.sanity;
            stats.sanity = Mth.clamp(stats.sanity + sanityRestore, 0, stats.sanityMax);
            if (stats.sanity != before) {
                stats.sync();
            }
            affected++;
            other.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.entertainment." + kind.name().toLowerCase(java.util.Locale.ROOT),
                    serverPlayer.getGameProfile().getName(), sanityRestore)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            serverLevel.sendParticles(ParticleTypes.HEART,
                    other.getX(), other.getY() + 2.1, other.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
        }
        serverLevel.playSound(null, serverPlayer.blockPosition(), sound(), SoundSource.PLAYERS, 0.8F, pitch());
        serverLevel.sendParticles(ParticleTypes.NOTE,
                serverPlayer.getX(), serverPlayer.getY() + 1.2, serverPlayer.getZ(), 6, 0.6, 0.4, 0.6, 0.0);
        serverPlayer.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.entertainment.used", affected, sanityRestore)
                .withStyle(ChatFormatting.GREEN), true);
        // 冷却 + 掉耐久（创造模式不消耗）
        serverPlayer.getCooldowns().addCooldown(this, SixtySecondsBalance.ENTERTAINMENT_COOLDOWN_TICKS);
        if (!serverPlayer.isCreative()) {
            stack.hurtAndBreak(1, serverPlayer, serverPlayer.getEquipmentSlotForItem(stack));
        }
        return InteractionResultHolder.success(stack);
    }

    private SoundEvent sound() {
        return switch (kind) {
            case POKER -> SoundEvents.BOOK_PAGE_TURN;
            case CHESS -> SoundEvents.NOTE_BLOCK_HARP.value();
            case HARMONICA -> SoundEvents.NOTE_BLOCK_FLUTE.value();
            case GUITAR -> SoundEvents.NOTE_BLOCK_GUITAR.value();
            case TEDDY_BEAR -> SoundEvents.WOOL_PLACE;
        };
    }

    private float pitch() {
        return switch (kind) {
            case HARMONICA -> 1.4F;
            case GUITAR -> 0.9F;
            default -> 1.0F;
        };
    }
}
