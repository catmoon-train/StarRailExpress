package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 苦力怕组件
 *
 * 技能：按下技能键花费300金币引燃自身，10s后爆炸
 */
public class CreeperPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<CreeperPlayerComponent> KEY = ModComponents.CREEPER;

    // ==================== 状态变量 ====================

    // @Override
    // public boolean shouldSyncWith(ServerPlayer nosync) {
    // return false;
    // }

    private final Player player;

    /** 是否已引燃 */
    public boolean ignited = false;

    /** 引燃剩余时间（tick） */
    public int igniteTimeLeft = 0;

    /** 爆炸倒计时（秒） */
    private static final int EXPLODE_TIME = 10 * 20; // 10秒

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 构造函数
     */
    public CreeperPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.ignited = false;
        this.igniteTimeLeft = 0;
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
        this.ignited = false;
        this.igniteTimeLeft = 0;
        this.sync();
    }

    /**
     * 检查是否是活跃的苦力怕
     */
    public boolean isActiveCreeper() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.CREEPER);
    }

    /**
     * 引燃自身
     */
    public boolean ignite() {
        if (ignited || !(player instanceof ServerPlayer))
            return false;

        // 检查金币
        var shopComponent = io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance < 300)
            return false;

        // 扣金币
        shopComponent.addToBalance(-300);

        // 引燃
        ignited = true;
        igniteTimeLeft = EXPLODE_TIME;

        // 播放TNT点燃声音
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TNT_PRIMED, SoundSource.MASTER, 1.0F, 1.0F);

        // this.sync();
        return true;
    }

    /**
     * 执行爆炸
     */
    private void explode() {
        if (!(player instanceof ServerPlayer))
            return;

        Vec3 pos = player.position();
        float radius = 4.0F;

        // 伤害玩家
        for (Player target : player.level().players()) {
            double distance = target.distanceToSqr(pos);
            if (distance <= radius * radius) {
                // 杀死玩家
                io.wifi.starrailexpress.game.GameUtils.killPlayer(target, true, player,
                        io.wifi.starrailexpress.game.GameConstants.DeathReasons.GRENADE);
            }
        }

        // 播放爆炸声音
        player.level().playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, 1.0F);
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        // ModComponents.CREEPER.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        if (!isActiveCreeper())
            return;

        if (ignited) {
            igniteTimeLeft--;
            if (igniteTimeLeft <= 0) {
                explode();
                ignited = false;
                this.sync();
            } else {
                // this.sync(); // 同步倒计时
            }
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("ignited", this.ignited);
        tag.putInt("igniteTimeLeft", this.igniteTimeLeft);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.ignited = tag.contains("ignited") && tag.getBoolean("ignited");
        this.igniteTimeLeft = tag.getInt("igniteTimeLeft");
    }

    @Override
    public void readFromNbt(CompoundTag tag, Provider registryLookup) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, Provider registryLookup) {
    }

    @Override
    public void clientTick() {
        if (this.igniteTimeLeft > 0) {
            this.igniteTimeLeft--;
        }
    }
}