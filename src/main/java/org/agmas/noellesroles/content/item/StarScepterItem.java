/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.event.OnGameServerTick;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.StarProjectileEntity;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 星之权杖：蓄力物品（用法与巫师法杖一致）。
 * <p>
 * 右键蓄力 → 松开时按视线锁定 35 格内最近的玩家，星星从高空砸下（死因「星星」）。
 * 蓄力不足一半不会释放；冷却统一走原版物品冷却。
 * <p>
 * <b>本物品不绑定职业</b>：任何拿到星之权杖的玩家都能正常使用。摘星者只是开局自带一把，
 * 权杖本身的规则（锁定、延迟落星、冷却、命中结算）全部集中在本类，不再依赖
 * {@code StarGathererRoleData}，因此其它职业持有也能走完整流程。
 */
public class StarScepterItem extends Item implements ChargeableItem {

    /** 需要蓄力的 tick 数。不足一半不释放。 */
    private static final int MAX_CHARGE_TICKS = 20;

    /** 权杖攻击距离（格）。 */
    public static final double SCEPTER_RANGE = 35.0D;
    /** 锁定后到星星出现的延迟（2 秒）。 */
    public static final int SCEPTER_DROP_DELAY_TICKS = 2 * 20;
    /** 权杖命中玩家后的原版物品冷却（20 秒；只要命中就触发，不要求击杀）。 */
    public static final int SCEPTER_HIT_COOLDOWN_TICKS = 20 * 20;

    /**
     * 待降落的星星。
     * <p>
     * 不再挂在 {@code StarGathererRoleData} 上：那份数据只有摘星者才有，其它职业拿到权杖后
     * 根本没有地方登记，星星就永远不会出现。这里改成物品自己的静态队列，由
     * {@link OnGameServerTick} 统一推进，与持有者是什么职业无关。
     */
    private static final List<PendingStar> PENDING_STARS = new ArrayList<>();

    /** 一颗待降落的星星：目标、召唤者、剩余 tick。 */
    private record PendingStar(UUID target, UUID owner, int ticksLeft) {
    }

    /** 全局 tick 事件只注册一次。 */
    private static boolean tickRegistered;

    public StarScepterItem(Properties properties) {
        super(properties);
    }

    // ==================== 全局推进（与职业无关） ====================

    /**
     * 注册待降落星星的推进器（幂等）。
     * <p>
     * 用全局 {@link OnGameServerTick} 而不是某个职业的 {@code serverTick}：权杖不绑定职业，
     * 任何持有者释放的星星都必须照常落下。
     */
    public static void registerTick() {
        if (tickRegistered) {
            return;
        }
        tickRegistered = true;
        OnGameServerTick.EVENT.register(StarScepterItem::tickPendingStars);
        // 对局结束清空残留，避免跨局把上一局登记的星星带到下一局
        io.wifi.starrailexpress.event.OnGameEnd.EVENT.register(
                (serverLevel, gameWorldComponent) -> PENDING_STARS.clear());
    }

    /** 每 tick 推进所有待降落的星星。 */
    private static void tickPendingStars(ServerLevel level) {
        if (PENDING_STARS.isEmpty()) {
            return;
        }
        List<PendingStar> remaining = new ArrayList<>(PENDING_STARS.size());
        for (PendingStar star : PENDING_STARS) {
            int left = star.ticksLeft() - 1;
            if (left > 0) {
                remaining.add(new PendingStar(star.target(), star.owner(), left));
                continue;
            }
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(star.target());
            if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            // 召唤者可能已经死亡/掉线，此时星星照常落下，只是不记击杀归属
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(star.owner());
            // 星星从目标上方 24 格的高空落下
            Vec3 from = new Vec3(target.getX(), target.getY() + 24.0D, target.getZ());
            StarProjectileEntity.spawn(level, from, target, owner);
            level.playSound(null, target.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS, 1.2F, 0.7F);
        }
        PENDING_STARS.clear();
        PENDING_STARS.addAll(remaining);
    }

    // ==================== 使用 ====================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 原版物品冷却中 → 直接拒绝，避免依赖原版 use 被冷却拦截的隐含前提
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingUseTicks) {
        if (level.isClientSide || !(entity instanceof ServerPlayer sp)) {
            return;
        }
        int chargeTime = getUseDuration(stack, entity) - remainingUseTicks;
        if (chargeTime < MAX_CHARGE_TICKS / 2) {
            return; // 蓄力不足
        }
        // 成功后立刻进入原版物品冷却
        if (useScepter(sp)) {
            sp.getCooldowns().addCooldown(this, SCEPTER_HIT_COOLDOWN_TICKS);
        }
    }

    /**
     * 使用星之权杖：锁定视线方向 {@value #SCEPTER_RANGE} 格内最近的玩家，
     * {@value #SCEPTER_DROP_DELAY_TICKS} tick 后星星从高空砸下。
     * <p>
     * 不检查职业：只要求玩家存活、时间未冻结、权杖不在冷却中、并且视野里有目标。
     */
    public static boolean useScepter(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        if (GameUtils.isTimeFrozen(sp.level())) {
            return false;
        }
        // 冷却统一走原版物品冷却
        if (sp.getCooldowns().isOnCooldown(ModItems.STAR_SCEPTER)) {
            return false;
        }

        ServerPlayer target = findScepterTarget(sp);
        if (target == null) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.star_gatherer.scepter_no_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        ServerLevel level = sp.serverLevel();
        PENDING_STARS.add(new PendingStar(target.getUUID(), sp.getUUID(), SCEPTER_DROP_DELAY_TICKS));

        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4F, 1.2F);
        level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1.0D, target.getZ(),
                30, 0.6D, 1.0D, 0.6D, 0.4D);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.star_gatherer.scepter_locked", target.getName())
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** 视线方向 35 格内、夹角最小的存活玩家。 */
    @Nullable
    private static ServerPlayer findScepterTarget(ServerPlayer sp) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle().normalize();
        ServerPlayer best = null;
        double bestDot = 0.94D; // 约 20 度锥形
        for (ServerPlayer candidate : sp.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                sp.getBoundingBox().inflate(SCEPTER_RANGE),
                p -> p != sp && GameUtils.isPlayerAliveAndSurvival(p) && !p.isSpectator())) {
            Vec3 to = candidate.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist > SCEPTER_RANGE || dist < 0.001D)
                continue;
            double dot = to.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 蓄力中的杖端粒子。
     * <p>
     * <b>只发给使用者本人</b>：{@code ServerLevel#sendParticles} 是向周围 32 格内
     * 所有玩家广播，别人也能看到使用者在蓄力，等于暴露位置。这里直接构造
     * {@code ClientboundLevelParticlesPacket} 只塞给使用者自己的连接。
     * <p>
     * 另外先校验手里拿的确实是星之权杖，避免粒子挂到别的物品上。
     */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (!(entity instanceof ServerPlayer sp)) {
            return;
        }
        // 只在「正拿着星之权杖」时生效
        if (!stack.is(ModItems.STAR_SCEPTER)) {
            return;
        }
        int chargeTime = getUseDuration(stack, entity) - remainingUseTicks;
        if (chargeTime < MAX_CHARGE_TICKS / 2) {
            return; // 蓄力不足时不显示，保持隐蔽
        }

        Vec3 look = sp.getViewVector(1.0f).normalize();
        Vec3 tip = sp.getEyePosition().add(look.scale(0.8)).add(0, -0.2, 0);
        // 仅发送给使用者自己
        sendParticleToSelf(sp, ParticleTypes.END_ROD, tip);
        if (chargeTime % 2 == 0) {
            sendParticleToSelf(sp, ParticleTypes.ENCHANT, tip);
        }
    }

    /** 把单颗粒子只发给这一个玩家（不进网络广播）。 */
    private static void sendParticleToSelf(ServerPlayer sp, net.minecraft.core.particles.SimpleParticleType type,
            Vec3 pos) {
        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(
                type, false, pos.x, pos.y, pos.z, 0.05F, 0.05F, 0.05F, 0.0F, 1));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    // ==================== ChargeableItem（蓄力视觉） ====================

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return MAX_CHARGE_TICKS;
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.max(0f, Math.min(1f, (float) ticksUsingItem / MAX_CHARGE_TICKS));
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return true;
    }
}
