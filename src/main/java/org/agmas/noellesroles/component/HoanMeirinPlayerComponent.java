package org.agmas.noellesroles.component;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.hoan_meirin.HoanMeirinFistPunchHandler;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.DeathInfo;
import io.wifi.starrailexpress.SRE;

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
public class HoanMeirinPlayerComponent
        implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<HoanMeirinPlayerComponent> KEY = ModComponents.hoan_meirin;

    // 持有该组件的玩家
    private final Player player;

    // 技能冷却时间（tick）
    public int cooldown = 100;
    public int loneyTime = 0;
    public int armor = 0;

    /**
     * 构造函数
     */
    public HoanMeirinPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void reset() {
        this.cooldown = 0;
        this.loneyTime = 0;
        this.armor = 1;
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
     * 获取冷却时间（秒）
     */
    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 服务端每 tick 减少冷却时间
        var gameWorldComponent = GameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning())
            return;
        if (!gameWorldComponent.isRole(player, ModRoles.HOAN_MEIRIN))
            return;
        boolean shouldSync = false;
        if (this.cooldown > 0) {
            this.cooldown--;
            // 每秒同步一次（而不是每 tick），减少网络压力
            if (this.cooldown % 100 == 0 || this.cooldown == 0) {
                shouldSync = true;
            }
        }
        if (GameFunctions.isPlayerAliveAndSurvival(player)) {
            int nearByPlayerCount = 0;
            for (var p : this.player.level().players()) {
                if (!GameFunctions.isPlayerAliveAndSurvival(p))
                    continue;
                if (p.getUUID().equals(player.getUUID()))
                    continue;
                if (p.distanceTo(player) <= 5) {
                    nearByPlayerCount++;
                    break;
                }
            }
            if (nearByPlayerCount <= 0) {
                this.loneyTime++;
                if (this.loneyTime % 100 == 0) {
                    shouldSync = true;
                }
            } else {
                this.loneyTime = 0;
            }
        }

        if (shouldSync)
            this.sync();
        if (this.loneyTime == 45 * 20) {
            this.player.displayClientMessage(
                    Component.translatable("message.hoan_meirin.tip_for_loney").withStyle(ChatFormatting.YELLOW), true);
        }
        if (this.loneyTime >= 60 * 20) {
            killPlayerBecauseLonely();
        }
    }

    public void killPlayerBecauseLonely() {
        this.loneyTime = 0;
        GameFunctions.killPlayer(player, true, null, Noellesroles.id("hoan_meirin_lonely"));
        this.sync();
    }

    @Override
    public void clientTick() {
        var gameWorldComponent = GameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning())
            return;
        if (!gameWorldComponent.isRole(player, ModRoles.HOAN_MEIRIN))
            return;
        if (!GameFunctions.isPlayerAliveAndSurvival(player))
            return;
        // 客户端也进行冷却计算（用于预测显示）
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        int nearByPlayerCount = 0;
        for (var p : this.player.level().players()) {
            if (!GameFunctions.isPlayerAliveAndSurvival(p))
                continue;
            if (player.getUUID().equals(p.getUUID()))
                continue;
            if (p.distanceTo(player) <= 5) {
                nearByPlayerCount++;
                break;
            }
        }
        if (nearByPlayerCount <= 0) {
            this.loneyTime++;
        } else {
            this.loneyTime = 0;
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("loneyTime", this.loneyTime);
        tag.putInt("armor", this.armor);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.loneyTime = tag.contains("loneyTime") ? tag.getInt("loneyTime") : 0;
        this.armor = tag.contains("armor") ? tag.getInt("armor") : 0;
    }

    public boolean triggerArmor(Player victim, Player killer, ResourceLocation deathReason) {
        if (this.armor > 0) {
            boolean cantDefend = SRE.canStickArmor.stream()
                    .anyMatch((pre) -> pre.test(new DeathInfo(victim, killer, deathReason)));
            if (cantDefend) {
                return false;
            }
            this.armor--;
            this.sync();
            player.level().playSound(null, player.blockPosition(), TMMSounds.ITEM_PSYCHO_ARMOUR, SoundSource.MASTER,
                    5.0F, 1.0F);
            SRE.REPLAY_MANAGER.breakArmor(player.getUUID());
            HoanMeirinFistPunchHandler.applyShockwave(player);
            return true;
        }
        return false;
    }
}