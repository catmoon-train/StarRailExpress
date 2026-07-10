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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 弱效护盾组件
 * - 弱效护盾是限时的
 * - 只能抵挡一次来自特定伤害类型的伤害（默认为刀）
 * - 抵挡完伤害后弱效护盾立即消失
 */
public class SREWeakArmorPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREWeakArmorPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("weak_armor"), SREWeakArmorPlayerComponent.class);

    private final Player player;
    private SREGameWorldComponent gameWorldComponent = null;

    /** 弱效护盾层数（0 或 1） */
    public int weakArmor = 0;
    /** 弱效护盾剩余时间（ticks），-1 表示没有激活 */
    public int weakArmorTicks = -1;
    /** 弱效护盾可以抵挡的死亡原因列表 */
    public Set<ResourceLocation> blockedDeathReasons = new HashSet<>();

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
     * 给予弱效护盾
     * @param durationTicks 持续时间（ticks）
     * @param blockedReasons 可以抵挡的死亡原因列表
     */
    public void giveWeakArmor(int durationTicks, Set<ResourceLocation> blockedReasons) {
        this.weakArmor = 1;
        this.weakArmorTicks = durationTicks;
        this.blockedDeathReasons = new HashSet<>(blockedReasons);
        this.sync();
    }

    /**
     * 给予弱效护盾，使用默认持续时间
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
        return weakArmor > 0 && blockedDeathReasons.contains(deathReason);
    }

    /**
     * 消耗弱效护盾（抵挡一次伤害后调用）
     */
    public void consumeWeakArmor() {
        this.weakArmor = 0;
        this.weakArmorTicks = -1;
        this.blockedDeathReasons.clear();
        this.sync();
    }

    public void removeWeakArmor() {
        consumeWeakArmor();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        if (sp == this.player)
            return true;
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        }
        if (gameWorldComponent != null) {
            var role = gameWorldComponent.getRole(sp);
            if (role != null) {
                // 酒保可以看到弱效护盾
                return SREArmorPlayerComponent.canSyncedRolePaths.contains(role.identifier().getPath());
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
            return;
        }
    }

    @Override
    public void serverTick() {
        if (!checkIsGameRunning()) {
            this.weakArmor = 0;
            this.weakArmorTicks = -1;
            return;
        }
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
        this.sync();
    }

    @Override
    public void clear() {
        this.weakArmor = 0;
        this.weakArmorTicks = -1;
        this.blockedDeathReasons.clear();
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
