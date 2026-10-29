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

package io.wifi.starrailexpress.client.pet;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.network.PetSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端随从装备缓存（uuid → 随从皮肤 ID + 是否公开）。
 * <p>
 * 这里同时是<b>可见性闸门</b>的唯一出口：渲染层只问
 * {@link #getVisibleSkinId(AbstractClientPlayer)}，不自己判断状态，避免各处规则漂移。
 * <p>
 * 可见性规则：
 * <ul>
 * <li><b>自己的随从</b>：始终可见（本地配置 {@code hideAllPets} 可整体关掉）；</li>
 * <li><b>别人的随从</b>：需要主人勾选「展示给其他玩家」，且游戏尚未开始
 * （{@code GameStatus == INACTIVE}）。用 {@code INACTIVE} 而不是 {@code !isRunning()}，
 * 因为 {@code isRunning()} 不包含 {@code STARTING}——开局淡入期间必须同样隐藏，
 * 否则会泄漏「谁有随从」这条信息。</li>
 * </ul>
 * 位置与朝向<b>不在本包内</b>：随从逐帧从客户端自己的玩家实体上取坐标
 * （原版实体追踪包已经同步过了），因此移动不产生任何额外流量。
 */
@Environment(EnvType.CLIENT)
public final class ClientPetCache {
    private static final Map<UUID, PetSyncPayload.Entry> ENTRIES = new ConcurrentHashMap<>();

    private ClientPetCache() {
    }

    /** 应用一次服务端同步。全量包先清空，增量包只覆盖出现的玩家。 */
    public static void applySync(PetSyncPayload payload) {
        if (payload == null) {
            return;
        }
        if (payload.fullSync()) {
            ENTRIES.clear();
        }
        for (Map.Entry<UUID, PetSyncPayload.Entry> entry : payload.entries().entrySet()) {
            apply(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 取某玩家装备的随从皮肤 ID（不含可见性判断）。
     *
     * @return 未装备时返回空串
     */
    public static String getSkinId(UUID uuid) {
        if (uuid == null) {
            return "";
        }
        PetSyncPayload.Entry entry = ENTRIES.get(uuid);
        if (entry == null || !entry.hasPet()) {
            return "";
        }
        return entry.skinId();
    }

    /** 某玩家是否勾选了「展示给其他玩家」。 */
    public static boolean isShared(UUID uuid) {
        PetSyncPayload.Entry entry = uuid == null ? null : ENTRIES.get(uuid);
        return entry != null && entry.shared();
    }

    /**
     * 渲染层唯一入口：返回「此刻应当渲染的随从皮肤 ID」，空串表示不渲染。
     * <p>
     * 已经合入本地配置与游戏状态，调用方无需再判断任何可见性条件。
     * <p>
     * <b>这是每帧热路径</b>（每个可见玩家每帧一次），因此：
     * <ul>
     * <li>先用最便宜的「未装备」判断短路——绝大多数玩家没装随从；</li>
     * <li>游戏阶段只在「他人随从」分支才查，自己/未装备的路径完全不碰组件；</li>
     * <li>本地配置只读一次到局部变量，避免重复 {@code instance()} 调用。</li>
     * </ul>
     */
    public static String getVisibleSkinId(AbstractClientPlayer player) {
        if (player == null) {
            return "";
        }
        UUID uuid = player.getUUID();
        PetSyncPayload.Entry entry = ENTRIES.get(uuid);
        // 最便宜的一步：没有条目（或没装随从）直接返回，不碰任何配置/组件
        if (entry == null || !entry.hasPet()) {
            return "";
        }
        SREClientConfig config = SREClientConfig.instance();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getUUID().equals(uuid)) {
            // 自己的随从：除非本地配置要求全隐藏，否则一直可见
            return config.hideAllPets ? "" : entry.skinId();
        }
        // 他人勾选公开也不一定看：本地可以只留自己的
        if (config.showOwnPetOnly) {
            return "";
        }
        // 他人的随从：必须公开 + 游戏未开始（含开局淡入的 STARTING 阶段）
        if (!entry.shared()) {
            return "";
        }
        return isLobbyPhase(minecraft) ? entry.skinId() : "";
    }

    /** 游戏是否处于「大厅」阶段（只有此阶段他人随从才可见）。 */
    private static boolean isLobbyPhase(Minecraft minecraft) {
        if (minecraft.level == null) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.getInstance(minecraft.level);
        if (game == null) {
            return false;
        }
        return game.getGameStatus() == SREGameWorldComponent.GameStatus.INACTIVE;
    }

    private static boolean isLocalPlayer(AbstractClientPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID());
    }

    /** 断开连接 / 切换维度时清空。 */
    public static void clear() {
        ENTRIES.clear();
    }


    /**
     * 本地乐观更新：玩家在皮肤页面点选随从后立刻生效，不必等 5 秒一轮的服务端扫描。
     * <p>
     * 只改本地缓存，权威状态仍由服务端下一次 {@link PetSyncPayload} 覆盖；
     * 服务端拒绝（例如皮肤未解锁）时会在下一轮扫描把缓存改回去。
     */
    public static void applyLocalOptimistic(UUID uuid, String skinId) {
        if (uuid == null) {
            return;
        }
        PetSyncPayload.Entry existing = ENTRIES.get(uuid);
        boolean shared = existing != null && existing.shared();
        if (skinId == null || skinId.isBlank()) {
            ENTRIES.remove(uuid);
        } else {
            ENTRIES.put(uuid, new PetSyncPayload.Entry(skinId, shared));
        }
    }
    private static void apply(UUID uuid, PetSyncPayload.Entry entry) {
        if (uuid == null) {
            return;
        }
        if (entry == null || !entry.hasPet()) {
            ENTRIES.remove(uuid);
        } else {
            ENTRIES.put(uuid, entry);
        }
    }
}
