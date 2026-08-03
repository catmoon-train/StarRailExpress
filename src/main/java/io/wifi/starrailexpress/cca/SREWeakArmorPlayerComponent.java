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
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 弱效护盾组件
 * - 弱效护盾是限时的
 * - 只能抵挡一次来自特定死亡类型的伤害（默认刀）
 * - 抵挡完一次伤害后弱效护盾层数减 1（支持叠加层数）
 * - blockAllDeathReasons 为 true 时，可抵挡任意死亡原因
 */
public class SREWeakArmorPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREWeakArmorPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("weak_armor"), SREWeakArmorPlayerComponent.class);

    private final Player player;
    private SREGameWorldComponent gameWorldComponent = null;

    /** 弱效护盾层数（0 表示没有） */
    public int weakArmor = 0;
    /** 弱效护盾剩余时间（ticks），-1 表示没有激活 */
    public int weakArmorTicks = -1;
    /** 弱效护盾可以抵挡的死亡原因列表 */
    public Set<ResourceLocation> blockedDeathReasons = new HashSet<>();
    /** 是否抵挡任意死亡原因（true 时忽略 blockedDeathReasons） */
    public boolean blockAllDeathReasons = false;

    /** 弱效护盾默认持续时间（ticks），默认 1 分钟 */
    public static final int DEFAULT_WEAK_ARMOR_DURATION = GameConstants.getInTicks(1, 0);
    /** 弱效护盾试剂提供的持续时间（ticks），20 秒 */
    public static final int VIAL_WEAK_ARMOR_DURATION = GameConstants.getInTicks(0, 20);

    public SREWeakArmorPlayerComponent(Player player) {
        this.player = player;
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        }
    }

    public int getWeakArmor() {
        return weakArmor;
    }

    /**
     * 给予一层弱效护盾（叠加），使用指定的持续时间与可抵挡的死亡原因。
     * 
     * @param durationTicks  持续时间（ticks）
     * @param blockedReasons 可以抵挡的死亡原因列表
     */
    public void giveWeakArmor(int durationTicks, Set<ResourceLocation> blockedReasons) {
        giveWeakArmor(durationTicks, blockedReasons, false);
    }

    /**
     * 给予一层弱效护盾（叠加）。
     * 
     * @param durationTicks  持续时间（ticks）
     * @param blockedReasons 可以抵挡的死亡原因列表
     * @param blockAll       是否抵挡任意死亡原因（true 时忽略 blockedReasons）
     */
    public void giveWeakArmor(int durationTicks, Set<ResourceLocation> blockedReasons, boolean blockAll) {
        this.weakArmor += 1;
        if (this.weakArmor == 1) {
            // 第一层：设置持续时间、死亡原因与 blockAll 标记
            this.weakArmorTicks = durationTicks;
            this.blockAllDeathReasons = blockAll;
            this.blockedDeathReasons = new HashSet<>(blockedReasons);
        } else {
            // 叠加层：合并死亡原因，保留已有的 blockAll 标记
            if (!blockAll) {
                this.blockedDeathReasons.addAll(blockedReasons);
            }
        }
        this.sync();
    }

    /**
     * 直接设置弱效护盾层数（非叠加）。
     * 
     * @param layers         层数（0 表示清除）
     * @param durationTicks  持续时间（ticks）
     * @param blockedReasons 可以抵挡的死亡原因列表
     * @param blockAll       是否抵挡任意死亡原因
     */
    public void setWeakArmor(int layers, int durationTicks, Set<ResourceLocation> blockedReasons, boolean blockAll) {
        this.weakArmor = Math.max(0, layers);
        this.blockAllDeathReasons = blockAll;
        if (this.weakArmor > 0) {
            this.weakArmorTicks = durationTicks;
            this.blockedDeathReasons = new HashSet<>(blockedReasons);
        } else {
            this.weakArmorTicks = -1;
            this.blockedDeathReasons.clear();
        }
        this.sync();
    }

    /**
     * 给予弱效护盾，使用默认持续时间与默认死亡原因（刀）
     */
    public void giveWeakArmor() {
        Set<ResourceLocation> defaultBlocked = new HashSet<>();
        defaultBlocked.add(GameConstants.DeathReasons.KNIFE);
        giveWeakArmor(DEFAULT_WEAK_ARMOR_DURATION, defaultBlocked);
    }

    /**
     * 检查给定的死亡原因是否可以被弱效护盾抵挡
     */
    public boolean canBlockDeathReason(ResourceLocation deathReason) {
        if (this.weakArmor <= 0)
            return false;
        if (this.blockAllDeathReasons)
            return true;
        return blockedDeathReasons.contains(deathReason);
    }

    /**
     * 消耗一层弱效护盾（抵挡一次伤害后调用）
     */
    public void consumeWeakArmor() {
        this.weakArmor = Math.max(0, this.weakArmor - 1);
        if (this.weakArmor == 0) {
            this.weakArmorTicks = -1;
            this.blockedDeathReasons.clear();
            this.blockAllDeathReasons = false;
        }
        this.sync();
    }

    public void removeWeakArmor() {
        consumeWeakArmor();
    }

    /**
     * 增加弱效护盾层数（仅在已存在弱效护盾时生效，保留原有的持续时间与抵挡规则）。
     * 
     * @param layers 要增加的层数（>0）
     */
    public void increaseWeakArmor(int layers) {
        if (this.weakArmor <= 0 || layers <= 0)
            return;
        this.weakArmor += layers;
        this.sync();
    }

    /**
     * 减少弱效护盾层数（最小到 0）；减到 0 时一并清除持续时间与抵挡规则。
     * 
     * @param layers 要减少的层数（>0）
     */
    public void decreaseWeakArmor(int layers) {
        if (layers <= 0)
            return;
        this.weakArmor = Math.max(0, this.weakArmor - layers);
        if (this.weakArmor == 0) {
            this.weakArmorTicks = -1;
            this.blockedDeathReasons.clear();
            this.blockAllDeathReasons = false;
        }
        this.sync();
    }

    /** 减少指定层数的弱效护盾（与 decreaseWeakArmor 等价）。 */
    public void removeWeakArmor(int amount) {
        decreaseWeakArmor(amount);
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
                for (var t : SREArmorPlayerComponent.canSynced) {
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
            this.weakArmor = 0;
            this.weakArmorTicks = -1;
            this.blockAllDeathReasons = false;
            return;
        }
    }

    @Override
    public void serverTick() {
        if (!checkIsGameRunning()) {
            this.weakArmor = 0;
            this.weakArmorTicks = -1;
            this.blockAllDeathReasons = false;
            return;
        }
        // CCA冷冻：仅禁止CCA/职业执行tick，因此冻结弱效护盾的倒计时（不再减少、不超时消失）
        // 不需要，上游已冻结
        if (this.weakArmorTicks > 0) {
            this.weakArmorTicks--;
            if (this.weakArmorTicks <= 0) {
                // 弱效护盾超时消失
                this.removeWeakArmor();
            }
        }
    }

    @Override
    public void init() {
        this.weakArmor = 0;
        this.weakArmorTicks = -1;
        this.blockedDeathReasons.clear();
        this.blockAllDeathReasons = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.weakArmor = 0;
        this.weakArmorTicks = -1;
        this.blockedDeathReasons.clear();
        this.blockAllDeathReasons = false;
        this.sync();
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.weakArmor > 0) {
            tag.putInt("weakArmor", this.weakArmor);
        }
        if (this.weakArmorTicks >= 0) {
            tag.putInt("weakArmorTicks", this.weakArmorTicks);
        }
        tag.putBoolean("blockAllDeathReasons", this.blockAllDeathReasons);
        if (!this.blockedDeathReasons.isEmpty()) {
            ListTag list = new ListTag();
            for (ResourceLocation rl : this.blockedDeathReasons) {
                list.add(StringTag.valueOf(rl.toString()));
            }
            tag.put("blockedDeathReasons", list);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.weakArmor = tag.contains("weakArmor") ? tag.getInt("weakArmor") : 0;
        this.weakArmorTicks = tag.contains("weakArmorTicks") ? tag.getInt("weakArmorTicks") : -1;
        this.blockAllDeathReasons = tag.contains("blockAllDeathReasons") && tag.getBoolean("blockAllDeathReasons");
        this.blockedDeathReasons.clear();
        if (tag.contains("blockedDeathReasons")) {
            ListTag list = tag.getList("blockedDeathReasons", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation rl = ResourceLocation.tryParse(list.getString(i));
                if (rl != null) {
                    this.blockedDeathReasons.add(rl);
                }
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
