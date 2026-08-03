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
import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Predicate;

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
    public int timedArmorTicks = 0;

    public int getArmor() {
        return armor;
    }

    public void setArmor(int count) {
        this.armor = count;
        if (this.armor < 0)
            this.armor = 0;
        this.sync();
    }

    public void addArmor(int count) {
        this.armor += count;
        if (this.armor < 0)
            this.armor = 0;
        this.sync();
    }

    public void addArmor() {
        ++this.armor;
        this.sync();
    }

    /**
     * 限时护盾：给玩家添加限时护盾，持续指定 tick 数，时间到后自动移除。
     * 
     * @param layers     叠加的护盾层数
     * @param ticks      护盾持续 tick 数
     * @param stackArmor true=重置计时器并叠加护盾层数；false=仅重置计时器，不叠加护盾层数（但保证至少有 1 层）
     */
    public void addTimedArmor(int layers, int ticks, boolean stackArmor) {
        if (stackArmor) {
            this.armor += Math.max(0, layers);
        } else if (this.armor < 1) {
            this.armor = 1;
        }
        this.timedArmorTicks = ticks;
        this.sync();
    }

    /**
     * 限时护盾：直接设置护盾层数与持续时间（非叠加）。
     * 
     * @param layers 护盾层数（0 表示清除限时护盾）
     * @param ticks  护盾持续 tick 数
     */
    public void setTimedArmor(int layers, int ticks) {
        this.armor = Math.max(0, layers);
        this.timedArmorTicks = this.armor > 0 ? ticks : 0;
        this.sync();
    }

    public void removeArmor() {
        --this.armor;
        this.sync();
    }

    public void removeArmor(int amount) {
        this.armor -= amount;
        this.sync();
    }

    public void init() {
        this.armor = 0;
        this.timedArmorTicks = 0;
        this.sync();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        if (target == this.player)
            return true;
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

    public void clientTick() {
        if (!checkIsGameRunning()) {
            this.armor = 0;
            this.timedArmorTicks = 0;
            return;
        }
    }

    public static int tick_ = 0;

    public void serverTick() {
        if (!checkIsGameRunning()) {
            if (this.timedArmorTicks > 0) {
                this.timedArmorTicks = 0;
                this.armor = 0;
            }
            return;
        }
        // CCA冷冻：仅禁止CCA/职业执行tick，因此冻结限时护盾的倒计时（不再减少）
        // 不需要，上游已冻结
        if (this.timedArmorTicks > 0) {
            this.timedArmorTicks--;
            if (this.timedArmorTicks == 0 && this.armor > 0) {
                this.armor = 0;
                this.sync();
            }
        }
    }

    public boolean giveArmor() {
        // 防止清空大于1的护盾
        if (this.armor < 1)
            armor = 1;
        this.sync();
        return true;
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.armor > 0)
            tag.putInt("armor", this.armor);
        if (this.timedArmorTicks > 0)
            tag.putInt("timedArmorTicks", this.timedArmorTicks);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.armor = tag.contains("armor") ? tag.getInt("armor") : 0;
        this.timedArmorTicks = tag.contains("timedArmorTicks") ? tag.getInt("timedArmorTicks") : 0;
    }

    @Override
    public void clear() {
        this.armor = 0;
        this.timedArmorTicks = 0;
        this.sync();
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
