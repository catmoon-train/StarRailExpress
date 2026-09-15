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

package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义列车物品的「命中标记」组件 —— 挂在<b>被击中的玩家</b>身上。
 *
 * <p>
 * 每被某个自定义物品命中一次，就在被击中者身上记一笔：{@code 物品 id -> 命中次数 + 过期时刻}。
 * 次数达到物品配置的 {@code hitsToFinal} 时，由
 * {@link io.wifi.starrailexpress.customitem.CustomItemRuntime} 清掉标记并结算最终效果；
 * 如果迟迟没凑够次数，超过物品配置的 {@code hitMarkerTicks}（「命中标记持续」）后标记自动消失、
 * 计数从 0 重新开始 —— 与警棍的命中记录（{@code BatonHandler} 的 4 秒窗口）同一套思路，
 * 只是这里把窗口做成了物品自己的配置项，并且标记真正记在玩家身上（而不是全局静态表）。
 *
 * <p>
 * 标记<b>按物品 id 分开存</b>：不同物品各记一份、互不影响，不会出现「A 枪打一下 + B 枪打一下」
 * 却凑够次数的错误叠加；同一物品的连续命中会不断刷新过期时间。
 */
public class CustomItemHitMarkerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<CustomItemHitMarkerComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("custom_item_hit_marker"), CustomItemHitMarkerComponent.class);

    public final Player player;
    /** 物品 id -> 该物品在本玩家身上的命中标记。 */
    private final Map<String, Marker> markers = new HashMap<>();

    public CustomItemHitMarkerComponent(Player player) {
        this.player = player;
    }

    /**
     * 记录一次命中并返回该物品当前（未过期的）命中次数。
     *
     * @param itemId      施加标记的物品 id（不同物品各记一份）
     * @param windowTicks 标记持续时间（tick），从本次命中重新计时；到点没触发最终效果就自动消失
     * @param now         当前游戏刻
     * @return 该物品累计命中次数；标记不存在或刚过期时返回 1（本次算第一次）
     */
    public int addHit(String itemId, int windowTicks, long now) {
        if (itemId == null || itemId.isEmpty()) {
            return 1;
        }
        Marker marker = markers.get(itemId);
        if (marker == null || now >= marker.expireGameTime) {
            // 没有标记 / 标记已过期：从这一次重新开始计数
            marker = new Marker();
            markers.put(itemId, marker);
        }
        marker.count++;
        marker.expireGameTime = now + Math.max(1, windowTicks);
        KEY.sync(this.player);
        return marker.count;
    }

    /** 该物品当前未过期的命中次数（0 = 没有标记）。 */
    public int getHitCount(String itemId, long now) {
        Marker marker = itemId == null ? null : markers.get(itemId);
        if (marker == null || now >= marker.expireGameTime) {
            return 0;
        }
        return marker.count;
    }

    /** 清掉某个物品的标记（触发最终效果后调用，避免下一轮命中接在前一次计数上）。 */
    public void clearMarker(String itemId) {
        if (itemId != null && markers.remove(itemId) != null) {
            KEY.sync(this.player);
        }
    }

    /** 清掉本玩家身上全部命中标记（开局重置 / 组件初始化）。 */
    public void clearAllMarkers() {
        if (!markers.isEmpty()) {
            markers.clear();
            KEY.sync(this.player);
        }
    }

    /** 过期标记自动清理（只在服务端跑）。 */
    @Override
    public void serverTick() {
        long now = gameTime();
        if (markers.entrySet().removeIf(entry -> now >= entry.getValue().expireGameTime)) {
            KEY.sync(this.player);
        }
    }

    private long gameTime() {
        return this.player instanceof ServerPlayer serverPlayer
                ? serverPlayer.serverLevel().getGameTime()
                : this.player.level().getGameTime();
    }

    // ==================== 同步 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        CompoundTag markersTag = new CompoundTag();
        for (Map.Entry<String, Marker> entry : markers.entrySet()) {
            CompoundTag markerTag = new CompoundTag();
            markerTag.putInt("count", entry.getValue().count);
            markerTag.putLong("expire", entry.getValue().expireGameTime);
            markersTag.put(entry.getKey(), markerTag);
        }
        tag.put("hit_markers", markersTag);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        markers.clear();
        if (!tag.contains("hit_markers", Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag markersTag = tag.getCompound("hit_markers");
        for (String itemId : markersTag.getAllKeys()) {
            CompoundTag markerTag = markersTag.getCompound(itemId);
            Marker marker = new Marker();
            marker.count = markerTag.getInt("count");
            marker.expireGameTime = markerTag.getLong("expire");
            markers.put(itemId, marker);
        }
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public void init() {
        clearAllMarkers();
    }

    @Override
    public void clear() {
        clearAllMarkers();
    }

    /** 单个物品的命中标记。 */
    public static class Marker {
        /** 已累计的命中次数。 */
        public int count;
        /** 过期时刻（游戏刻）：到点仍未触发最终效果就整条消失。 */
        public long expireGameTime;
    }
}
