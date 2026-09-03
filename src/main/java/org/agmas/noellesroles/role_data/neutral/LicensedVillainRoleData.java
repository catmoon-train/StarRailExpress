/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
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

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import org.agmas.noellesroles.packet.BroadcastMessageS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalInt;

/**
 * 黑警（Licensed villain）职业数据。
 * <p>
 * 核心机制：当场上存活玩家数量低于开局人数的 25% 时触发「黑警时刻」。
 * 黑警时刻触发后，以触发时在场玩家为基准比较三大阵营（杀手阵营含杀手方中立 /
 * 平民阵营含警长阵营 / 中立阵营不含杀手方中立）的存活数量，取最多者作为黑警的击杀目标阵营。
 * 黑警只需击杀完该阵营的所有玩家即可独立获胜；若误杀其它阵营玩家，则黑警因「悔恨自尽」而死。
 */
public class LicensedVillainRoleData extends SimpleRoleData {

    /** 黑警职业颜色（浅黑色）。 */
    public static final int LICENSED_VILLAIN_COLOR = new java.awt.Color(0x2B, 0x2B, 0x2B).getRGB();

    /** 阶段：未触发。 */
    public static final int PHASE_NONE = 0;
    /** 阶段：击杀所有杀手阵营（含杀手方中立）玩家。 */
    public static final int PHASE_KILLER = 1;
    /** 阶段：击杀所有平民阵营（含警长阵营）玩家。 */
    public static final int PHASE_CIVILIAN = 2;
    /** 阶段：击杀所有中立阵营（不含杀手方中立）玩家。 */
    public static final int PHASE_NEUTRAL = 3;

    /** 全局触发守卫，确保黑警时刻只触发一次。 */
    private static boolean momentTriggeredGuard = false;

    /** 本实例（黑警）已知黑警时刻已触发。会同步给客户端用于透视。 */
    public boolean momentTriggered = false;
    /** 当前目标阵营阶段。会同步给客户端用于透视。 */
    public int targetPhase = PHASE_NONE;

    public LicensedVillainRoleData(RoleDataContext context) {
        super(context);
    }

    // ---- 服务端的黑警时刻触发与状态 ----

    @Override
    public void serverTick() {
        super.serverTick();
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, BounsRoles.LICENSED_VILLAIN)) {
            return;
        }
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        // 黑警时刻已触发：检查独立胜利（目标阵营是否已被清空）
        if (momentTriggered) {
            if (areAllTargetsCleared(player.level(), gameWorld, targetPhase)) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    RoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.CUSTOM,
                            BounsRoles.LICENSED_VILLAIN.identifier().getPath(),
                            OptionalInt.of(LICENSED_VILLAIN_COLOR));
                }
            }
            return;
        }

        int playerCount = gameWorld.getPlayerCount();
        if (playerCount <= 0)
            return;

        int alive = countAlivePlayers(gameWorld);
        // 存活人数不足开局人数的 25% 时触发
        if (alive > 0 && alive < Math.ceil(playerCount * 0.25)) {
            if (momentTriggeredGuard)
                return;
            momentTriggeredGuard = true;
            triggerLicensedVillainMoment(gameWorld);
        }
    }

    /** 黑警时刻触发：广播、发放德林加、播放音效、决定目标阵营并同步给所有黑警。 */
    private void triggerLicensedVillainMoment(SREGameWorldComponent gameWorld) {
        ServerLevel level = (ServerLevel) player.level();

        // 以触发时在场（存活）玩家为基准统计三大阵营数量
        int killerCount = 0;
        int civilianCount = 0;
        int neutralCount = 0;
        for (Player p : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p))
                continue;
            SRERole role = gameWorld.getRole(p);
            if (role == null)
                continue;
            if (role.isKillerTeam()) {
                killerCount++;
            } else if (role.isInnocent()) {
                civilianCount++;
            } else if (role.isNeutrals() && !role.isNeutralForKiller()) {
                neutralCount++;
            }
        }

        int phase = PHASE_KILLER;
        int max = killerCount;
        if (civilianCount > max) {
            max = civilianCount;
            phase = PHASE_CIVILIAN;
        }
        if (neutralCount > max) {
            max = neutralCount;
            phase = PHASE_NEUTRAL;
        }

        // 向所有玩家广播「有人会为你们带来迟到的正义」
        Component momentMessage = Component.translatable("message.noellesroles.licensed_villain.moment");
        for (ServerPlayer sp : level.players()) {
            ServerPlayNetworking.send(sp, new BroadcastMessageS2CPacket(momentMessage));
        }

        // 给所有黑警发放德林加手枪，并同步状态
        for (ServerPlayer sp : level.players()) {
            if (gameWorld.isRole(sp, BounsRoles.LICENSED_VILLAIN)) {
                sp.getInventory().add(new net.minecraft.world.item.ItemStack(
                        io.wifi.starrailexpress.index.TMMItems.DERRINGER));
                RoleData data = RoleData.getNullable(sp);
                if (data instanceof LicensedVillainRoleData lv) {
                    lv.momentTriggered = true;
                    lv.targetPhase = phase;
                    lv.sync();
                }
            }
        }

        // 全场播放与远征队修饰符触发时一致的音效（master 类型）
        for (Player p : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p))
                continue;
            p.level().playSound(null, p.blockPosition(), TMMSounds.ITEM_PSYCHO_ARMOUR,
                    SoundSource.MASTER, 2.0F, 0.8F);
        }
    }

    /** 统计当前存活玩家数。 */
    private int countAlivePlayers(SREGameWorldComponent gameWorld) {
        int count = 0;
        for (Player p : ((ServerLevel) player.level()).players()) {
            if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p))
                count++;
        }
        return count;
    }

    // ---- 客户端读取（透视用） ----

    /** 黑警时刻是否已触发（客户端同步）。 */
    public boolean isMomentTriggered() {
        return momentTriggered;
    }

    /** 当前目标阵营阶段（客户端同步）。 */
    public int getTargetPhase() {
        return targetPhase;
    }

    /**
     * 判断某角色在当前阶段是否属于黑警的击杀目标阵营（不含黑警自身）。
     * 中立阶段以下，目标以玩家当前阵营挂钩：若玩家在此期间从目标阵营变为其它阵营，
     * 则该方法会自动返回 false（自动不再是目标）。
     */
    public boolean isTargetRole(@NotNull SRERole role) {
        if (RoleUtils.compareRole(role, BounsRoles.LICENSED_VILLAIN))
            return false; // 黑警自己永远不是目标
        return switch (targetPhase) {
            case PHASE_KILLER -> role.isKillerTeam();
            case PHASE_CIVILIAN -> role.isInnocent();
            case PHASE_NEUTRAL -> role.isNeutrals() && !role.isNeutralForKiller();
            default -> false;
        };
    }

    // ---- NBT 同步（供客户端透视） ----

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("lvMoment", momentTriggered);
        tag.putInt("lvPhase", targetPhase);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        momentTriggered = tag.getBoolean("lvMoment");
        targetPhase = tag.getInt("lvPhase");
    }

    // ---- 静态工具：供击杀校验事件使用 ----

    /** 当前目标阵营是否仍有存活的非黑警玩家（用于判独立胜利）。 */
    public static boolean areAllTargetsCleared(@NotNull Level level, SREGameWorldComponent gameWorld, int phase) {
        for (Player p : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p))
                continue;
            SRERole role = gameWorld.getRole(p);
            if (role == null)
                continue;
            if (role.equals(BounsRoles.LICENSED_VILLAIN))
                continue;
            boolean target = switch (phase) {
                case PHASE_KILLER -> role.isKillerTeam();
                case PHASE_CIVILIAN -> role.isInnocent();
                case PHASE_NEUTRAL -> role.isNeutrals() && !role.isNeutralForKiller();
                default -> false;
            };
            if (target)
                return false;
        }
        return true;
    }
}
