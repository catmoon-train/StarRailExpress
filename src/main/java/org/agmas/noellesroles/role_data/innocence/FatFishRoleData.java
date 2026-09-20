package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.bouns.roles.FatFishRole;

/**
 * 大肥鱼的职业数据。
 *
 * <ul>
 * <li><b>浑水鱼</b>：在水里持续获得水下呼吸 + 海豚的恩惠，并且额外恢复体力；</li>
 * <li><b>圆滚滚</b>：抗击退属性加成（init 加、clear 移除），并且移动时把撞到的玩家顶开；</li>
 * <li><b>消化冷却</b>：被投喂一次后要等 {@link FatFishRole#FEED_COOLDOWN_SECONDS} 秒才能再被喂。</li>
 * </ul>
 */
public class FatFishRoleData extends SimpleRoleData {

    /** 抗击退属性修饰符 */
    private static final ResourceLocation KNOCKBACK_MODIFIER_ID = Noellesroles.id("fat_fish_knockback_resistance");
    /** 抗击退量（0.6 = 减少 60% 击退） */
    private static final double KNOCKBACK_RESISTANCE = 0.6D;

    /** 水中每 tick 额外恢复的体力（净恢复，原版那 0.4 之外再给这么多） */
    private static final float WATER_STAMINA_RECOVERY = 0.6F;

    /** 顶开别人的判定半径（格） */
    private static final double PUSH_RADIUS = 1.3D;
    /** 自己移动速度平方超过这个值才触发顶开（约 0.15 格 / tick） */
    private static final double PUSH_MIN_SPEED_SQ = 0.02D;
    /** 顶开力度 */
    private static final double PUSH_STRENGTH = 0.55D;

    /**
     * 投喂冷却结束的时刻（用「结束时刻」而不是每 tick 自减的倒计时，
     * 这样时停 / 会议期间冷却会正确暂停）。0 = 现在就能被喂。
     */
    public long feedReadyAt = 0L;

    public FatFishRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        applyKnockbackResistance();
    }

    @Override
    public void clear() {
        removeKnockbackResistance();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        tickWater();
        tickPushAway();
    }

    // ==================== 投喂：消化冷却 ====================

    /** 现在能不能被投喂 */
    public boolean canBeFed() {
        return SRE.getTicksFromGameStart() >= feedReadyAt;
    }

    /** 记录一次投喂，开始消化 */
    public void markFed() {
        feedReadyAt = SRE.getTicksFromGameStart() + FatFishRole.FEED_COOLDOWN_TICKS;
    }

    /** 还需要消化多久（tick） */
    public long feedCooldownRemainingTicks() {
        return Math.max(0L, feedReadyAt - SRE.getTicksFromGameStart());
    }

    // 本职业没有需要同步给客户端的字段（体力走原版那套两端各自模拟）
    @Override
    public void writeToSyncNbt(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider provider) {
    }

    @Override
    public void readFromSyncNbt(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider provider) {
    }

    // ==================== 浑水鱼 ====================

    private void tickWater() {
        if (!player.isInWater()) {
            return;
        }
        // 短时长、每 tick 刷新：离开水面就自然时效
        player.addEffect(FatFishRole.quietEffect(MobEffects.WATER_BREATHING, 40, 0));
        player.addEffect(FatFishRole.quietEffect(MobEffects.DOLPHINS_GRACE, 40, 0));
        if (ModEffects.hasInfiniteStamina(player)) {
            return;
        }
        if (!(player instanceof PlayerStaminaGetter stamina)) {
            return;
        }
        float max = FatFishRole.maxStamina(player);
        if (max == Float.MAX_VALUE) {
            return;
        }
        float current = stamina.starrailexpress$getStamina();
        if (current < 0f) {
            current = max;
        }
        stamina.starrailexpress$setStamina(Math.min(max, current + WATER_STAMINA_RECOVERY));
    }

    // ==================== 圆滚滚 ====================

    private void applyKnockbackResistance() {
        var attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attribute == null || attribute.getModifier(KNOCKBACK_MODIFIER_ID) != null) {
            return;
        }
        attribute.addTransientModifier(
                new AttributeModifier(KNOCKBACK_MODIFIER_ID, KNOCKBACK_RESISTANCE,
                        AttributeModifier.Operation.ADD_VALUE));
    }

    private void removeKnockbackResistance() {
        var attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attribute != null && attribute.getModifier(KNOCKBACK_MODIFIER_ID) != null) {
            attribute.removeModifier(KNOCKBACK_MODIFIER_ID);
        }
    }

    /** 移动时把贴到自己的人顶开 */
    private void tickPushAway() {
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.x * velocity.x + velocity.z * velocity.z < PUSH_MIN_SPEED_SQ) {
            return;
        }
        Vec3 self = player.position();
        for (Player other : player.level().players()) {
            if (other == player || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            Vec3 offset = other.position().subtract(self);
            double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            if (horizontal > PUSH_RADIUS || horizontal < 1.0E-4D) {
                continue;
            }
            other.push(offset.x / horizontal * PUSH_STRENGTH, 0.2D, offset.z / horizontal * PUSH_STRENGTH);
            // 追踪
            other.setLastHurtByMob(player);
            other.hurtMarked = true;
            if (other instanceof ServerPlayer serverOther) {
                serverOther.connection.send(new ClientboundSetEntityMotionPacket(serverOther));
            }
        }
    }
}
