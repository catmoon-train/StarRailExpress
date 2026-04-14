package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import io.wifi.starrailexpress.game.GameUtils;
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
    private static final int EXPLODE_TIME = 6 * 20; // 6秒

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

        // 播放苦力怕引燃声音
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.CREEPER_PRIMED, SoundSource.MASTER, 2.0F, 1.0F);

        // this.sync();
        return true;
    }

    /**
     * 执行爆炸
     */
    private void explode() {
        if (!(player instanceof ServerPlayer))
            return;
        // 如果引燃者已死亡或不处于生存状态，则取消爆炸
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        Vec3 pos = player.position();
        float radius = 5.0F;

        // 伤害玩家（跳过旁观者）
        for (Player target : player.level().players()) {
            if (target.isSpectator())
                continue;
            double distance = target.distanceToSqr(pos);
            if (distance <= radius * radius) {
                // 杀死玩家
                io.wifi.starrailexpress.game.GameUtils.killPlayer(target, true, player,
                        io.wifi.starrailexpress.game.GameConstants.DeathReasons.GRENADE);
            }
        }

        // 播放苦力怕爆炸声音
        player.level().playSound(null, pos.x, pos.y, pos.z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.MASTER, 4.0F, 1.0F);

        // 生成彩虹粒子效果
        spawnRainbowParticles(pos);

        // 让引燃者自爆死亡，死因为自爆
        io.wifi.starrailexpress.game.GameUtils.killPlayer(player, true, player,
            io.wifi.starrailexpress.game.GameConstants.DeathReasons.SELF_EXPLOSION);
    }

    /**
     * 生成彩虹粒子效果
     */
    private void spawnRainbowParticles(Vec3 pos) {
        if (!(player.level() instanceof ServerLevel serverLevel))
            return;

        // 向所有玩家发送粒子效果
        for (Player p : serverLevel.players()) {
            double dist = p.distanceToSqr(pos);
            if (dist > 4096) continue; // 64格距离限制

            for (int i = 0; i < 20; i++) {
                // 随机偏移位置
                double offsetX = (serverLevel.random.nextDouble() - 0.5) * 6;
                double offsetY = serverLevel.random.nextDouble() * 4;
                double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 6;

                // 随机速度
                double speed = 0.3;
                double vx = (serverLevel.random.nextDouble() - 0.5) * speed;
                double vy = serverLevel.random.nextDouble() * speed;
                double vz = (serverLevel.random.nextDouble() - 0.5) * speed;

                // 交替使用不同粒子效果
                switch (i % 8) {
                    case 0 -> serverLevel.sendParticles(ParticleTypes.END_ROD, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 1, vx, vy, vz, 0.01);
                    case 1 -> serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 1, vx, vy, vz, 0.01);
                    case 2 -> serverLevel.sendParticles(ParticleTypes.FLAME, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 1, vx, vy, vz, 0.01);
                    case 3 -> serverLevel.sendParticles(ParticleTypes.SOUL, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 1, vx, vy, vz, 0.01);
                    case 4 -> serverLevel.sendParticles(ParticleTypes.WITCH, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 1, vx, vy, vz, 0.01);
                    case 5 -> serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 1, vx, vy, vz, 0.01);
                    case 6 -> serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 1, vx, vy, vz, 0.01);
                    case 7 -> serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 1, vx, vy, vz, 0.01);
                }
            }
        }
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
            // 如果玩家在引燃期间死亡，则取消即将发生的爆炸
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                ignited = false;
                this.sync();
                return;
            }

            igniteTimeLeft--;
            if (igniteTimeLeft <= 0) {
                explode();
                ignited = false;
                this.sync();
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