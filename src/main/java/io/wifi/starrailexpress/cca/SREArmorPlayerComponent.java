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

import io.wifi.starrailexpress.progression.ProgressionDataManager;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * 护盾组件。
 * <p>
 * 护盾分为「普通护盾」（{@link #armor}）与「限时护盾」（{@link #timedArmor}）。
 * <p>
 * 每一层护盾都带有一个「是否可被酒保透视」的标记（默认 false）：
 * <ul>
 * <li>只有普通护盾与限时护盾拥有该标记；</li>
 * <li>护盾试剂（含深渊护盾试剂）给予的护盾标记为 true，且不区分阵营；</li>
 * <li>职业/指令/其他途径直接给予的护盾一律为 false，酒保无法透视。</li>
 * </ul>
 * 标记为 true 的层数记录在 {@link #bartenderVisibleArmor} 与
 * {@link #bartenderVisibleTimedArmor} 中，同步时只把「可透视」的部分发给酒保，
 * 避免向酒保泄露不可见的护盾层数。
 */
public class SREArmorPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREArmorPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "armor"), SREArmorPlayerComponent.class);
    private final Player player;
    private SREGameWorldComponent gameWorldComponent = null;

    /**
     * 是否允许给指定职业同步。
     * 参数：
     * - 第一个SRERole: 可否同步的玩家
     * - 第二个SRERole: 拥有护盾的玩家
     */
    public static ArrayList<Predicate<Map.Entry<SRERole, SRERole>>> canSynced = new ArrayList<>();
    public int armor = 0;
    /**
     * 普通护盾中「可被酒保透视」的层数（始终 &lt;= {@link #armor}）。
     * 默认 0：不显式标记的护盾酒保看不见。
     */
    public int bartenderVisibleArmor = 0;
    // 仅服务端：避免因为环境伤害死亡。若受到环境伤害将会返回出生点。
    public int environmentDeathProtection = 0;
    public final TreeMap<Long, Integer> timedArmor = new TreeMap<>(); // 到期时间 -> 层数
    /** 限时护盾中「可被酒保透视」的层数：到期时间 -> 层数 */
    public final TreeMap<Long, Integer> bartenderVisibleTimedArmor = new TreeMap<>();

    /**
     * 环境保护，仅服务端记录
     * 
     * @param ticks
     */
    public void setEnvironmentProtection(int ticks) {
        environmentDeathProtection = ticks;
    }

    public int getNormalArmor() {
        return armor;
    }

    public int getArmor() {
        return getAllArmorCount();
    }

    // ===== 可被酒保透视的护盾 =====

    /** 普通护盾中可被酒保透视的层数。 */
    public int getBartenderVisibleNormalArmor() {
        return Math.max(0, Math.min(this.bartenderVisibleArmor, this.armor));
    }

    /** 当前是否存在「可被酒保透视」的护盾层（普通 + 未到期的限时）。 */
    public boolean hasBartenderVisibleArmor() {
        if (getBartenderVisibleNormalArmor() > 0)
            return true;
        long now = getTicksFromGameStart(player.level());
        for (Map.Entry<Long, Integer> entry : bartenderVisibleTimedArmor.entrySet()) {
            if (entry.getKey() > now && entry.getValue() > 0)
                return true;
        }
        return false;
    }

    /** 把可透视的普通护盾层数收敛到 [0, armor]。 */
    private void clampBartenderVisibleNormalArmor() {
        if (this.bartenderVisibleArmor > this.armor)
            this.bartenderVisibleArmor = this.armor;
        if (this.bartenderVisibleArmor < 0)
            this.bartenderVisibleArmor = 0;
    }

    /**
     * 从指定的限时护盾条目中移除层数（优先移除不可透视的层数）。
     */
    private void consumeTimedLayers(long expire, int total, int remove) {
        int visible = Math.min(bartenderVisibleTimedArmor.getOrDefault(expire, 0), total);
        int invisible = Math.max(0, total - visible);
        int takeInvisible = Math.min(invisible, remove);
        int takeVisible = remove - takeInvisible;
        int newTotal = total - remove;
        int newVisible = Math.max(0, visible - takeVisible);
        if (newTotal <= 0) {
            timedArmor.remove(expire);
            bartenderVisibleTimedArmor.remove(expire);
            return;
        }
        timedArmor.put(expire, newTotal);
        if (newVisible > 0)
            bartenderVisibleTimedArmor.put(expire, Math.min(newVisible, newTotal));
        else
            bartenderVisibleTimedArmor.remove(expire);
    }

    // ===== 普通护盾 =====

    public void setArmor(int count) {
        setArmor(count, false, false);
    }

    public void setArmor(int count, boolean clearTimedArmor) {
        setArmor(count, clearTimedArmor, false);
    }

    /**
     * @param count            普通护盾层数
     * @param clearTimedArmor  是否同时清除限时护盾
     * @param bartenderVisible 这些护盾是否可被酒保透视
     */
    public void setArmor(int count, boolean clearTimedArmor, boolean bartenderVisible) {
        if (clearTimedArmor) {
            this.timedArmor.clear();
            this.bartenderVisibleTimedArmor.clear();
        }
        this.armor = count;
        if (this.armor < 0)
            this.armor = 0;
        this.bartenderVisibleArmor = bartenderVisible ? this.armor : 0;
        this.sync();
    }

    public void addArmor(int count) {
        addArmor(count, false);
    }

    /**
     * @param count            叠加的普通护盾层数
     * @param bartenderVisible 这些层是否可被酒保透视
     */
    public void addArmor(int count, boolean bartenderVisible) {
        this.armor += count;
        if (this.armor < 0)
            this.armor = 0;
        if (bartenderVisible && count > 0)
            this.bartenderVisibleArmor += count;
        clampBartenderVisibleNormalArmor();
        if (count > 0 && this.player instanceof ServerPlayer sp)
            ProgressionDataManager.onShieldGained(sp, "normal", count);
        this.sync();
    }

    public void addArmor() {
        addArmor(1, false);
    }

    /** 添加 1 层可被酒保透视的普通护盾（护盾试剂等）。 */
    public void addBartenderVisibleArmor() {
        addArmor(1, true);
    }

    private long getTicksFromGameStart(Level world) {
        return GameUtils.getTicksFromGameStart(world);
    }

    // ===== 限时护盾 =====

    public void addTimedArmor(int layers, int ticks) {
        addTimedArmor(layers, ticks, true, false);
    }

    public void addTimedArmor(int layers, int ticks, boolean stackArmor) {
        addTimedArmor(layers, ticks, stackArmor, false);
    }

    /**
     * 限时护盾：给玩家添加限时护盾，持续指定 tick 数，时间到后自动移除。
     * 
     * @param layers           叠加的护盾层数
     * @param ticks            护盾持续 tick 数
     * @param stackArmor       true=重置计时器并叠加护盾层数；false=仅重置计时器，不叠加护盾层数（但保证至少有 1 层）
     * @param bartenderVisible 这些层是否可被酒保透视
     */
    public void addTimedArmor(int layers, int ticks, boolean stackArmor, boolean bartenderVisible) {
        long now = getTicksFromGameStart(player.level());
        long expireTime = now + ticks;

        if (!stackArmor) {
            timedArmor.clear();
            bartenderVisibleTimedArmor.clear();
        }
        timedArmor.merge(expireTime, layers, Integer::sum);
        if (bartenderVisible && layers > 0)
            bartenderVisibleTimedArmor.merge(expireTime, layers, Integer::sum);
        // 防御：可透视层数不超过该到期时间的总层数
        int total = timedArmor.getOrDefault(expireTime, 0);
        int visible = bartenderVisibleTimedArmor.getOrDefault(expireTime, 0);
        if (visible > 0 && total > 0)
            bartenderVisibleTimedArmor.put(expireTime, Math.min(visible, total));
        else
            bartenderVisibleTimedArmor.remove(expireTime);
        if (layers > 0 && this.player instanceof ServerPlayer sp)
            ProgressionDataManager.onShieldGained(sp, "timed", layers);
        this.sync();
    }

    /**
     * 限时护盾：直接设置护盾层数与持续时间（非叠加）。
     * 
     * @param layers 护盾层数（0 表示清除限时护盾）
     * @param ticks  护盾持续 tick 数
     */
    public void setTimedArmor(int layers, int ticks) {
        setTimedArmor(layers, ticks, false);
    }

    /**
     * 限时护盾：直接设置护盾层数与持续时间（非叠加）。
     * 
     * @param layers           护盾层数（0 表示清除限时护盾）
     * @param ticks            护盾持续 tick 数
     * @param bartenderVisible 这些层是否可被酒保透视
     */
    public void setTimedArmor(int layers, int ticks, boolean bartenderVisible) {
        this.addTimedArmor(layers, ticks, false, bartenderVisible);
    }

    public void removeArmor() {
        removeArmor(1);
    }

    public void removeArmor(int amount) {
        this.armor -= amount;
        clampBartenderVisibleNormalArmor();
        this.sync();
    }

    public boolean hasArmor() {
        return hasArmor(1);
    }

    public boolean hasArmor(int count) {
        return getAllArmorCount() >= count;
    }

    public int getAllArmorCount() {
        return this.armor + timedArmor.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean triggerAndRemoveArmor(int amount) {
        // 先移除限时护盾
        while (amount > 0 && !timedArmor.isEmpty()) {
            Map.Entry<Long, Integer> first = timedArmor.firstEntry();
            int remove = Math.min(amount, first.getValue());
            amount -= remove;
            consumeTimedLayers(first.getKey(), first.getValue(), remove);
        }
        if (amount <= 0) {
            sync();
            return true;
        }

        // 移除常驻护盾
        if (amount <= this.armor) {
            this.armor -= amount;
            // 优先消耗不可透视的层数，保证可透视护盾不会被优先打掉
            int invisible = Math.max(0, this.armor + amount - this.bartenderVisibleArmor);
            int takeInvisible = Math.min(invisible, amount);
            this.bartenderVisibleArmor -= (amount - takeInvisible);
            clampBartenderVisibleNormalArmor();
            sync();
            return true;
        }
        this.armor = 0;
        this.bartenderVisibleArmor = 0;
        return false;
    }

    @Override
    public void init() {
        this.armor = 0;
        this.bartenderVisibleArmor = 0;
        this.timedArmor.clear();
        this.bartenderVisibleTimedArmor.clear();
        this.sync();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        if (target == this.player)
            return true;
        // 酒保透视：只有存在可透视护盾时才同步，避免泄露不可见的护盾
        if (!hasBartenderVisibleArmor())
            return false;
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        }
        if (gameWorldComponent != null) {
            var selfRole = gameWorldComponent.getRole(player);
            var targetRole = gameWorldComponent.getRole(target);
            if (targetRole != null && selfRole != null) {
                for (var t : canSynced) {
                    if (t.test(Map.entry(targetRole, selfRole))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public SREArmorPlayerComponent(Player player) {
        this.player = player;
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        }
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public boolean checkIsGameRunning() {
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        }
        return gameWorldComponent.gameStatus.equals(SREGameWorldComponent.GameStatus.ACTIVE);
    }

    @Override
    public void clientTick() {
        if (!checkIsGameRunning()) {
            this.armor = 0;
            this.bartenderVisibleArmor = 0;
            this.timedArmor.clear();
            this.bartenderVisibleTimedArmor.clear();
            return;
        }
        long now = getTicksFromGameStart(player.level());
        timedArmor.headMap(now, true).clear();
        bartenderVisibleTimedArmor.headMap(now, true).clear();
    }

    public static int tick_ = 0;

    public void serverTick() {
        if (!checkIsGameRunning()) {
            if (!this.timedArmor.isEmpty()) {
                this.timedArmor.clear();
            }
            if (!this.bartenderVisibleTimedArmor.isEmpty()) {
                this.bartenderVisibleTimedArmor.clear();
            }
            if (this.armor > 0) {
                this.armor = 0;
            }
            if (this.bartenderVisibleArmor > 0) {
                this.bartenderVisibleArmor = 0;
            }
            if (this.environmentDeathProtection > 0) {
                this.environmentDeathProtection = 0;
            }
            return;
        }
        if (environmentDeathProtection > 0)
            environmentDeathProtection--;
        // CCA冷冻：仅禁止CCA/职业执行tick，因此冻结限时护盾的倒计时（不再减少）
        // 不需要，上游已冻结
        long now = getTicksFromGameStart(player.level());
        // 移除所有已到期的护盾层
        timedArmor.headMap(now, true).clear();
        bartenderVisibleTimedArmor.headMap(now, true).clear();
    }

    public boolean giveArmor() {
        return giveArmor(false);
    }

    /**
     * 保证至少有 1 层普通护盾（不清空已有的多层护盾）。
     *
     * @param bartenderVisible 这一层是否可被酒保透视
     */
    public boolean giveArmor(boolean bartenderVisible) {
        // 防止清空大于1的护盾
        if (this.armor < 1) {
            armor = 1;
            if (bartenderVisible)
                this.bartenderVisibleArmor = 1;
        } else if (bartenderVisible) {
            this.bartenderVisibleArmor = Math.max(this.bartenderVisibleArmor, 1);
        }
        clampBartenderVisibleNormalArmor();
        this.sync();
        return true;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeArmorNbt(tag, false);
    }

    @Override
    public void writeToSyncNbtWithPlayer(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup,
            ServerPlayer recipient) {
        // 非本人（酒保）只能收到「可被酒保透视」的那部分护盾
        writeArmorNbt(tag, recipient != null && recipient != this.player);
    }

    /**
     * @param onlyBartenderVisible true=只写可被酒保透视的部分（发给酒保）
     */
    private void writeArmorNbt(@NotNull CompoundTag tag, boolean onlyBartenderVisible) {
        int normalArmor = onlyBartenderVisible ? getBartenderVisibleNormalArmor() : this.armor;
        if (normalArmor > 0)
            tag.putInt("armor", normalArmor);
        int visibleNormal = Math.min(getBartenderVisibleNormalArmor(), normalArmor);
        if (visibleNormal > 0)
            tag.putInt("bartenderVisibleArmor", visibleNormal);

        if (!timedArmor.isEmpty() || !bartenderVisibleTimedArmor.isEmpty()) {
            long now = getTicksFromGameStart(player.level());
            ListTag list = new ListTag();
            for (Map.Entry<Long, Integer> entry : timedArmor.entrySet()) {
                int remaining = (int) (entry.getKey() - now);
                if (remaining <= 0)
                    continue; // 跳过已到期（理论不会发生，防御性）
                int visible = Math.min(bartenderVisibleTimedArmor.getOrDefault(entry.getKey(), 0), entry.getValue());
                int layers = onlyBartenderVisible ? visible : entry.getValue();
                if (layers <= 0)
                    continue;

                CompoundTag entryTag = new CompoundTag();
                entryTag.putInt("r", remaining);
                entryTag.putInt("c", layers);
                if (visible > 0)
                    entryTag.putInt("v", Math.min(visible, layers));
                list.add(entryTag);
            }
            if (!list.isEmpty()) {
                tag.put("timedArmor", list);
            }
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.armor = tag.contains("armor") ? tag.getInt("armor") : 0;
        this.bartenderVisibleArmor = tag.contains("bartenderVisibleArmor") ? tag.getInt("bartenderVisibleArmor") : 0;
        this.timedArmor.clear();
        this.bartenderVisibleTimedArmor.clear();
        {
            if (tag.contains("timedArmor", Tag.TAG_LIST)) {
                ListTag list = tag.getList("timedArmor", Tag.TAG_COMPOUND);
                long now = getTicksFromGameStart(player.level());
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag entryTag = list.getCompound(i);
                    int remaining = entryTag.getInt("r");
                    int count = entryTag.getInt("c");
                    long expire = now + remaining;
                    this.timedArmor.merge(expire, count, Integer::sum);
                    int visible = entryTag.contains("v") ? Math.min(entryTag.getInt("v"), count) : 0;
                    if (visible > 0)
                        this.bartenderVisibleTimedArmor.merge(expire, visible, Integer::sum);
                }
            }
        }
        clampBartenderVisibleNormalArmor();
    }

    @Override
    public void clear() {
        this.armor = 0;
        this.bartenderVisibleArmor = 0;
        this.timedArmor.clear();
        this.bartenderVisibleTimedArmor.clear();
        this.sync();
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    public void consumeArmor() {
        this.consumeArmor(1);
    }

    public void consumeArmor(int count) {
        this.triggerAndRemoveArmor(count);
    }

}
