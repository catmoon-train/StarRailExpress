package org.agmas.harpymodloader.modifiers;

import io.wifi.starrailexpress.api.SREAbstractInfoClass;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.noellesroles.config.NoellesRolesConfig.SpawnInfo;

public class SREModifier extends SREAbstractInfoClass {
    private final Random random = new Random();
    private ResourceLocation identifier;
    public boolean canSetSpawnInfoInConfig = true;
    public int color;
    public HashSet<SRERole> cannotBeAppliedTo;
    public HashSet<SRERole> canOnlyBeAppliedTo;
    public boolean killerOnly;
    public boolean civilianOnly;
    public boolean notVigilante;
    public Consumer<ServerPlayer> serverTickEvent = null;
    public Consumer<Player> clientTickEvent = null;
    public int defaultMaxCount = -1;
    public SpawnInfo spawnInfo = new SpawnInfo();
    public int defaultEnableChance = 10000;
    public int defaultNeedPlayerCount = 6;
    public int defaultMaxPlayerCount = -1;
    public boolean isOtherModeRole = false;
    public ArrayList<String> defaultSpawnMaps = new ArrayList<>();

    /**
     * 添加与此相关的职业。用于职业介绍。
     * 
     * @return
     */
    public SREModifier addRelatedRole(SRERole... role) {
        for (var i : role) {
            if (i != null)
                this.relatedRoles.add(i);
        }
        return this;
    }

    /**
     * 删除与此相关的职业。用于职业介绍。
     * 
     * @return
     */
    public SREModifier removeRelatedRole(SRERole... role) {
        for (var i : role) {
            if (i != null)
                this.relatedRoles.remove(i);
        }
        return this;
    }

    /**
     * 添加与此相关的修饰符。用于职业介绍。
     * 
     * @return
     */
    public SREModifier addRelatedModifier(SREModifier... modifier) {
        for (var i : modifier) {
            if (i != null)
                this.relatedModifiers.add(i);
        }
        return this;
    }

    /**
     * 删除与此相关的修饰符。用于职业介绍。
     * 
     * @return
     */
    public SREModifier removeRelatedModifier(SREModifier... role) {
        for (var i : role) {
            if (i != null)
                this.relatedModifiers.remove(i);
        }
        return this;
    }

    /**
     * 添加显示FLAG
     */
    public SREModifier addFlag(String... flag) {
        for (var i : flag) {
            this.flags.add(i);
        }
        return this;
    }

    /**
     * 是否为指定flag
     * 
     * @param flags
     * @return
     */
    public boolean isFlag(String... flags) {
        for (var f : flags) {
            if (!this.flags.contains(f))
                return false;
        }
        return true;

    }

    /**
     * 是否为指定flag，带inner.的标签。
     * 
     * @param flags
     * @return
     */
    public boolean isFlagWithInner(Set<String> flags) {
        var test = new HashSet<>(flags);
        if (test.contains("inner.enable")) {
            test.remove("inner.enable");
            if (SREDisableManager.isModifierDisabled(this))
                return false;
        }
        if (test.contains("inner.disable")) {
            test.remove("inner.disable");
            if (!SREDisableManager.isModifierDisabled(this))
                return false;
        }
        return this.flags.containsAll(test);
    }

    /**
     * 是否为指定flag
     * 
     * @param flags
     * @return
     */
    public boolean isFlag(HashSet<String> flags) {
        return this.flags.containsAll(flags);
    }

    /**
     * 获取显示FLAG
     */
    public HashSet<String> getFlags() {
        return this.flags;
    }

    /**
     * 删除显示FLAG
     */
    public SREModifier removeFlag(String... flag) {
        for (var i : flag) {
            this.flags.remove(i);
        }
        return this;
    }

    public SREModifier setCanSetSpawnInfoInConfig(boolean flag) {
        this.canSetSpawnInfoInConfig = flag;
        return this;
    }

    public boolean canSetSpawnInfoInConfig() {
        return this.canSetSpawnInfoInConfig;
    }

    public SREModifier setClientGameTickEvent(Consumer<Player> event) {
        this.clientTickEvent = event;
        return this;
    };

    public SREModifier setServerGameTickEvent(Consumer<ServerPlayer> event) {
        this.serverTickEvent = event;
        return this;
    };

    public void autoGameTickEvent(Player player) {
        if (player instanceof ServerPlayer sl) {
            this.serverGameTickEvent(sl);
        } else {
            this.clientGameTickEvent(player);
        }
    }

    public void clientGameTickEvent(Player player) {
        if (clientTickEvent != null)
            clientTickEvent.accept(player);
    }

    public void serverGameTickEvent(ServerPlayer player) {
        if (serverTickEvent != null)
            serverTickEvent.accept(player);
    }

    /**
     * 在启用的状态下，默认的最大分配数量。
     * 
     * @param count 最大数量
     * @return
     */
    public SREModifier setDefaultMax(int count) {
        defaultMaxCount = count;
        return this;
    };

    public SREModifier addDefaultSpawnMaps(String... maps) {
        return this.setDefaultSpawnMaps(maps);
    };

    public SREModifier setDefaultSpawnMaps(String... maps) {
        for (String s : maps) {
            this.defaultSpawnMaps.add(s);
        }
        return this;
    };

    /**
     * 默认启用最大玩家数 -1禁用
     * 
     * @param count
     * @return
     */
    public SREModifier setDefaultMaxPlayerCount(int count) {
        defaultMaxPlayerCount = count;
        return this;
    };

    /**
     * 默认需要玩家数
     * 
     * @param count
     * @return
     */
    public SREModifier setDefaultEnableNeededPlayerCount(int count) {
        defaultNeedPlayerCount = count;
        return this;
    };

    /**
     * 默认启用概率（1/10000）
     * 
     * @param chance
     * @return
     */
    public SREModifier setDefaultEnableChance(int chance) {
        defaultEnableChance = chance;
        return this;
    };

    /**
     * 生成设置
     * 
     * @param chance
     * @return
     */
    public SREModifier setSpawnInfo(SpawnInfo spinfo) {
        this.spawnInfo = spinfo;
        return this;
    };

    /**
     * 修饰符普通玩家不可视
     * 隐藏修饰符
     * 
     * @return
     */
    public SREModifier setHidden(boolean flag) {
        if (flag)
            this.addFlag("inner.hidden");
        else
            this.removeFlag("inner.hidden");
        return this;
    }

    public SREModifier(ResourceLocation identifier, int color, HashSet<SRERole> cannotBeAppliedTo,
            HashSet<SRERole> canOnlyBeAppliedTo, boolean killerOnly, boolean civilianOnly) {
        this.identifier = identifier;
        this.color = color;
        this.cannotBeAppliedTo = cannotBeAppliedTo;
        this.canOnlyBeAppliedTo = canOnlyBeAppliedTo;
        this.killerOnly = killerOnly;
        this.civilianOnly = civilianOnly;
    }

    @Override
    public ResourceLocation identifier() {
        return this.identifier;
    }

    @Override
    public Component getName() {
        return getName(false);
    }

    public MutableComponent getName(boolean color) {
        String key = "announcement.star.modifier." + identifier().toLanguageKey();
        // if (!Language.getInstance().has(key)) {
        // return Component.translatable("info.screen.role.name.error", key);
        // }
        final MutableComponent text = Component
                .translatable(key);
        if (color) {
            return text.withColor(color());
        }
        return text;
    }

    @Override
    public int color() {
        return this.color;
    }

    public HashSet<SRERole> canOnlyBeAppliedTo() {
        return canOnlyBeAppliedTo;
    }

    public HashSet<SRERole> cannotBeAppliedTo() {
        return cannotBeAppliedTo;
    }

    public void setCannotBeAppliedTo(HashSet<SRERole> cannotBeAppliedTo) {
        this.cannotBeAppliedTo = cannotBeAppliedTo;
    }

    public void setCanOnlyBeAppliedTo(HashSet<SRERole> canOnlyBeAppliedTo) {
        this.canOnlyBeAppliedTo = canOnlyBeAppliedTo;
    }

    /**
     * 获取一局里最大可出现此修饰符数量。-1表示不变。
     * 
     * @param gameWorldComponent
     * @param serverLevel
     * @param players
     * @return
     */
    public int getRoundMaxCount(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players, String mapName) {
        if (defaultMaxCount == -1)
            return -1;
        // 优先使用 spawnInfo（来自用户配置），若未设置则不回退。如果要设置默认的请设置canSetSpawnInfoInConfig为false
        int chance = this.spawnInfo.enableChance;
        if (chance >= 0) {
            int nchance = random.nextInt(0, 10000);
            if (nchance > chance) {
                return 0;
            }
        }
        int minPlayer = this.spawnInfo.minEnabledPlayer;
        if (minPlayer >= 0) {
            int playerCount = players.size();
            if (playerCount < minPlayer) {
                return 0;
            }
        }
        int maxPlayer = this.spawnInfo.maxEnabledPlayer;
        if (maxPlayer >= 0) {
            int playerCount = players.size();
            if (playerCount > maxPlayer) {
                return 0;
            }
        }
        if (!this.spawnInfo.map.isEmpty()) {
            if (!this.spawnInfo.map.contains(mapName))
                return 0;
        }
        return spawnInfo.maxSpawn;
    }

    public SREModifier setCannotAppliedToVigilante(boolean flag) {
        this.notVigilante = flag;
        return this;
    }

    /**
     * 是否是"其它模式"的修饰符（用于U键职业介绍页面的模式筛选）
     * 
     * @return 是否为其他模式修饰符
     */
    public boolean isOtherModeRole() {
        return this.isOtherModeRole;
    }

    /**
     * 设置是否为"其它模式"的修饰符
     * 
     * @param isOtherModeRole 是否为其他模式修饰符
     * @return this
     */
    public SREModifier setOtherModeRole(boolean isOtherModeRole) {
        this.isOtherModeRole = isOtherModeRole;
        if (isOtherModeRole)
            this.canSetSpawnInfoInConfig = false;
        this.addFlag("other_gamemode");
        return this;
    }

    @Override
    public Component getDescription() {
        String key = "info.screen.modifier." + this.identifier().getPath();
        if (!Language.getInstance().has(key)) {
            return Component.translatable("info.screen.role.desc.error", key);
        }
        return Component.translatable(key);
    }

    @Override
    public Component getSimpleDescription() {
        String key = "info.screen.modifier." + this.identifier().getPath() + ".simple";
        if (!Language.getInstance().has(key) || Language.getInstance().getOrDefault(key, "").isEmpty()) {
            return getDescription();
        }
        return Component
                .translatable("info.screen.modifier." + this.identifier().getPath() + ".simple");
    }

    @Override
    public boolean hasSimpleDescription() {
        var id = this.identifier();
        String path = "info.screen.modifier." + id.getPath() + ".simple";
        if (!Language.getInstance().has(path)) {
            return false;
        }
        return true;
    }
}
