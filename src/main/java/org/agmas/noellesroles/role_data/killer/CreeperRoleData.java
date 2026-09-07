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

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.Scheduler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.CreateCreeperBombAreaPacket;
import org.agmas.noellesroles.role.bouns.BounsRoles;

import java.util.ArrayList;
import java.util.List;

public class CreeperRoleData extends SimpleRoleData {

    // ==================== 状态变量 ====================

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

    /**
     * 构造函数
     */
    public CreeperRoleData(RoleDataContext context) {
        super(context);
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
        if (player == null)
            return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, BounsRoles.CREEPER);
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

    // ==================== Tick 处理 ====================

    /**
     * Execute an explosion at a fixed position and return the number of players hit.
     * Hit counting happens before applying kills, so death prevention still counts as
     * a hit.
     */
    private static int explodeAt(ServerLevel level, Vec3 pos, Player damageSource, float radius) {
        List<Player> targets = new ArrayList<>();
        for (Player target : level.players()) {
            if (!target.isSpectator() && target != damageSource
                    && target.distanceToSqr(pos) <= radius * radius) {
                targets.add(target);
            }
        }

        for (Player target : targets) {
            GameUtils.killPlayer(target, true, damageSource, GameConstants.DeathReasons.GRENADE);
        }

        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.MASTER, 4.0F, 1.0F);
        spawnRainbowParticlesAt(level, pos);
        return targets.size();
    }

    /** Trigger the death explosion caused by a revolver shot. */
    public static void onRevolverDeath(ServerPlayer creeper) {
        if (creeper == null || !(creeper.level() instanceof ServerLevel level))
            return;

        Vec3 explosionPos = creeper.position();
        int hitCount = explodeAt(level, explosionPos, creeper, NORMAL_RADIUS);
        if (hitCount > 5) {
            Scheduler.schedule(() -> explodeAt(level, explosionPos, creeper, NORMAL_RADIUS), 20);
        }
    }

    private static void spawnRainbowParticlesAt(ServerLevel level, Vec3 pos) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos) <= 4096) {
                ServerPlayNetworking.send(p, new CreateCreeperBombAreaPacket(pos));
            }
        }
    }

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
    public void clientTick() {
        if (this.igniteTimeLeft > 0) {
            this.igniteTimeLeft--;
        }
    }
}
