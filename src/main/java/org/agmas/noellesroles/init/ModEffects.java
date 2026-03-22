package org.agmas.noellesroles.init;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.effects.NoCollideEffect;
import org.agmas.noellesroles.effects.SimpleMobEffect;
import org.agmas.noellesroles.effects.TimeStopEffect;

public class ModEffects {
    public static final Holder<MobEffect> SKILL_BANED = register("skill_baned", new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));
    public static final Holder<MobEffect> BLACK_MONITOR = register("black_monitor", new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));
    public static final Holder<MobEffect> MOVE_BANED = register("move_baned", new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));
    public static final Holder<MobEffect> TURN_BANED = register("turn_baned", new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));
    public static final Holder<MobEffect> USED_BANED = register("used_baned", new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));

    /**
     * 时间停止效果
     * - 中性效果
     * - 白色粒子
     */
    public static final Holder<MobEffect> TIME_STOP = register("time_stop", new TimeStopEffect());

    /**
     * 无碰撞效果
     * - 中性效果
     * - 绿色粒子
     */
    public static final Holder<MobEffect> NO_COLLIDE = register("no_collide", new NoCollideEffect());

    /**
     * 鬼缚效果（布袋鬼攻击诅咒）
     * - 有害效果，深红色
     * - 被攻击者：隐身 + 无法移动 + 无法使用物品 + 红色粒子
     */

    public static final Holder<MobEffect> GHOST_CURSE = register("ghost_curse", new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x8B0000){
        @Override
        public boolean shouldApplyEffectTickThisTick(int i, int j) {
            return true;
        }

        @Override
        public boolean isEnabled(FeatureFlagSet featureFlagSet) {
            return true;
        }

        @Override
        public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
            if (livingEntity.level() instanceof ServerLevel serverLevel){
                BlockPos blockPos = livingEntity.blockPosition().above(1);
                serverLevel.sendParticles(DustParticleOptions.REDSTONE, (double)blockPos.getX() , (double)blockPos.getY() , (double)blockPos.getZ() ,14, (double)0.6F, (double)0.6F, (double)0.6F,0.4d);

            }
            return super.applyEffectTick(livingEntity, amplifier);
        }
    });

    /**
     * 里世界侵蚀效果
     * - 有害效果，暗紫色
     * - 用于标记处于里世界影响下的好人玩家，驱动客户端shader和场景变化
     */
    public static final Holder<MobEffect> OTHERWORLD_AURA = register("otherworld_aura", new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x4B0082));

    /**
     * 心情消耗减缓
     * - 有益效果
     * - 降低 mood 的自然消耗速度
     */
    public static final Holder<MobEffect> MOOD_DRAIN_REDUCTION = register("mood_drain_reduction", new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0x63D5A5));

    /**
     * 无视心情消耗
     * - 有益效果
     * - mood 不再因任务自然下降
     */
    public static final Holder<MobEffect> MOOD_DRAIN_IMMUNITY = register("mood_drain_immunity", new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0x2CC36B));

    /**
     * 心情持续恢复
     * - 有益效果
     * - 持续缓慢恢复 mood
     */
    public static final Holder<MobEffect> MOOD_REGENERATION = register("mood_regeneration", new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0x7AF2D2));

    /**
     * 无限体力
     * - 有益效果
     * - 冲刺不消耗体力
     */
    public static final Holder<MobEffect> INFINITE_STAMINA = register("infinite_stamina", new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xF6C95A));

    /**
     * 体力提升
     * - 有益效果
     * - 提升体力上限
     */
    public static final Holder<MobEffect> STAMINA_BOOST = register("stamina_boost", new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xE7A945));

    /**
     * 体力恢复加速
     * - 有益效果
     * - 增加非冲刺状态下体力回复速度
     */
    public static final Holder<MobEffect> STAMINA_RECOVERY = register("stamina_recovery", new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xFFD97D));

    /**
     * 低san视觉抗性
     * - 有益效果
     * - 降低低san下后处理视觉干扰（等级越高越强）
     */
    public static final Holder<MobEffect> LOW_SAN_SHADER_RESISTANCE = register("low_san_shader_resistance", new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xA9D6FF));

    /**
     * 注册药水效果到注册表
     */

    private static Holder<MobEffect> register(String id, MobEffect statusEffect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Noellesroles.id(id), statusEffect);
    }

    private static int getAmplifier(LivingEntity entity, Holder<MobEffect> effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance != null ? instance.getAmplifier() : -1;
    }

    public static float getMoodDrainMultiplier(LivingEntity entity) {
        if (entity.hasEffect(MOOD_DRAIN_IMMUNITY)) {
            return 0f;
        }
        int amp = getAmplifier(entity, MOOD_DRAIN_REDUCTION);
        if (amp < 0) {
            return 1f;
        }
        return Mth.clamp(1f - 0.3f * (amp + 1), 0f, 1f);
    }

    public static float getMoodRegenPerTick(LivingEntity entity) {
        int amp = getAmplifier(entity, MOOD_REGENERATION);
        if (amp < 0) {
            return 0f;
        }
        return 0.0008f * (amp + 1);
    }

    public static boolean hasInfiniteStamina(LivingEntity entity) {
        return entity.hasEffect(INFINITE_STAMINA);
    }

    public static float getStaminaCapacityMultiplier(LivingEntity entity) {
        int amp = getAmplifier(entity, STAMINA_BOOST);
        if (amp < 0) {
            return 1f;
        }
        return 1f + 0.35f * (amp + 1);
    }

    public static float getStaminaRecoveryMultiplier(LivingEntity entity) {
        int amp = getAmplifier(entity, STAMINA_RECOVERY);
        if (amp < 0) {
            return 1f;
        }
        return 1f + 0.75f * (amp + 1);
    }

    public static float getLowSanShaderResistance(LivingEntity entity) {
        int amp = getAmplifier(entity, LOW_SAN_SHADER_RESISTANCE);
        if (amp < 0) {
            return 0f;
        }
        return Mth.clamp(0.35f * (amp + 1), 0f, 1f);
    }

    /**
     * 初始化所有药水效果
     */
    public static void init() {

    }
}
