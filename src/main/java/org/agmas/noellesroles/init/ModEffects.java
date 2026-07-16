package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.flag.FeatureFlagSet;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.GhostStateComponent;
import org.agmas.noellesroles.content.effects.NoCollideEffect;
import org.agmas.noellesroles.content.effects.SimpleMobEffect;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.game.roles.killer.nostalgist.NostalgistBackworldEffectSync;
import org.agmas.noellesroles.game.roles.killer.wraith_assassin.WraithDimensionEffectSync;

public class ModEffects {
    /** 禁止商店购买 */
    public static final Holder<MobEffect> SHOP_BANNED = register("shop_banned",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));
    /** 仅禁止CCA/职业执行tick */
    public static final Holder<MobEffect> CCA_FREEZED = register("cca_freezed",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));
    /** 禁止CCA/职业执行tick与禁止技能使用 */
    public static final Holder<MobEffect> SKILL_FREEZED = register("skill_freezed",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));
    /** 禁止技能使用 */
    public static final Holder<MobEffect> SKILL_BANED = register("skill_baned",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0xFFFFFF));
    public static final Holder<MobEffect> INVENTORY_BANED = register("inventory_baned",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0xFFFFFF));
    public static final Holder<MobEffect> EAT_MEAT_FOOD = register("eat_meat_food",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0xFFFFFF));
    public static final Holder<MobEffect> NEXT_SKILL_BANED = register("next_skill_baned",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0xFFFFFF));
    public static final Holder<MobEffect> TAROT_ASSEMBLY = register("tarot_assembly",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));
    public static final Holder<MobEffect> BLACK_MONITOR = register("black_monitor",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0xFFFFFF));
    public static final Holder<MobEffect> GHOST_STATE = register("ghost_state",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF) {
                @Override
                public boolean shouldApplyEffectTickThisTick(int i, int j) {
                    return true;
                }

                @Override
                public boolean applyEffectTick(LivingEntity livingEntity, int i) {

                    if (livingEntity instanceof ServerPlayer serverPlayer) {
                        GhostStateComponent ghostStateComponent = GhostStateComponent.KEY
                                .get(serverPlayer);
                        if (!ghostStateComponent.isGhostState()) {
                            ghostStateComponent.isGhost = true;
                            ghostStateComponent.sync();
                        }
                    }
                    return super.applyEffectTick(livingEntity, i);
                }
            });
    public static final Holder<MobEffect> MOVE_BANED = register("move_baned",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0xFFFFFF) {
                @Override
                public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
                    // 应该单独给而不是打包
                    // if (livingEntity.level().getGameTime() % 20 == 0)
                    // livingEntity.addEffect(new MobEffectInstance(
                    // ModEffects.SAFE_TIME,
                    // 40, // 持续时间 30s（tick）
                    // 5, // 等级（0 = 速度 I）
                    // true, // ambient（环境效果，如信标）
                    // false, // showParticles（显示粒子）
                    // false // showIcon（显示图标）
                    // ));
                    return super.applyEffectTick(livingEntity, amplifier);
                }
            });
    public static final Holder<MobEffect> TURN_BANED = register("turn_baned",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0xFFFFFF));
    public static final Holder<MobEffect> USED_BANED = register("used_baned",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0xFFFFFF));

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
     * 安全时间效果
     * - 中性效果
     * - 绿色粒子
     */
    public static final Holder<MobEffect> SAFE_TIME = register("safe_time", new NoCollideEffect());

    /**
     * 鬼缚效果（布袋鬼攻击诅咒）
     * - 有害效果，深红色
     * - 被攻击者：隐身 + 无法移动 + 无法使用物品 + 红色粒子
     */

    public static final Holder<MobEffect> GHOST_CURSE = register("ghost_curse",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x8B0000) {
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
                    if (livingEntity.level() instanceof ServerLevel serverLevel) {
                        BlockPos blockPos = livingEntity.blockPosition().above(1);
                        serverLevel.sendParticles(DustParticleOptions.REDSTONE,
                                (double) blockPos.getX(),
                                (double) blockPos.getY(), (double) blockPos.getZ(), 14,
                                (double) 0.6F, (double) 0.6F,
                                (double) 0.6F, 0.4d);
                    }
                    if (livingEntity.level().getGameTime() % 20 == 0)
                        livingEntity.addEffect(new MobEffectInstance(
                                ModEffects.SAFE_TIME,
                                40, // 持续时间 30s（tick）
                                5, // 等级（0 = 速度 I）
                                true, // ambient（环境效果，如信标）
                                false, // showParticles（显示粒子）
                                false // showIcon（显示图标）
                        ));
                    return super.applyEffectTick(livingEntity, amplifier);
                }
            });

    /**
     * 里世界侵蚀效果
     * - 有害效果，暗紫色
     * - 用于标记处于里世界影响下的好人玩家，驱动客户端shader和场景变化
     */
    public static final Holder<MobEffect> OTHERWORLD_AURA = register("otherworld_aura",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x4B0082));

    /**
     * 怀旧者·里世界状态标记
     * - 中性效果，灰白色
     * - 由 {@code NostalgistPlayerComponent} 在里世界期间每 tick 维持，驱动客户端独立的灰白滤镜
     * shader（{@code TimeStopShader} 的 {@code nostalgist_gray} pass）并隐藏手持物品
     * （{@code InvisbleHandItem}）。禁止说话/使用物品则由 {@link #CHAT_BAN} /
     * {@link #VOICE_SILENCE}
     * / {@link #USED_BANED} 一并施加。
     */
    public static final Holder<MobEffect> NOSTALGIST_BACKWORLD = register("nostalgist_backworld",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xBFBFBF));

    public static final Holder<MobEffect> WRAITH_DIMENSION = register("wraith_dimension",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x315B7C));

    public static final Holder<MobEffect> WRAITH_MANIFEST = register("wraith_manifest",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x8FD6FF));

    /**
     * 脚步消失
     * - 中性效果，灰色
     * - 拥有者的脚步声不会被任何人听到、疾跑粒子不显示（行为见
     * {@code org.agmas.noellesroles.mixin.FootstepVanishMixin} 对
     * {@code playStepSound} /
     * {@code canSpawnSprintParticle} 的拦截）。由 {@code FootstepVanishEffectSync}
     * 广播给所有客户端，
     * 使其它玩家侧运行的拦截也能查到该效果，从而真正做到“别人听不到脚步”。
     */
    public static final Holder<MobEffect> FOOTSTEP_VANISH = register("footstep_vanish",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x9E9E9E));

    /**
     * san值消耗减缓
     * - 有益效果
     * - 降低 mood 的自然消耗速度
     */
    public static final Holder<MobEffect> MOOD_DRAIN_REDUCTION = register("mood_drain_reduction",
            new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0x63D5A5));

    /**
     * 无视心情消耗
     * - 有益效果
     * - mood 不再因任务自然下降
     */
    public static final Holder<MobEffect> MOOD_DRAIN_IMMUNITY = register("mood_drain_immunity",
            new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0x2CC36B));

    /**
     * san值恢复
     * - 有益效果
     * - 持续缓慢恢复 mood
     */
    public static final Holder<MobEffect> MOOD_REGENERATION = register("mood_regeneration",
            new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0x7AF2D2));
    /**
     * 无敌
     */
    public static final Holder<MobEffect> INVINCIBLE = register("invincible",
            new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0x7AF2D2));

    /**
     * 无限体力
     * - 有益效果
     * - 冲刺不消耗体力
     */
    public static final Holder<MobEffect> INFINITE_STAMINA = register("infinite_stamina",
            new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xF6C95A));

    /**
     * 体力提升
     * - 有益效果
     * - 提升体力上限
     */
    public static final Holder<MobEffect> STAMINA_BOOST = register("stamina_boost",
            new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xE7A945));

    /**
     * 体力恢复效率提升
     * - 有益效果
     * - 增加非冲刺状态下体力回复速度
     */
    public static final Holder<MobEffect> STAMINA_RECOVERY = register("stamina_recovery",
            new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xFFD97D));

    /**
     * 低san视觉抗性
     * - 有益效果
     * - 降低低san下后处理视觉干扰（等级越高越强）
     */
    public static final Holder<MobEffect> LOW_SAN_SHADER_RESISTANCE = register("low_san_shader_resistance",
            new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xA9D6FF));

    /**
     * 黑白狂暴前奏效果
     * - 有害效果
     * - 全服减速20%+无法打开背包+水墨风shader
     * - 持续60秒
     */

    /**
     * 沉浸式滤镜效果：仙境
     */
    public static final Holder<MobEffect> FAIRYLAND_FILTER = register("fairyland_filter",
            new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xB7F7FF));

    /**
     * 沉浸式滤镜效果：后世
     */
    public static final Holder<MobEffect> AFTERLIFE_FILTER = register("afterlife_filter",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xD7D7D7));

    /**
     * 沉浸式滤镜效果：梦核
     */
    public static final Holder<MobEffect> DREAMCORE_FILTER = register("dreamcore_filter",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFC0F5));

    /**
     * 沉浸式滤镜效果：后室
     * 纯视觉：VHS 录像带式后室渲染滤镜（搬运自 MinecraftFoundFootage 的 VHS 后处理），无任何玩法副作用。
     */
    public static final Holder<MobEffect> BACKROOMS_FILTER = register("backrooms_filter",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xD9C86B));

    /**
     * 玩家隔离：看不见/听不见其他玩家
     */
    public static final Holder<MobEffect> PLAYER_ISOLATION = register("player_isolation",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x6A5ACD));
    /**
     * 重金属语音：让 simple voice chat 的说话音色变低沉
     */
    public static final Holder<MobEffect> HEAVY_METAL_VOICE = register("heavy_metal_voice",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x505050));
    /**
     * 扩音语音：扩大语音传播范围
     */
    public static final Holder<MobEffect> VOICE_RANGE_BOOST = register("voice_range_boost",
            new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0x8FD3FF));
    /**
     * 回响语音：让语音出现回音
     */
    public static final Holder<MobEffect> VOICE_ECHO = register("voice_echo",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xCBB6FF));
    /**
     * 沉默语音：让其他人听不到说话者的声音
     */
    public static final Holder<MobEffect> VOICE_SILENCE = register("voice_silence",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x808080));
    /**
     * 聊天禁止：拥有此效果的玩家发送的聊天消息不会被任何人看到
     */
    public static final Holder<MobEffect> CHAT_BAN = register("chat_ban",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x666666));
    /**
     * 闯入者标记：撬锁器/开锁器破门闯入别队避难所后标记闯入者，
     * 用于豁免新手保护期的 PvP 禁令（见 {@code SixtySecondsHealthSystem.isPvpBlocked}）。
     * 隐身、无粒子、无图标——纯标记用途，不给被闯入方任何视觉提示（潜行进入）。
     */
    public static final Holder<MobEffect> BREAK_IN_INTRUDER = register("break_in_intruder",
            new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xFF6B35));
    public static final Holder<MobEffect> MONOKUMA_FRENZY = register("monokuma_frenzy",
            new org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaFrenzyEffect());

    /**
     * 伪装效果
     * - 中性效果
     * - 持续期间客户端会把玩家皮肤替换为预留的伪装皮肤
     * （见 OnGettingPlayerSkin 监听器，皮肤资源位于
     * assets/starrailexpress/textures/entity/disguise/disguise_skin.png）
     */
    public static final Holder<MobEffect> DISGUISE = register("disguise",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x7B68EE));

    /**
     * 时间回溯恍惚：滞时鬼回溯时所有人短暂获得，触发客户端时空滤镜 shader。
     */
    public static final Holder<MobEffect> TIME_REWIND_DAZE = register("time_rewind_daze",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x8A6BFF));

    /**
     * 时停滤镜（纯视觉）：驱动与 {@link #TIME_STOP} 完全相同的客户端灰白滤镜 shader，
     * 但不附带任何时停玩法副作用（不冻结世界/不封控/不静音）。
     * 滞时鬼回溯期间施加给回溯者本人，让其屏幕呈现时停同款滤镜。
     */
    public static final Holder<MobEffect> TIME_STOP_FILTER = register("time_stop_filter",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));

    /**
     * 诡域标记（鬼眼·杨间）。
     * - 有害效果，幽蓝色
     * - 标记处于诡域内的玩家；拥有此效果的玩家无法开启杀手透视
     * （客户端拦截见 {@code org.agmas.noellesroles.mixin.client.InstinctMixin}）。
     */
    public static final Holder<MobEffect> EERIE_DOMAIN = register("eerie_domain",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x6A5ACD));

    /**
     * 视野迷雾
     * - 有害效果，灰蓝色
     * - 拥有者视野被浓雾笼罩；等级越高雾的距离越远（看得越远）。
     * 1 级（amplifier 0）时雾仅 2 格。雾的渲染见
     * {@code org.agmas.noellesroles.mixin.client.VisionFogMixin}（注入
     * FogRenderer.setupFog）。
     */
    public static final Holder<MobEffect> VISION_FOG = register("vision_fog",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x55667A));

    /**
     * 2D 视角
     * - 中性效果
     * - 客户端固定侧视镜头。amplifier: 0=西边，1=东边，2=北边，3=南边，4=上方（0~3 为 2.5D 俯视侧视）；
     * 5=西平面，6=东平面，7=北平面，8=南平面（与视点同高的纯平面视线，无俯角）。
     * 行为见 {@code TwoDimensionalCameraClientHandle}，最终通过 AdvancedCameraDirector
     * 接管相机。
     * - 拥有该效果时，客户端会渲染指向当前 SAN 任务点的指引箭头（手持钥匙时额外指向自己房门），
     * 见 {@code TwoDimensionalTaskArrowRenderer}。
     */
    public static final Holder<MobEffect> TWO_DIMENSIONAL_CAMERA = register("two_dimensional_camera",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x87CEFA));

    /**
     * 2D camera distance. Amplifier 0 keeps the legacy distance, each extra level
     * moves farther away.
     */
    public static final Holder<MobEffect> TWO_DIMENSIONAL_CAMERA_DISTANCE = register(
            "two_dimensional_camera_distance",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xB3E5FC));

    /**
     * 指针视角
     * - 中性效果
     * - 客户端显示鼠标指针，并让玩家朝向指针射线命中的方块/实体。
     */
    public static final Holder<MobEffect> POINTER = register("pointer",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFE082));

    /**
     * 箱庭视野（{@link #TWO_DIMENSIONAL_CAMERA} 的扩展核心）
     * - 中性效果
     * - 客户端以玩家所在位置做泛洪扫描自动构筑「当前房间」的箱庭，并按当前视角
     * 自动切割遮挡面：俯视（2D 效果 amplifier 4，或普通视角低头超过约 45°）时
     * 屋顶整层被隐藏，可直接俯瞰自己的身体；2.5D / 平面侧视（amplifier 0~3 / 5~8）时
     * 面向镜头一侧的墙体被切开。屋顶上的方块与实体一并被剔除 —— 只有玩家自己
     * 走上屋顶（头顶见天，视为 outside）时才恢复完整渲染。
     * 行为见 {@code HakoniwaVisionClientHandle}；区块网格级的方块剔除见
     * {@code mixin/client/hakoniwa} 与 sodium 兼容层 {@code mixin/compat/sodium}。
     */
    public static final Holder<MobEffect> HAKONIWA_VISION = register("hakoniwa_vision",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x9CCFB8));

    /**
     * 颤抖
     * - 有害效果，暗红色
     * - 拥有者因恐惧而双手发抖：客户端准星/视角会缓慢随机漂移。
     * 漂移逻辑见 {@code org.agmas.noellesroles.game.roles.killer.dream.client.DreamClientHandler}
     * （客户端 tick，纯本地视角偏移，不发包）。
     * Dream（梦魇）狂暴时被"看到"的玩家会获得此效果。
     */
    public static final Holder<MobEffect> TREMBLE = register("tremble",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x8B1A1A));

    /** 视野迷雾：根据效果等级计算雾的可见距离（格）。1 级=2 格，每升 1 级多看 3 格。 */
    public static float getVisionFogDistance(int amplifier) {
        return 2.0f + Math.max(0, amplifier) * 3.0f;
    }

    /**
     * 2D camera distance by potion amplifier. Level I = 28 blocks, +6 blocks per
     * level.
     */
    public static float getTwoDimensionalCameraDistance(int amplifier) {
        return Mth.clamp(2.0f + Math.max(0, amplifier) * 3.0f, 8.0f, 64.0f);
    }

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
        return 0.005f * (amp + 1);
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
        return Mth.clamp(0.25f * (amp + 1), 0f, 1f);
    }

    public static float getHeavyMetalPitchRatio(LivingEntity entity) {
        int amp = getAmplifier(entity, HEAVY_METAL_VOICE);
        if (amp < 0) {
            return 1f;
        }
        return Mth.clamp(1f - 0.15f * (amp + 1), 0.4f, 1f);
    }

    public static float getVoiceRangeMultiplier(LivingEntity entity) {
        int amp = getAmplifier(entity, VOICE_RANGE_BOOST);
        if (amp < 0) {
            return 1f;
        }
        return 1f + (amp + 1);
    }

    public static int getVoiceEchoCount(LivingEntity entity) {
        int amp = getAmplifier(entity, VOICE_ECHO);
        if (amp < 0) {
            return 0;
        }
        return Mth.clamp(amp + 1, 1, 5);
    }

    /**
     * 初始化所有药水效果
     */
    public static boolean pierceDeath = false;

    public static void init() {
        // 把说话者侧的语音效果（重金属/回响）同步给所有客户端，
        // 否则听者客户端查不到说话者的效果，OpenAL 语音处理无法生效。
        org.agmas.noellesroles.voice.VoiceEffectSync.init();
        // 把伪装效果同步给所有客户端，否则观察者客户端查不到其他玩家的伪装，
        // 导致“伪装只有自己能看到”。
        io.wifi.starrailexpress.content.item.DisguiseEffectSync.init();
        // 把“脚步消失”效果同步给所有客户端，否则其它玩家侧的脚步声/疾跑粒子拦截查不到该效果。
        org.agmas.noellesroles.init.FootstepVanishEffectSync.init();
        // 把怀旧者“里世界标记”效果同步给所有客户端，否则其它客户端查不到怀旧者的里世界状态，
        // 导致手持物品仍显示 / 仍能被杀手透视。
        NostalgistBackworldEffectSync.init();
        WraithDimensionEffectSync.init();
        AllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (pierceDeath) {
                pierceDeath = false;
                return true;
            }
            if (deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)) {
                return true;
            }
            if (player.hasEffect(ModEffects.INVINCIBLE)) {
                var gameComponent = SREGameWorldComponent.KEY.get(player.level());
                if (gameComponent.isRole(killer, TMMRoles.LOOSE_END)) {
                    return true;
                }
                return false;
            }
            if (deathReason.equals(Noellesroles.id("bomb_death")))
                return true;
            if (player.hasEffect(ModEffects.TAROT_ASSEMBLY)) {
                if (player.position().z >= 19000)
                    return false;
            }
            return true;
        });
    }

    /**
     * 获取一个药水效果的实例
     * 
     * @param holder        药水效果。(ModEffects.xxx/MobEffects.xxx)
     * @param time          持续时间(ticks)。-1永久
     * @param amplifier     等级。0为1级
     * @param ambient       是否为环境效果，比如信标
     * @param showParticles 是否显示粒子
     * @param showIcon      是否显示图标
     * @return
     */
    public static MobEffectInstance of(Holder<MobEffect> holder, int time, int amplifier, boolean ambient,
            boolean showParticles, boolean showIcon) {
        return new MobEffectInstance(new MobEffectInstance(
                holder,
                time, // 持续时间（tick）
                amplifier, // 等级（0 = 速度 I）
                ambient, // ambient（环境效果，如信标）
                showParticles, // showParticles（显示粒子）
                showIcon // showIcon（显示图标）
        ));
    }
}
