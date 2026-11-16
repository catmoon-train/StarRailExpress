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

package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.entity.MechanicalBirdEntity;
import org.agmas.noellesroles.game.roles.neutral.silver_wing.SilverWingRules;
import org.agmas.noellesroles.utils.MoneyUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class SilverWingRoleData extends SimpleRoleData {
    /**
     * 非银翼玩家使用「机械小鸟」时的临时会话（道具已不限制职业）。
     *
     * <p>activeBirdId 原本只挂在银翼的 RoleData 上；其它职业用服务端临时会话承载，
     * 无需同步（飞行控制 / 相机 / 爆炸全部在服务端及实体侧完成）。</p>
     */
    private static final Map<UUID, SilverWingRoleData> GENERIC_BIRDS = new HashMap<>();

    /** 查会话：银翼用自身 RoleData，其它职业查临时表（不创建）。 */
    public static SilverWingRoleData resolve(Player player) {
        if (player == null) {
            return null;
        }
        SilverWingRoleData own = RoleData.getNullable(SilverWingRoleData.class, player);
        return own != null ? own : GENERIC_BIRDS.get(player.getUUID());
    }

    /** 取/建会话：机械小鸟放出时调用。 */
    public static SilverWingRoleData resolveOrCreate(Player player) {
        SilverWingRoleData own = RoleData.getNullable(SilverWingRoleData.class, player);
        if (own != null) {
            return own;
        }
        GENERIC_BIRDS.values().removeIf(data -> !data.hasActiveBird());
        return GENERIC_BIRDS.computeIfAbsent(player.getUUID(),
                id -> new SilverWingRoleData(new RoleDataContext(player, null, () -> {
                }, sp -> {
                })));
    }

    /**
     * 注册临时会话的 tick：非银翼玩家不会被 RoleData 系统 tick，
     * 这里只推进「鸟已消失则清引用」，不触发被动金币。
     */
    public static void initGenericBirdTicking() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Map.Entry<UUID, SilverWingRoleData>> iterator = GENERIC_BIRDS.entrySet().iterator();
            while (iterator.hasNext()) {
                SilverWingRoleData data = iterator.next().getValue();
                if (!(data.player instanceof ServerPlayer sp) || sp.hasDisconnected() || !data.hasActiveBird()) {
                    iterator.remove();
                    continue;
                }
                data.tickBirdState();
            }
        });
    }

    private UUID activeBirdId;
    /** 下次发放被动金币的游戏内 tick（会议/时停会暂停）。 */
    private long nextPassiveGoldTick;

    public SilverWingRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean hasActiveBird() {
        if (activeBirdId == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Entity entity = level.getEntity(activeBirdId);
        return entity instanceof MechanicalBirdEntity bird && !bird.isRemoved();
    }

    public void setActiveBird(MechanicalBirdEntity bird) {
        this.activeBirdId = bird.getUUID();
    }

    public void clearActiveBird() {
        this.activeBirdId = null;
    }

    @Override
    public void clear() {
        if (activeBirdId != null && player.level() instanceof ServerLevel level) {
            Entity entity = level.getEntity(activeBirdId);
            if (entity instanceof MechanicalBirdEntity bird) {
                bird.despawnQuietly();
            }
        }
        activeBirdId = null;
    }

    @Override
    public void serverTick() {
        tickPassiveGold();
        tickBirdState();
    }

    /** 鸟已消失 / 被移除时清掉引用（临时会话也走这里，但不会发被动金币）。 */
    private void tickBirdState() {
        if (activeBirdId == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = level.getEntity(activeBirdId);
        if (!(entity instanceof MechanicalBirdEntity bird) || bird.isRemoved()) {
            activeBirdId = null;
        }
    }

    private void tickPassiveGold() {
        if (!(player instanceof ServerPlayer) || !GameUtils.isGameRunning(player)
                || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(player.level());
        if (nextPassiveGoldTick <= 0L) {
            nextPassiveGoldTick = SilverWingRules.nextPassiveGoldTick(now);
            return;
        }
        if (!SilverWingRules.isPassiveGoldDue(now, nextPassiveGoldTick)) {
            return;
        }
        MoneyUtils.addToBalance(player, SilverWingRules.PASSIVE_GOLD_AMOUNT);
        nextPassiveGoldTick = SilverWingRules.nextPassiveGoldTick(now);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
