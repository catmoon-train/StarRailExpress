package org.agmas.noellesroles.game.roles.killer.creeper;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.packet.CreateCreeperBombAreaPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 苦力怕组件
 *
 * 技能：按下技能键花费300金币引燃自身，6s后爆炸，将自己和范围5格内的玩家炸死。
 * 引燃后再次按下技能键可花费75金币立即引爆，且爆炸范围扩大1格（半径6格）。
 */
public class CreeperPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<CreeperPlayerComponent> KEY = ModComponents.CREEPER;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 是否已引燃 */
    public boolean ignited = false;

    /** 引燃剩余时间（tick） */
    public int igniteTimeLeft = 0;

    /** 是否为立即引爆（扩大范围） */
    private boolean instantDetonate = false;

    /** 爆炸倒计时（秒） */
    private static final int EXPLODE_TIME = 6 * 20; // 6秒

    /** 普通爆炸半径 */
    private static final float NORMAL_RADIUS = 5.0F;
    /** 立即引爆半径（+1格） */
    private static final float INSTANT_RADIUS = 6.0F;
    /** 立即引爆花费 */
    private static final int INSTANT_DETONATE_COST = 75;
    /** 引燃花费 */
    private static final int IGNITE_COST = 300;

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
        this.instantDetonate = false;
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
        this.instantDetonate = false;
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
     * 引燃自身 / 立即引爆
     * - 未引燃时：花费300金币引燃，6s后爆炸
     * - 已引燃时：花费75金币立即引爆，范围+1格
     */
    public boolean ignite() {
        if (!(player instanceof ServerPlayer))
            return false;

        // 死亡或旁观者状态下不允许
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return false;

        // 已引燃 → 尝试立即引爆
        if (ignited) {
            return tryInstantDetonate();
        }

        // 检查金币
        var shopComponent = io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance < IGNITE_COST)
            return false;

        // 扣金币
        shopComponent.addToBalance(-IGNITE_COST);

        // 引燃
        ignited = true;
        igniteTimeLeft = EXPLODE_TIME;
        instantDetonate = false;

        // 播放苦力怕引燃声音
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CREEPER_PRIMED, SoundSource.MASTER, 2.0F, 1.0F);

        return true;
    }

    /**
     * 立即引爆（需已引燃状态）
     */
    private boolean tryInstantDetonate() {
        var shopComponent = io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance < INSTANT_DETONATE_COST)
            return false;

        // 扣金币
        shopComponent.addToBalance(-INSTANT_DETONATE_COST);

        // 标记为立即引爆
        instantDetonate = true;
        explode();
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
        float radius = instantDetonate ? INSTANT_RADIUS : NORMAL_RADIUS;

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
        for (ServerPlayer p : serverLevel.players()) {

            double dist = p.distanceToSqr(pos);

            if (dist > 4096)
                continue; // 64格距离限制
            ServerPlayNetworking.send(p, new CreateCreeperBombAreaPacket(pos));
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
                instantDetonate = false;
                this.sync();
                return;
            }

            igniteTimeLeft--;
            if (igniteTimeLeft <= 0) {
                explode();
                ignited = false;
                instantDetonate = false;
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