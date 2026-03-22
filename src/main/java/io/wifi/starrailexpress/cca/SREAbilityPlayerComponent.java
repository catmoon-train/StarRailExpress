package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
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
public class SREAbilityPlayerComponent
        implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<SREAbilityPlayerComponent> KEY = ModComponents.ABILITY;

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
    public SREAbilityPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        this.cooldown = 0;
        this.charges = -1;
        this.maxCharges = -1;
        this.status = -1;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
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
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent.isRunning()) {
            var role = gameWorldComponent.getRole(player);
            if (role != null) {
                role.clientGameTickEvent(player, gameWorldComponent);
            }
        }
    }

    @Override
    public void clientTick() {
        // 客户端也进行冷却计算（用于预测显示）
        if (this.cooldown > 1) {
            this.cooldown--;
        }

        if (SREGameWorldComponent.KEY.get(this.player.level()).isRunning()) {
            io.wifi.starrailexpress.api.RoleMethodDispatcher.callClientTick(this.player);
            var modifiers = WorldModifierComponent.KEY.get(this.player.level()).getModifiers(this.player);
            for (var mo : modifiers) {
                mo.clientGameTickEvent(player);
            }
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("charges", this.charges);
        tag.putInt("maxCharges", this.maxCharges);
        tag.putInt("status", this.status);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.charges = tag.contains("charges") ? tag.getInt("charges") : -1;
        this.maxCharges = tag.contains("maxCharges") ? tag.getInt("maxCharges") : -1;
        this.status = tag.contains("status") ? tag.getInt("status") : -1;
    }
}