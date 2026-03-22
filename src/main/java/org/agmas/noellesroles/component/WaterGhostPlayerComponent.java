package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 水鬼玩家组件
 *
 * 杀手阵营，假心情，默认冲刺时间
 *
 * 武器：激流2三叉戟（Mixin实现）
 *
 * 商店：可花费100金币购买开锁器，150金币购买下雨
 *
 * 技能：按下技能键获得10秒海豚的恩惠1，冷却40秒
 *
 * 被动：在非水中环境超过90秒时会死亡（死因：干涸而死）
 */
public class WaterGhostPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 */
    public static final ComponentKey<WaterGhostPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "water_ghost"),
            WaterGhostPlayerComponent.class);

    /** 下雨持续时间（20秒 = 400 tick） */
    public static final int RAIN_DURATION = 20 * 20;

    /** 下雨冷却时间（60秒 = 1200 tick） */
    public static final int RAIN_COOLDOWN = 60 * 20;

    /** 技能冷却时间（40秒 = 800 tick） */
    public static final int SKILL_COOLDOWN = 40 * 20;

    /** 技能持续时间（10秒 = 200 tick） */
    public static final int SKILL_DURATION = 10 * 20;

    /** 干涸死亡时间（60秒 = 1200 tick） */
    public static final int DRY_DEATH_TIME = 60 * 20;

    private final Player player;

    /** 技能冷却计时器（tick） */
    private int skillCooldown = 0;

    /** 技能剩余持续时间（tick） */
    private int skillDuration = 0;

    /** 下雨持续时间（tick） */
    private int rainDuration = 0;

    /** 下雨冷却计时器（tick） */
    private int rainCooldown = 0;

    /** 非水中计时器（tick） */
    private int outOfWaterTimer = 0;

    /**
     * 构造函数
     */
    public WaterGhostPlayerComponent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.skillCooldown = tag.getInt("SkillCooldown");
        this.skillDuration = tag.getInt("SkillDuration");
        this.rainDuration = tag.getInt("RainDuration");
        this.outOfWaterTimer = tag.getInt("OutOfWaterTimer");
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("SkillCooldown", this.skillCooldown);
        tag.putInt("SkillDuration", this.skillDuration);
        tag.putInt("RainDuration", this.rainDuration);
        tag.putInt("OutOfWaterTimer", this.outOfWaterTimer);
    }

    @Override
    public void init() {
        this.skillCooldown = 0;
        this.skillDuration = 0;
        this.rainDuration = 0;
        this.rainCooldown = 0;
        this.outOfWaterTimer = 0;
        sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void serverTick() {
        if (!(player.level() instanceof ServerLevel serverLevel))
            return;

        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent == null)
            return;
        if (!gameWorldComponent.isRunning())
            return;

        // 检查玩家是否是水鬼角色
        if (!gameWorldComponent.isRole(player, ModRoles.WATER_GHOST)) {
            return;
        }

        // 检查玩家是否存活
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;

        boolean shouldSync = false;

        // 处理技能冷却
        if (skillCooldown > 0) {
            skillCooldown--;
            if (skillCooldown % 20 == 0) {
                shouldSync = true;
            }
        }

        // 处理技能持续时间
        if (skillDuration > 0) {
            skillDuration--;
            if (skillDuration == 0) {
                // 技能结束，移除海豚的恩惠效果
                player.removeEffect(MobEffects.DOLPHINS_GRACE);
                if (serverPlayer != null) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.water_ghost.skill_end")
                                    .withStyle(ChatFormatting.AQUA),
                            true);
                }
                shouldSync = true;
            }
        }

        // 处理下雨持续时间
        if (rainDuration > 0) {
            rainDuration--;
            if (rainDuration == 0) {
                // 下雨结束
                serverLevel.setWeatherParameters(6000, 0, false, false); // 晴天

                if (serverPlayer != null) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.water_ghost.rain_end")
                                    .withStyle(ChatFormatting.BLUE),
                            true);
                }
                shouldSync = true;
            }
        }

        // 处理下雨冷却
        if (rainCooldown > 0) {
            rainCooldown--;
            if (rainCooldown % 20 == 0) {
                shouldSync = true;
            }
        }

        // 处理干涸死亡
        boolean isInWater = isInWater();
        if (isInWater) {
            // 在水中，重置计时器
            if (outOfWaterTimer > 0) {
                outOfWaterTimer = 0;
                // shouldSync = true;
            }
        } else {
            // 不在水中，增加计时器
            outOfWaterTimer++;

            // 检查是否需要警告（30秒、60秒）
            if (outOfWaterTimer == 30 * 20) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.water_ghost.warning_30s")
                                .withStyle(ChatFormatting.YELLOW),
                        true);
                shouldSync = true;
            } else if (outOfWaterTimer == 60 * 20) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.water_ghost.warning_60s")
                                .withStyle(ChatFormatting.RED),
                        true);
                shouldSync = true;
            }

            // 检查是否干涸死亡
            if (outOfWaterTimer >= DRY_DEATH_TIME) {
                // 死亡
                GameUtils.killPlayer(serverPlayer, false, null,
                        Noellesroles.id("dry_death"));
                return;
            }

            // 每10秒同步一次
            if (outOfWaterTimer % 200 == 0) {
                shouldSync = true;
            }
        }

        if (shouldSync) {
            sync();
        }
    }

    /**
     * 使用技能
     */
    public void useSkill() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 检查冷却
        if (skillCooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.water_ghost.cooldown",
                            (skillCooldown + 19) / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 播放技能音效
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.DOLPHIN_PLAY, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 给予海豚的恩惠效果，持续10秒
        player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, SKILL_DURATION, 0, true, true));

        // 设置冷却和持续时间
        skillCooldown = SKILL_COOLDOWN;
        skillDuration = SKILL_DURATION;

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.water_ghost.skill_used")
                        .withStyle(ChatFormatting.AQUA),
                true);

        sync();
    }

    /**
     * 购买下雨
     */
    public boolean buyRain() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;

        // 检查冷却时间
        if (this.player.getCooldowns().isOnCooldown(Items.WATER_BUCKET)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.water_ghost.rain_cooldown", rainCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 检查金币
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance < 150) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.water_ghost.insufficient_funds", 150)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 不需要扣除金币，不然会扣除双份

        // 激活下雨（参考ma_chen_xu的狂热下雨，但只保留下雨效果）
        ServerLevel serverLevel = serverPlayer.serverLevel();
        // 使用setWeatherParameters设置下雨（第一个参数为晴天时长，第二个为雨天时长）

        serverLevel.setWeatherParameters(0, RAIN_DURATION, true, false); // 下雨 不打雷
        rainDuration = RAIN_DURATION;

        // 设置冷却时间
        player.getCooldowns().addCooldown(Items.WATER_BUCKET, RAIN_COOLDOWN);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.water_ghost.rain_purchased")
                        .withStyle(ChatFormatting.BLUE),
                true);

        sync();
        return true;
    }

    /**
     * 获取技能冷却剩余时间（秒）
     */
    public int getSkillCooldownRemaining() {
        return (skillCooldown + 19) / 20;
    }

    /**
     * 获取技能剩余持续时间（秒）
     */
    public int getSkillDurationRemaining() {
        return (skillDuration + 19) / 20;
    }

    /**
     * 获取干涸死亡剩余时间（秒）
     */
    public int getDryDeathRemaining() {
        return DRY_DEATH_TIME / 20 - (outOfWaterTimer + 19) / 20;
    }

    /**
     * 同步组件数据到客户端
     */
    private void sync() {
        if (!player.level().isClientSide) {
            KEY.sync(player);
        }
    }

    @Override
    public void clientTick() {
        // 客户端计时器递减，用于HUD显示
        if (skillCooldown > 1) {
            skillCooldown--;
        }
        if (skillDuration > 1) {
            skillDuration--;
        }

        boolean isInWater = isInWater();
        if (isInWater) {
            // 在水中，重置计时器
            if (outOfWaterTimer > 0) {
                outOfWaterTimer = 0;
            }
        } else {
            if (outOfWaterTimer < DRY_DEATH_TIME) {
                outOfWaterTimer++;
            }
        }
    }

    private boolean isInWater() {
        return player.isInWater()
                || player.isUnderWater()
                || (player.level().isRaining() && player.level().canSeeSky(player.blockPosition()));
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
