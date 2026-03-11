package org.agmas.noellesroles.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;

import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 通用技能组件
 *
 * 用于管理玩家的技能冷却时间和使用次数
 * 该组件会自动在客户端和服务端之间同步
 *
 * 功能：
 * - 冷却时间管理（自动递减）
 * - 技能使用次数限制
 * - 自动同步到客户端（用于 HUD 显示）
 */
public class NoellesRolesAbilityPlayerComponent
        implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<NoellesRolesAbilityPlayerComponent> KEY = ModComponents.ABILITY;

    // 持有该组件的玩家
    private final Player player;

    // 技能冷却时间（tick）
    public int cooldown = 100;

    // 技能剩余使用次数（-1 表示无限制）
    public int charges = -1;

    // 最大使用次数（用于 HUD 显示）
    public int maxCharges = -1;

    // 状态
    public int status = -1;

    /**
     * 构造函数
     */
    public NoellesRolesAbilityPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void reset() {
        this.cooldown = 0;
        this.charges = -1;
        this.maxCharges = -1;
        this.status = -1;
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
    }

    /**
     * 设置冷却时间
     * 
     * @param ticks 冷却时间（tick），20 tick = 1 秒
     */
    public void setCooldown(int ticks) {
        this.cooldown = ticks;
        this.sync();
    }

    /**
     * 设置技能使用次数
     * 
     * @param charges 使用次数
     */
    public void setCharges(int charges) {
        this.charges = charges;
        this.maxCharges = charges;
        this.sync();
    }

    /**
     * 使用一次技能
     * 
     * @return 是否成功使用
     */
    public boolean useAbility() {
        if (cooldown > 0) {
            return false;
        }
        if (charges == 0) {
            return false;
        }
        if (charges > 0) {
            charges--;
        }
        sync();
        return true;
    }

    /**
     * 检查技能是否可用
     */
    public boolean canUseAbility() {
        return cooldown <= 0 && (charges == -1 || charges > 0);
    }

    public int getCooldown() {
        return this.cooldown;
    }

    public boolean hasCooldown() {
        return this.cooldown > 0;
    }

    /**
     * 获取冷却时间（秒）
     */
    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.ABILITY.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 服务端每 tick 减少冷却时间
        if (this.cooldown > 0) {
            this.cooldown--;
            // 每5秒同步一次（而不是每 tick），减少网络压力
            if (this.cooldown % 100 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }
        if (this.player.level().getGameTime() % 20 == 0) {
            if (GameFunctions.isPlayerAliveAndSurvival(this.player)) {
                GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(this.player.level());
                if (gameWorldComponent.isRole(this.player, ModRoles.WIND_YAOSE)) {
                    var effect = player.getEffect(MobEffects.INVISIBILITY);
                    if (effect == null || effect.getDuration() <= 30) {
                        player.addEffect(new MobEffectInstance(
                                MobEffects.INVISIBILITY,
                                60 * 20, // 持续时间 60s（tick）
                                1, // 等级（0 = 速度 I）
                                true, // ambient（环境效果，如信标）
                                false, // showParticles（显示粒子）
                                true // showIcon（显示图标）
                        ));
                    }
                }
                if (gameWorldComponent.isRole(this.player, ModRoles.OLDMAN)) {
                    var effect = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    if (effect == null || effect.getDuration() <= 30) {
                        player.addEffect(new MobEffectInstance(
                                MobEffects.MOVEMENT_SLOWDOWN,
                                60 * 20, // 持续时间 60s（tick）
                                1, // 等级（0 = 速度 I）
                                true, // ambient（环境效果，如信标）
                                false, // showParticles（显示粒子）
                                true // showIcon（显示图标）
                        ));
                    }
                }
            }
        }
    }

    @Override
    public void clientTick() {
        // 客户端也进行冷却计算（用于预测显示）
        if (this.cooldown > 1) {
            this.cooldown--;
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("charges", this.charges);
        tag.putInt("maxCharges", this.maxCharges);
        tag.putInt("status", this.status);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.charges = tag.contains("charges") ? tag.getInt("charges") : -1;
        this.maxCharges = tag.contains("maxCharges") ? tag.getInt("maxCharges") : -1;
        this.status = tag.contains("status") ? tag.getInt("status") : -1;
    }
}