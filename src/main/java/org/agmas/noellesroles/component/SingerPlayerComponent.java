package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 歌手组件
 *
 * 主动技能：随机播放原版唱片音乐（60秒冷却）
 * 
 * 歌手为好人阵营（乘客阵营）
 */
public class SingerPlayerComponent implements RoleComponent, ServerTickingComponent {
    private static Holder.Reference<SoundEvent> registerForHolder(ResourceLocation resourceLocation) {
        return registerForHolder(resourceLocation, resourceLocation);
    }

    private static Holder.Reference<SoundEvent> registerForHolder(ResourceLocation resourceLocation,
            ResourceLocation resourceLocation2) {
        return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, resourceLocation,
                SoundEvent.createVariableRangeEvent(resourceLocation2));
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<SingerPlayerComponent> KEY = ModComponents.SINGER;

    // ==================== 常量定义 ====================
    public static final Holder.Reference<SoundEvent> MUSIC_DISC_LAVA_CHICKEN_CUT = registerForHolder(
            ResourceLocation.withDefaultNamespace("music_disc.lava_chicken_cut"));
    public static final Holder.Reference<SoundEvent> MUSIC_DISC_CREATOR_CUT = registerForHolder(
            ResourceLocation.withDefaultNamespace("music_disc.creator_cut"));
    public static final Holder.Reference<SoundEvent> MUSIC_DISC_BROKEN_MOON = registerForHolder(
            ResourceLocation.withDefaultNamespace("music_disc.broken_moon"));
    public static final Holder.Reference<SoundEvent> MUSIC_DISC_PIGSTEP_CUT = registerForHolder(
            ResourceLocation.withDefaultNamespace("music_disc.pigstep_cut"));
    /** 主动技能冷却时间（4800 tick） */
    public static final int ABILITY_COOLDOWN = 4800;

    /** 音乐播放范围（格） */
    public static final double MUSIC_RANGE = 24.0;

    /** 音乐持续时间（唱片最短约60秒，设为65秒 = 1300 tick） */
    public static final int MUSIC_DURATION = 1200;

    /** 速度效果范围（格） */
    public static final double SPEED_EFFECT_RANGE = 16.0;

    // ==================== 唱片音乐列表 ====================
    private static final SoundEvent[] MUSIC_DISCS = {
            MUSIC_DISC_PIGSTEP_CUT.value(),
            MUSIC_DISC_LAVA_CHICKEN_CUT.value(),
            MUSIC_DISC_CREATOR_CUT.value(),
            MUSIC_DISC_BROKEN_MOON.value()
    };

    // ==================== 状态变量 ====================

    private final Player player;

    /** 主动技能冷却时间（tick） */
    public int abilityCooldown = 0;

    /** 是否已激活（角色分配后） */
    public boolean isActive = false;

    /** 当前正在播放的音乐索引（-1表示没有播放） */
    public int currentMusicIndex = -1;

    /** 音乐剩余时间（tick） - 用于追踪音乐播放状态和给予速度效果 */
    public int musicRemainingTicks = 0;
    /** 音乐类型 - 用于追踪音乐播放状态和给予效果 */
    public int musicType = -1;

    /**
     * 构造函数
     */
    public SingerPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        this.abilityCooldown = 0;
        this.isActive = true;
        this.currentMusicIndex = -1;
        this.musicRemainingTicks = 0;
        this.sync();
    }

    @Override
    public void clear() {
        clearAll();
    }

    /**
     * 清除所有状态
     */
    public void clearAll() {
        this.abilityCooldown = 0;
        this.isActive = false;
        this.currentMusicIndex = -1;
        this.musicRemainingTicks = 0;
        this.sync();
    }

    /**
     * 检查是否为激活的歌手角色
     */
    public boolean isActiveSinger() {
        if (!isActive || player == null || player.level().isClientSide())
            return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.SINGER);
    }

    /**
     * 检查主动技能是否可用
     * 注意：此方法在客户端和服务端都可以调用
     * 客户端只检查冷却和激活状态，服务端安全检查在网络包处理器中进行
     */
    public boolean canUseAbility() {
        return abilityCooldown <= 0 && isActive;
    }

    /**
     * 使用主动技能 - 随机播放原版唱片音乐
     * 
     * @return 是否成功使用
     */
    public boolean useAbility() {
        if (!canUseAbility()) {
            return false;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        ServerLevel world = serverPlayer.serverLevel();

        // 随机选择一首音乐
        int musicIndex = this.musicType - 1;
        SoundEvent music = MUSIC_DISCS[musicIndex];

        // 播放音乐（给所有在范围内的玩家听到）
        world.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                music,
                SoundSource.RECORDS,
                4.0F, // 音量
                1.0F // 音调
        );

        // 设置冷却
        // this.abilityCooldown = ABILITY_COOLDOWN;

        // 设置音乐持续时间（用于持续给予速度效果）
        this.musicRemainingTicks = MUSIC_DURATION;

        // 发送消息给歌手玩家
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.singer.music_played")
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                true);

        this.sync();
        return true;
    }

    /**
     * 获取冷却时间（秒）
     */
    public float getCooldownSeconds() {
        return abilityCooldown / 20.0f;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            ModComponents.SINGER.sync(this.player);
        }
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        if (!isActiveSinger())
            return;

        // 减少主动技能冷却时间
        if (this.abilityCooldown > 0) {
            this.abilityCooldown--;
            // 每秒同步一次，减少网络压力
            if (this.abilityCooldown % 20 == 0 || this.abilityCooldown == 0) {
                this.sync();
            }
        }

        // 音乐播放期间给周围玩家速度效果
        if (this.musicRemainingTicks > 0) {
            this.musicRemainingTicks--;

            // 每秒给一次速度效果（持续2秒，确保连续覆盖）
            if (this.musicRemainingTicks % 20 == 0) {
                applySpeedEffectToNearbyPlayers();
                this.sync();
            }

            // 音乐结束时重置状态
            if (this.musicRemainingTicks == 0) {
                this.currentMusicIndex = -1;
                this.sync();
            }
        }
    }

    /**
     * 给周围玩家速度效果
     */
    private void applySpeedEffectToNearbyPlayers() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        ServerLevel world = serverPlayer.serverLevel();

        for (Player target : world.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;

            double distance = target.distanceToSqr(player);
            if (distance > SPEED_EFFECT_RANGE * SPEED_EFFECT_RANGE)
                continue;
            if (target.getUUID() != this.player.getUUID())
                target.displayClientMessage(
                        Component.translatable("message.noellesroles.singer.music_heard")
                                .withStyle(ChatFormatting.LIGHT_PURPLE),
                        true);
            // 给予速度 I 效果（持续2.5秒 = 50 tick，确保连续覆盖）
            switch (musicType) {
                case 1:
                    target.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SPEED,
                            50, // 持续时间（tick）
                            0, // 等级（0 = 速度 I）
                            false, // ambient（环境效果，如信标）
                            true, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    ));
                    break;
                case 2:
                    var pmd = SREPlayerMoodComponent.KEY.get(target);
                    if (pmd != null) {
                        float nmd = pmd.getMood();
                        nmd += 0.01; // 60 * 0.01 = 0.6
                        if (nmd >= 1F) {
                            nmd = 1F;
                        }
                        pmd.setMood(nmd);
                    }
                    break;
                case 3:
                    target.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN,
                            50, // 持续时间（tick）
                            0, // 等级（0 = 速度 I）
                            false, // ambient（环境效果，如信标）
                            true, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    ));
                    break;
                case 4:
                    target.addEffect(new MobEffectInstance(
                            MobEffects.DIG_SLOWDOWN,
                            50, // 持续时间（tick）
                            4, // 等级（0 = 速度 I）
                            false, // ambient（环境效果，如信标）
                            true, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    ));
                    break;
                default:
            }

        }
    }

    /**
     * 检查是否正在播放音乐
     */

    public boolean isPlayingMusic() {
        return musicRemainingTicks > 0;
    }

    public static boolean buyDisc(@NotNull Player player, int discId) {
        player.getCooldowns().addCooldown(ModItems.SINGER_MUSIC_DISC,
                60 * 20);
        SingerPlayerComponent spc = SingerPlayerComponent.KEY.get(player);
        if (spc == null) {
            return false;
        }
        if (!spc.isActiveSinger())
            return false;
        if (spc.musicRemainingTicks > 0)
            return false;
        spc.musicRemainingTicks = 60 * 20;// 60s
        spc.musicType = discId;
        spc.useAbility();
        SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(), BuiltInRegistries.ITEM.getKey(ModItems.SINGER_MUSIC_DISC));

        return true;
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("abilityCooldown", this.abilityCooldown);
        tag.putBoolean("isActive", this.isActive);
        tag.putInt("currentMusicIndex", this.currentMusicIndex);
        tag.putInt("musicRemainingTicks", this.musicRemainingTicks);
        tag.putInt("musicType", this.musicType);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.abilityCooldown = tag.contains("abilityCooldown") ? tag.getInt("abilityCooldown") : 0;
        this.musicType = tag.contains("musicType") ? tag.getInt("musicType") : -1;
        this.isActive = tag.contains("isActive") && tag.getBoolean("isActive");
        this.currentMusicIndex = tag.contains("currentMusicIndex") ? tag.getInt("currentMusicIndex") : -1;
        this.musicRemainingTicks = tag.contains("musicRemainingTicks") ? tag.getInt("musicRemainingTicks") : 0;
    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}