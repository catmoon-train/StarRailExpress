package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 钩锁：右键钩住准星指向的方块（射程 {@link SixtySecondsBalance#GRAPPLE_RANGE} 格），把自己朝落点荡过去。
 * 耐久 {@link SixtySecondsBalance#GRAPPLE_DURABILITY} 次、冷却
 * {@link SixtySecondsBalance#GRAPPLE_COOLDOWN_TICKS 15 秒}（原版物品冷却，快捷栏有转圈显示）。
 * <b>荡索期间落地无摔落伤害</b>——服务端持续清零 fallDistance 直到落地（不产生摔落伤害事件，
 * 与 {@code SixtySecondsHealthSystem} 的伤害折算天然兼容），落地或超时窗口即恢复正常。
 * 没钩中不消耗耐久、不进冷却。参照 {@link SixtySecondsRopeItem} 的全局 tick 注册套路。
 */
public class SixtySecondsGrapplingHookItem extends Item {

    /** 一次荡索的摔落保护：开始 tick（防止起跳瞬间就被“在地面”判掉）+ 超时 tick。 */
    private record NoFall(long startTick, long expireTick) {
    }

    private static final Map<UUID, NoFall> NO_FALL = new HashMap<>();

    public SixtySecondsGrapplingHookItem(Properties properties) {
        super(properties);
    }

    /** 模组初始化时注册一次：荡索期间清零摔落距离、落地/超时解除。 */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SixtySecondsGrapplingHookItem::tick);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.consume(stack);
        }
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        HitResult hit = player.pick(SixtySecondsBalance.GRAPPLE_RANGE, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            // 没钩中：不耗耐久、不进冷却
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.grapple_miss",
                            SixtySecondsBalance.GRAPPLE_RANGE), true);
            serverPlayer.playNotifySound(SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.6F, 1.4F);
            return InteractionResultHolder.consume(stack);
        }
        Vec3 pull = hit.getLocation().subtract(player.position());
        double dist = pull.length();
        // 速度随距离放大（封顶），并按距离补一点抬升让弧线能越过沿途障碍
        double speed = Math.min(2.6, 0.9 + dist * 0.1);
        Vec3 velocity = pull.normalize().scale(speed).add(0, Math.min(0.45, 0.12 + dist * 0.015), 0);
        player.setDeltaMovement(velocity);
        player.hurtMarked = true; // 强制把运动同步给客户端（ServerPlayer 不会自动同步 setDeltaMovement）

        long now = serverLevel.getGameTime();
        NO_FALL.put(player.getUUID(), new NoFall(now, now + SixtySecondsBalance.GRAPPLE_NO_FALL_TICKS));
        serverPlayer.getCooldowns().addCooldown(this, SixtySecondsBalance.GRAPPLE_COOLDOWN_TICKS);
        stack.hurtAndBreak(1, serverPlayer, LivingEntity.getSlotForHand(hand));
        serverLevel.playSound(null, player.blockPosition(),
                SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.9F);
        return InteractionResultHolder.consume(stack);
    }

    /** 荡索期间每 tick 清零摔落距离 → 落地不结算摔落伤害；落地（>0.5s 后）或超时解除保护。 */
    private static void tick(ServerLevel level) {
        if (NO_FALL.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Map.Entry<UUID, NoFall>> it = NO_FALL.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, NoFall> entry = it.next();
            if (now >= entry.getValue().expireTick()) {
                it.remove();
                continue;
            }
            if (!(level.getPlayerByUUID(entry.getKey()) instanceof ServerPlayer player)) {
                continue;
            }
            player.fallDistance = 0.0F;
            if (player.onGround() && now - entry.getValue().startTick() > 10) {
                it.remove();
            }
        }
    }

    public static void reset() {
        NO_FALL.clear();
    }
}
