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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义列车物品的「每物品冷却」组件 —— 挂在<b>使用者</b>身上。
 *
 * <p>
 * 所有自定义列车物品都共用同一个注册物品（{@code starrailexpress:custom_item}），
 * 所以原版 {@code ItemCooldowns}（按 {@link net.minecraft.world.item.Item} 记）会让
 * <b>不同自定义物品互相顶掉冷却</b>：A 枪进入冷却，B 枪也跟着不能开。
 * 这里改成按 <b>玩家 + 物品 id</b> 记「冷却结束的游戏刻 + 总时长」。
 *
 * <p>
 * 冷却是绝对时刻，客户端也同步一份：客户端的世界时间与服务端一致，
 * 所以到期判定两端都准，不需要额外发包来「解除」冷却；总时长用来算进度百分比，
 * 供物品栏冷却条 / 快捷栏与主手冷却显示使用（见
 * {@code mixin.client.ui.ItemCooldownOverlayMixin} 与
 * {@code client.render.hud.stamina.utils.HotbarCooldownRenderer}）。
 */
public class CustomItemCooldownComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<CustomItemCooldownComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("custom_item_cooldown"), CustomItemCooldownComponent.class);

    public final Player player;
    /** 物品 id -> 该物品的冷却记录。 */
    private final Map<String, Cooldown> cooldowns = new HashMap<>();

    public CustomItemCooldownComponent(Player player) {
        this.player = player;
    }

    /** 该物品是否还在冷却中。 */
    public boolean isOnCooldown(String itemId) {
        return remainingTicks(itemId) > 0L;
    }

    /** 该物品剩余冷却 tick（0 = 没在冷却）。 */
    public long remainingTicks(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 0L;
        }
        Cooldown cooldown = cooldowns.get(itemId);
        if (cooldown == null) {
            return 0L;
        }
        long left = cooldown.endTick - now();
        if (left <= 0L) {
            cooldowns.remove(itemId);
            return 0L;
        }
        return left;
    }

    /**
     * 剩余冷却比例：1.0 = 刚开始冷却，0.0 = 已经结束。
     *
     * <p>
     * 与原版 {@code ItemCooldowns#getCooldownPercent} 同义，供 HUD 画条/百分比用。
     */
    public float percent(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 0.0F;
        }
        Cooldown cooldown = cooldowns.get(itemId);
        if (cooldown == null || cooldown.totalTicks <= 0) {
            return 0.0F;
        }
        long left = cooldown.endTick - now();
        if (left <= 0L) {
            cooldowns.remove(itemId);
            return 0.0F;
        }
        return Math.min(1.0F, left / (float) cooldown.totalTicks);
    }

    /** 让某个物品进入冷却（{@code ticks <= 0} 视为不设置）。 */
    public void setCooldown(String itemId, int ticks) {
        if (itemId == null || itemId.isEmpty() || ticks <= 0) {
            return;
        }
        Cooldown cooldown = new Cooldown();
        cooldown.endTick = now() + ticks;
        cooldown.totalTicks = ticks;
        cooldowns.put(itemId, cooldown);
        KEY.sync(this.player);
    }

    /** 立刻清掉某个物品的冷却。 */
    public void clearCooldown(String itemId) {
        if (itemId != null && cooldowns.remove(itemId) != null) {
            KEY.sync(this.player);
        }
    }

    /** 清掉全部冷却（开局 / 重置）。 */
    public void clearAllCooldowns() {
        if (!cooldowns.isEmpty()) {
            cooldowns.clear();
            KEY.sync(this.player);
        }
    }

    /** 过期冷却自动清理（只在服务端跑）。 */
    @Override
    public void serverTick() {
        long current = now();
        if (cooldowns.entrySet().removeIf(entry -> current >= entry.getValue().endTick)) {
            KEY.sync(this.player);
        }
    }

    /** 当前游戏刻（服务端取所在世界，客户端原样用本地世界时间，两端一致）。 */
    private long now() {
        return this.player.level() instanceof ServerLevel serverLevel
                ? serverLevel.getGameTime()
                : this.player.level().getGameTime();
    }

    // ==================== 同步 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        CompoundTag cooldownsTag = new CompoundTag();
        for (Map.Entry<String, Cooldown> entry : cooldowns.entrySet()) {
            CompoundTag cooldownTag = new CompoundTag();
            cooldownTag.putLong("end", entry.getValue().endTick);
            cooldownTag.putInt("total", entry.getValue().totalTicks);
            cooldownsTag.put(entry.getKey(), cooldownTag);
        }
        tag.put("cooldowns", cooldownsTag);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        cooldowns.clear();
        if (!tag.contains("cooldowns", Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag cooldownsTag = tag.getCompound("cooldowns");
        for (String itemId : cooldownsTag.getAllKeys()) {
            CompoundTag cooldownTag = cooldownsTag.getCompound(itemId);
            Cooldown cooldown = new Cooldown();
            cooldown.endTick = cooldownTag.getLong("end");
            cooldown.totalTicks = cooldownTag.getInt("total");
            cooldowns.put(itemId, cooldown);
        }
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public void init() {
        clearAllCooldowns();
    }

    @Override
    public void clear() {
        clearAllCooldowns();
    }

    /** 单件物品的冷却记录。 */
    public static class Cooldown {
        /** 冷却结束的游戏刻。 */
        public long endTick;
        /** 本次冷却的总时长（tick），用于算进度百分比。 */
        public int totalTicks;
    }
}
