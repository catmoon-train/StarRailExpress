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
import org.agmas.noellesroles.content.effects.PuppetWanderEffect;
import org.agmas.noellesroles.content.effects.SimpleMobEffect;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.game.backworld.BackworldOutlineEffectSync;
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
     * 傀儡游走（操控者·失控之躯）
     * - 有害效果
     * - 拥有者身体每 tick 自动朝随机方向缓慢走动、拥有者无法控制（配合 MOVE_BANED/TURN_BANED 等），
     *   带悬崖/危险探测避免走进虚空。行为见 {@link PuppetWanderEffect}。
     */
    public static final Holder<MobEffect> PUPPET_WANDER = register("puppet_wander",
            new PuppetWanderEffect());

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

    /**
     * 里世界·同界描边
     * - 中性效果，青白色
     * - 仅作为「客户端渲染标记」：当本地玩家自身也持有该效果时，才会给同样持有该效果的
     * 其他玩家绘制发光轮廓（类似原版发光，但只有里世界内的人能互相看见）。
     * - 由 {@code BackworldOutlineEffectSync} 同步给所有客户端（原版只会把效果同步给持有者本人）。
     * - 使用方：怀旧者里世界、布袋鬼里世界、归途旅人「旧日渡口 / 末班车」。
     */
    public static final Holder<MobEffect> BACKWORLD_OUTLINE = register("backworld_outline",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x7FE7E0));

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
     * 蜂鸣音效：把说话者的声音替换成跟随语调起伏的纯正弦音（pitch-tracking sine vocoder）。
     * 听不出具体内容，但能感知语调与情绪起伏。客户端处理见
     * {@code org.agmas.noellesroles.voice.client.BeepRobotVoiceClientReceiver}。
     */
    public static final Holder<MobEffect> VOICE_BEEP = register("voice_beep",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFD700));
    /**
     * 机器人音效：环形调制（ring modulation）+ 轻度低通，模拟机器人电子音，但保留可懂度。
     * 客户端处理见 {@code org.agmas.noellesroles.voice.client.BeepRobotVoiceClientReceiver}。
     */
    public static final Holder<MobEffect> VOICE_ROBOT = register("voice_robot",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x00CED1));
    /**
     * 头盔/远处语音：OpenAL 直接低通滤波，让声音变闷、像隔着头盔或很远。
     * 客户端处理见 {@code org.agmas.noellesroles.voice.VoiceExtraEffectsPlugin}。
     */
    public static final Holder<MobEffect> VOICE_HELMET = register("voice_helmet",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x9E9E9E));
    /**
     * 水下语音：低通 + 降低增益，模拟在水下说话。
     * 客户端处理见 {@code org.agmas.noellesroles.voice.VoiceExtraEffectsPlugin}。
     */
    public static final Holder<MobEffect> VOICE_UNDERWATER = register("voice_underwater",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x1E90FF));
    /**
     * 混响语音：OpenAL EFX REVERB 效果，让语音带空间回响。
     * 客户端处理见 {@code org.agmas.noellesroles.voice.VoiceExtraEffectsPlugin}。
     */
    public static final Holder<MobEffect> VOICE_REVERB = register("voice_reverb",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xB0E0E6));
    /**
     * 合成人声 / 自动调音：基频量化到最近半音后用 WSOLA 变调，像电子歌姬/机器人唱歌。
     * 客户端处理见 {@code org.agmas.noellesroles.voice.VoiceExtraEffectsPlugin}。
     */
    public static final Holder<MobEffect> VOICE_SYNTH = register("voice_synth",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFF69B4));
    /**
     * 失真语音：tanh 软削波，像电吉他失真/坏掉的收音机。
     * 客户端处理见 {@code org.agmas.noellesroles.voice.VoiceExtraEffectsPlugin}。
     */
    public static final Holder<MobEffect> VOICE_DISTORTION = register("voice_distortion",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFF4500));
    /**
     * 合唱语音：延迟线 + LFO 调制，像多个自己同时说话。
     * 客户端处理见 {@code org.agmas.noellesroles.voice.VoiceExtraEffectsPlugin}。
     */
    public static final Holder<MobEffect> VOICE_CHORUS = register("voice_chorus",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x7FFF00));
    /**
     * 颤音语音：幅度 LFO 调制，声音一抖一抖的。
     * 客户端处理见 {@code org.agmas.noellesroles.voice.VoiceExtraEffectsPlugin}。
     */
    public static final Holder<MobEffect> VOICE_TREMOLO = register("voice_tremolo",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFF00));
    /**
     * 口吃语音：重复小段音频，像机器人卡碟/结巴。
     * 客户端处理见 {@code org.agmas.noellesroles.voice.VoiceExtraEffectsPlugin}。
     */
    public static final Holder<MobEffect> VOICE_STUTTER = register("voice_stutter",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFF00FF));
    /**
     * 倒放语音：分块缓冲后反向播放，像倒放磁带。
     * 客户端处理见 {@code org.agmas.noellesroles.voice.VoiceExtraEffectsPlugin}。
     */
    public static final Holder<MobEffect> VOICE_REVERSE = register("voice_reverse",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x8A2BE2));
    /**
     * 氦气变声：用 WSOLA 实时升调（pitch shift），像吸了氦气一样尖细。
     * 变调倍率随等级提升（等级越高升得越尖）。
     * 客户端处理见 {@code org.agmas.noellesroles.voice.VoiceExtraEffectsPlugin}。
     */
    public static final Holder<MobEffect> VOICE_HELIUM = register("voice_helium",
            new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x9FE0FF));
    /**
     * 聊天禁止：拥有此效果的玩家发送的聊天消息不会被任何人看到
     */
    public static final Holder<MobEffect> CHAT_BAN = register("chat_ban",
            new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x666666));
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

    /** 头盔/远处语音等级（0 = 无效果，1~5 = amplifier+1）。 */
    public static int getVoiceHelmetLevel(LivingEntity entity) {
        int amp = getAmplifier(entity, VOICE_HELMET);
        return amp < 0 ? 0 : Mth.clamp(amp + 1, 1, 5);
    }

    /** 水下语音等级（0 = 无效果，1~5 = amplifier+1）。 */
    public static int getVoiceUnderwaterLevel(LivingEntity entity) {
        int amp = getAmplifier(entity, VOICE_UNDERWATER);
        return amp < 0 ? 0 : Mth.clamp(amp + 1, 1, 5);
    }

    /** 混响语音等级（0 = 无效果，1~5 = amplifier+1）。 */
    public static int getVoiceReverbLevel(LivingEntity entity) {
        int amp = getAmplifier(entity, VOICE_REVERB);
        return amp < 0 ? 0 : Mth.clamp(amp + 1, 1, 5);
    }

    /** 合成人声等级（0 = 无效果，1~5 = amplifier+1）。 */
    public static int getVoiceSynthLevel(LivingEntity entity) {
        int amp = getAmplifier(entity, VOICE_SYNTH);
        return amp < 0 ? 0 : Mth.clamp(amp + 1, 1, 5);
    }

    /** 失真语音等级（0 = 无效果，1~5 = amplifier+1）。 */
    public static int getVoiceDistortionLevel(LivingEntity entity) {
        int amp = getAmplifier(entity, VOICE_DISTORTION);
        return amp < 0 ? 0 : Mth.clamp(amp + 1, 1, 5);
    }

    /** 合唱语音等级（0 = 无效果，1~5 = amplifier+1）。 */
    public static int getVoiceChorusLevel(LivingEntity entity) {
        int amp = getAmplifier(entity, VOICE_CHORUS);
        return amp < 0 ? 0 : Mth.clamp(amp + 1, 1, 5);
    }

    /** 颤音语音等级（0 = 无效果，1~5 = amplifier+1）。 */
    public static int getVoiceTremoloLevel(LivingEntity entity) {
        int amp = getAmplifier(entity, VOICE_TREMOLO);
        return amp < 0 ? 0 : Mth.clamp(amp + 1, 1, 5);
    }

    /** 口吃语音等级（0 = 无效果，1~5 = amplifier+1）。 */
    public static int getVoiceStutterLevel(LivingEntity entity) {
        int amp = getAmplifier(entity, VOICE_STUTTER);
        return amp < 0 ? 0 : Mth.clamp(amp + 1, 1, 5);
    }

    /** 倒放语音等级（0 = 无效果，1~5 = amplifier+1）。 */
    public static int getVoiceReverseLevel(LivingEntity entity) {
        int amp = getAmplifier(entity, VOICE_REVERSE);
        return amp < 0 ? 0 : Mth.clamp(amp + 1, 1, 5);
    }

    /** 氦气变声等级（0 = 无效果，1~5 = amplifier+1）。 */
    public static int getVoiceHeliumLevel(LivingEntity entity) {
        int amp = getAmplifier(entity, VOICE_HELIUM);
        return amp < 0 ? 0 : Mth.clamp(amp + 1, 1, 5);
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
        BackworldOutlineEffectSync.init();
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
