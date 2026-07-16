package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsAirdrop;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 信号枪（仅野外可搜到，一次性）：<b>朝天空开枪</b>——需要头顶露天。发射后向<b>全服所有玩家</b>
 * 广播信号弹位置，并在 {@link #DELAY_TICKS} 后在发射点召唤一次空投（{@link SixtySecondsAirdrop#drop}）。
 * 谁都看得见、谁都能去抢——用不用要想清楚。
 */
public class SixtySecondsFlareGunItem extends Item {

    /** 信号弹升空 → 空投抵达的延迟（60 秒）。 */
    public static final int DELAY_TICKS = 20 * 60;

    /** 待落地的空投（服务端）：{触发 gameTime, x, z}。 */
    private static final List<long[]> PENDING = new ArrayList<>();

    public SixtySecondsFlareGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.consume(stack);
        }
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        ServerLevel serverLevel = serverPlayer.serverLevel();
        // 必须朝天开枪：头顶要露天
        BlockPos pos = serverPlayer.blockPosition();
        if (!serverLevel.canSeeSky(pos.above())) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.flare_need_sky"), true);
            return InteractionResultHolder.fail(stack);
        }
        // 升空表现 + 全服广播
        serverLevel.playSound(null, serverPlayer.getX(), serverPlayer.getEyeY(), serverPlayer.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2.0F, 0.8F);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                serverPlayer.getX(), serverPlayer.getY() + 2, serverPlayer.getZ(), 40, 0.3, 6.0, 0.3, 0.05);
        Component msg = Component.translatable("message.noellesroles.sixty_seconds.flare_fired",
                pos.getX(), pos.getZ()).withStyle(ChatFormatting.RED);
        for (ServerPlayer other : serverLevel.players()) {
            other.displayClientMessage(msg, false);
            other.playNotifySound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        PENDING.add(new long[]{serverLevel.getGameTime() + DELAY_TICKS, pos.getX(), pos.getZ()});
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    /** 服务端逐 tick：到时召唤空投（由 SixtySecondsMod 的 END_WORLD_TICK 驱动）。 */
    public static void tick(ServerLevel level) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        PENDING.removeIf(entry -> {
            if (now < entry[0]) {
                return false;
            }
            SixtySecondsAirdrop.drop(level, (int) entry[1], (int) entry[2]);
            return true;
        });
    }

    /** 对局重置时清空待落空投。 */
    public static void reset() {
        PENDING.clear();
    }
}
