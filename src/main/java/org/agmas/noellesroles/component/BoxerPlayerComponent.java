package org.agmas.noellesroles.component;

import  org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 拳击手组件
 *
 * 管理"钢筋铁骨"技能：
 * - 按下技能键后获得1.5秒（30 tick）无敌
 * - 无敌期间可以反弹任何死亡：
 *   - 对刀/棍棒攻击：给攻击者2秒缓慢4效果，使刀进入CD
 *   - 对其他死亡原因（毒药、枪击等）：同样免疫
 * - 开局冷却60秒（1200 tick）
 * - 使用后冷却120秒（2400 tick）
 */
public class BoxerPlayerComponent implements RoleComponent, ServerTickingComponent {
    
    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<BoxerPlayerComponent> KEY = ModComponents.BOXER;
    
    // ==================== 常量定义 ====================
    
    /** 开局冷却时间（60秒 = 1200 tick） */
    public static final int INITIAL_COOLDOWN = 1200;
    
    /** 使用后冷却时间（120秒 = 2400 tick） */
    public static final int ABILITY_COOLDOWN = 2400;
    
    /** 无敌持续时间（2.5秒 = 50 tick） */
    public static final int INVULNERABILITY_DURATION = 50;
    
    /** 反制效果：缓慢持续时间（2秒 = 40 tick） */
    public static final int COUNTER_SLOWNESS_DURATION = 40;
    
    /** 反制效果：缓慢等级（4级，索引为3） */
    public static final int COUNTER_SLOWNESS_AMPLIFIER = 3;
    
    /** 刀的冷却时间（使用原版刀的默认CD，60秒 = 1200 tick） */
    public static final int KNIFE_COOLDOWN = 1200;
    
    // ==================== 状态变量 ====================
    
    private final Player player;
    
    /** 技能冷却时间（tick） */
    public int cooldown = 0;
    
    /** 无敌剩余时间（tick） */
    public int invulnerabilityTicks = 0;
    
    /** 是否处于无敌状态 */
    public boolean isInvulnerable = false;
    
    /**
     * 构造函数
     */
    public BoxerPlayerComponent(Player player) {
        this.player = player;
    }
    
    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void reset() {
        this.cooldown = INITIAL_COOLDOWN; // 开局60秒冷却
        this.invulnerabilityTicks = 0;
        this.isInvulnerable = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    /**
     * 检查技能是否可用
     */

    public boolean canUseAbility() {
        return cooldown <= 0 && !isInvulnerable;
    }
    
    /**
     * 使用钢筋铁骨技能
     * @return 是否成功使用
     */
    public boolean useAbility() {
        if (!canUseAbility()) {
            return false;
        }
        
        // 验证是拳击手
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.BOXER)) {
            return false;
        }
        
        // 激活无敌
        this.isInvulnerable = true;
        this.invulnerabilityTicks = INVULNERABILITY_DURATION;
        
        // 设置冷却
        this.cooldown = ABILITY_COOLDOWN;
        
        // 发送消息
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.boxer.ability_activated"), true);
        }
        
        this.sync();
        return true;
    }
    
    /**
     * 处理反制攻击
     * 当无敌期间受到刀/棍棒攻击时调用
     *
     * @param attacker 攻击者
     * @param weapon 攻击者使用的武器
     * @return true 表示成功反制，攻击应被取消
     */
    public boolean handleCounterAttack(Player attacker, ItemStack weapon) {
        if (!isInvulnerable || invulnerabilityTicks <= 0) {
            return false;
        }
        
        // 给攻击者施加缓慢4效果，持续2秒
        attacker.addEffect(new MobEffectInstance(
            MobEffects.MOVEMENT_SLOWDOWN,
            COUNTER_SLOWNESS_DURATION,
            COUNTER_SLOWNESS_AMPLIFIER,
            false,
            true,
            true
        ));
        
        // 如果是刀，让刀进入CD
        if (weapon.is(io.wifi.starrailexpress.index.TMMItems.KNIFE)) {
            // 使用物品冷却系统
            attacker.getCooldowns().addCooldown(weapon.getItem(), KNIFE_COOLDOWN);
            
            // 发送消息给攻击者
            if (attacker instanceof ServerPlayer serverAttacker) {
                serverAttacker.displayClientMessage(Component.translatable("message.noellesroles.boxer.counter_knife"), true);
            }
        } else {
            // 棍棒攻击
            if (attacker instanceof ServerPlayer serverAttacker) {
                serverAttacker.displayClientMessage(Component.translatable("message.noellesroles.boxer.counter_bat"), true);
            }
        }
        
        // 发送消息给拳击手
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.boxer.counter_success", attacker.getName()), true);
        }
        
        return true;
    }
    
    /**
     * 处理任何死亡的反制
     * 钢筋铁骨期间可以反弹任何死亡
     *
     * @param deathReason 死亡原因
     */
    public void handleAnyDeathCounter(ResourceLocation deathReason) {
        if (!isInvulnerable || invulnerabilityTicks <= 0) {
            return;
        }
        
        // 播放反弹音效
        player.level().playSound(null, player.blockPosition(),
            io.wifi.starrailexpress.index.TMMSounds.ITEM_PSYCHO_ARMOUR,
            SoundSource.MASTER, 5.0F, 1.0F);
        
        // 发送消息给拳击手
        if (player instanceof ServerPlayer serverPlayer) {
            // 根据死亡原因显示不同消息
            String deathType = getDeathTypeName(deathReason);
            serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.boxer.blocked_death", deathType)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                true
            );
        }
    }
    
    /**
     * 获取死亡类型名称
     */
    private String getDeathTypeName(ResourceLocation deathReason) {
        if (deathReason == null) return "未知";
        
        String path = deathReason.getPath();
        return switch (path) {
            case "knife" -> "刀刺";
            case "bat" -> "棍击";
            case "revolver" -> "枪击";
            case "poison" -> "毒药";
            case "fall" -> "坠落";
            case "fire" -> "火焰";
            case "explosion" -> "爆炸";
            default -> path;
        };
    }
    
    /**
     * 获取冷却时间（秒）
     */
    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }
    
    /**
     * 获取无敌剩余时间（秒）
     */
    public float getInvulnerabilitySeconds() {
        return invulnerabilityTicks / 20.0f;
    }
    
    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.BOXER.sync(this.player);
    }
    
    // ==================== Tick 处理 ====================

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void serverTick() {
        // 减少冷却时间
        if (this.cooldown > 0) {
            this.cooldown--;
            // 每秒同步一次，减少网络压力
            if (this.cooldown % 20 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }
        
        // 减少无敌时间
        if (this.invulnerabilityTicks > 0) {
            this.invulnerabilityTicks--;
            
            // 无敌结束
            if (this.invulnerabilityTicks <= 0) {
                this.isInvulnerable = false;
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.boxer.ability_ended"), true);
                }
                this.sync();
            }
        }
    }
    
    // ==================== NBT 序列化 ====================
    
    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("invulnerabilityTicks", this.invulnerabilityTicks);
        tag.putBoolean("isInvulnerable", this.isInvulnerable);
    }
    
    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.invulnerabilityTicks = tag.contains("invulnerabilityTicks") ? tag.getInt("invulnerabilityTicks") : 0;
        this.isInvulnerable = tag.contains("isInvulnerable") && tag.getBoolean("isInvulnerable");
    }
}