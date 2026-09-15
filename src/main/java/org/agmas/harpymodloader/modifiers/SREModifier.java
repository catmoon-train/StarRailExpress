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

package org.agmas.harpymodloader.modifiers;

import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.SREAbstractInfoClass;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.utils.RandomSelector;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.noellesroles.config.SpawnInfoConfig.SpawnInfo;

import java.util.*;
import java.util.function.Consumer;

public class SREModifier extends SREAbstractInfoClass {
    private ResourceLocation identifier;
    public boolean canSetSpawnInfoInConfig = true;
    public int color;
    public HashSet<SRERole> cannotBeAppliedTo = null;
    public HashSet<SRERole> canOnlyBeAppliedTo = null;
    public boolean killerOnly = false;
    public boolean civilianOnly = false;
    public boolean notVigilante = false;
    /**
     * 互斥修饰符：同一名玩家身上不会同时出现这些修饰符。
     *
     * <p>
     * 与 {@link SRERole#opposingRoles}（互斥职业）对应，共三处生效：
     * <ul>
     * <li>生成阶段：{@code SREMurderGameMode.canAssignModifierToPlayer} 会跳过已有互斥修饰符的玩家</li>
     * <li>运行时：{@link ModifierOpposingHelper} 兜底移除与该修饰符冲突的旧修饰符（后到者优先）</li>
     * <li>介绍页面（U 键）：「互斥修饰符」分组</li>
     * </ul>
     */
    public final HashSet<SREModifier> opposingModifiers = new HashSet<>();
    /**
     * 按阵营整体排除：这些阵营的职业不会获得此修饰符。
     * 与 {@link #cannotBeAppliedTo}（按具体职业排除）叠加生效，见 {@link #setCannotAppliedToTeam}。
     */
    public final EnumSet<RoleTeam> cannotBeAppliedToTeams = EnumSet.noneOf(RoleTeam.class);
    /**
     * 只作用于这些阵营：设置后只有它们的职业会获得此修饰符（为空表示不限制）。
     * 与 {@link #cannotBeAppliedToTeams} 同时设置时，黑名单优先否决。见 {@link #setCanOnlyBeAppliedToTeam}。
     */
    public final EnumSet<RoleTeam> canOnlyBeAppliedToTeams = EnumSet.noneOf(RoleTeam.class);
    public Consumer<ServerPlayer> serverTickEvent = null;
    public Consumer<Player> clientTickEvent = null;
    public int defaultMaxCount = 1;
    public SpawnInfo spawnInfo = new SpawnInfo();
    public int defaultEnableChance = 10000;
    public int defaultNeedPlayerCount = 6;
    public int defaultMaxPlayerCount = -1;
    public boolean isOtherModeRole = false;
    public ArrayList<String> defaultSpawnMaps = new ArrayList<>();

    @Override
    public SREModifier setAddedVersion(String versionName) {
        super.setAddedVersion(versionName);
        return this;
    }

    /**
     * 添加与此相关的职业。互相添加。用于职业介绍。
     * 
     * @return
     */
    public SREModifier addBothRelatedRole(SRERole... role) {
        for (var i : role) {
            if (i != null) {
                this.relatedRoles.add(i);
                i.addRelatedModifier(this);
            }
        }
        return this;
    }

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
     * 添加与此相关的修饰符。互相添加。用于职业介绍。
     * 
     * @return
     */
    public SREModifier addBothRelatedModifier(SREModifier... modifier) {
        for (var i : modifier) {
            if (i != null) {
                this.relatedModifiers.add(i);
                i.addRelatedModifier(this);
            }
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
     * 删除互斥修饰符（单向，只清本实例这一侧声明的记录）。
     *
     * @param modifier 要删除的修饰符，{@code null} 会被忽略
     * @return this
     */
    public SREModifier removeOpposingModifier(SREModifier... modifier) {
        for (var m : modifier) {
            if (m != null)
                this.opposingModifiers.remove(m);
        }
        return this;
    }

    /**
     * 获取互斥修饰符（按 identifier 去重，避免重载后的重复展示）。
     *
     * @return 互斥修饰符副本，可直接用于介绍页面渲染
     */
    public Set<SREModifier> getOpposingModifiers() {
        Set<SREModifier> result = new HashSet<>();
        Set<ResourceLocation> seen = new HashSet<>();
        for (SREModifier m : this.opposingModifiers) {
            if (m != null && m.identifier() != null && seen.add(m.identifier())) {
                result.add(m);
            }
        }
        return result;
    }

    /**
     * 添加双向互斥修饰符：双方都不会与对方出现在同一名玩家身上。
     *
     * @param modifier 互斥的修饰符，{@code null} 与自身会被忽略
     * @return this
     */
    public SREModifier addTwoWayOpposingModifier(SREModifier... modifier) {
        for (var m : modifier) {
            if (m == null || m == this)
                continue;
            this.opposingModifiers.add(m);
            m.opposingModifiers.add(this);
        }
        return this;
    }

    /**
     * 添加单向互斥修饰符：本修饰符不会被分配给已持有该修饰符的玩家。
     *
     * <p>
     * 判定时同时读取双方的声明（见 {@link #isOpposingModifier}），因此单向声明同样能阻止两者共存。
     *
     * @param modifier 互斥的修饰符，{@code null} 与自身会被忽略
     * @return this
     */
    public SREModifier addOpposingModifier(SREModifier... modifier) {
        for (var m : modifier) {
            if (m != null && m != this)
                this.opposingModifiers.add(m);
        }
        return this;
    }

    /**
     * 设置单向互斥修饰符（先清空原有列表）。
     *
     * @param modifiers 互斥修饰符，{@code null} 表示只清空
     * @return this
     */
    public SREModifier setOpposingModifiers(Collection<SREModifier> modifiers) {
        this.opposingModifiers.clear();
        if (modifiers != null) {
            for (SREModifier m : modifiers) {
                if (m != null && m != this)
                    this.opposingModifiers.add(m);
            }
        }
        return this;
    }

    /**
     * 是否为互斥关系（双向判定：任意一侧声明过即成立）。
     *
     * @param other 另一个修饰符，{@code null} 或自身时返回 {@code false}
     */
    public boolean isOpposingModifier(SREModifier other) {
        if (other == null || other == this)
            return false;
        return this.opposingModifiers.contains(other) || other.opposingModifiers.contains(this);
    }

    /**
     * 是否与给定集合中的任意一个修饰符互斥。
     *
     * @param others 待判定的修饰符集合，{@code null} 或空集合返回 {@code false}
     */
    public boolean isOpposingWithAny(Collection<SREModifier> others) {
        if (others == null || others.isEmpty())
            return false;
        for (SREModifier other : others) {
            if (isOpposingModifier(other))
                return true;
        }
        return false;
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

        Optional<String> versionTag = test.stream().filter((t) -> t.startsWith("inner.version.")).findFirst();
        if (versionTag.isPresent()) {
            String judgeVersion = versionTag.get();
            String version = judgeVersion.substring("inner.version.".length());
            if (this.addedVersion.equals(version)) {
                test.remove(judgeVersion);
            } else {
                return false;
            }
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
     * @param count 最大数量。-2代表不限制
     * @return
     */
    public SREModifier setDefaultMax(int count) {
        defaultMaxCount = count;
        this.spawnInfo.maxSpawn = defaultMaxCount;
        return this;
    };

    public SREModifier addDefaultSpawnMaps(String... maps) {
        return this.setDefaultSpawnMaps(maps);
    };

    public SREModifier setDefaultSpawnMaps(String... maps) {
        for (String s : maps) {
            this.defaultSpawnMaps.add(s);
        }
        this.spawnInfo.addMaps(maps);
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
        this.spawnInfo.maxEnabledPlayer = count;
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
        this.spawnInfo.minEnabledPlayer = count;

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
        this.spawnInfo.enableChance = chance;

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

    public SREModifier(ResourceLocation identifier, int color, Collection<SRERole> cannotBeAppliedTo,
            Collection<SRERole> canOnlyBeAppliedTo, boolean killerOnly, boolean civilianOnly) {
        this.identifier = identifier;
        this.color = color;
        if (cannotBeAppliedTo != null)
            this.cannotBeAppliedTo = new HashSet<SRERole>(cannotBeAppliedTo);
        if (canOnlyBeAppliedTo != null)
            this.canOnlyBeAppliedTo = new HashSet<SRERole>(canOnlyBeAppliedTo);
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
        String key = "announcement.star.modifier." + identifier().getPath();
        if (!Language.getInstance().has(key)) {
            key = "announcement.star.modifier." + identifier().toLanguageKey();
        }
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
        if (spawnInfo.maxSpawn == -1)
            return -1;
        // 优先使用 spawnInfo（来自用户配置），若未设置则不回退。如果要设置默认的请设置canSetSpawnInfoInConfig为false
        int chance = this.spawnInfo.enableChance;
        if (chance >= 0) {
            if (!RandomSelector.tryChance(chance, 10000)) {
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
     * 按阵营排除：不把此修饰符分配给这些阵营的职业（可多次调用叠加）。
     *
     * <p>
     * 例：{@code setCannotAppliedToTeam(RoleTeam.KILLER, RoleTeam.NEUTRAL_KILLER)}
     * 表示不给杀手与杀手方中立。
     *
     * @param teams 阵营，{@code null} 会被忽略
     * @return this
     */
    public SREModifier setCannotAppliedToTeam(RoleTeam... teams) {
        if (teams != null) {
            for (RoleTeam team : teams) {
                if (team != null) {
                    this.cannotBeAppliedToTeams.add(team);
                }
            }
        }
        return this;
    }

    /**
     * 取消 {@link #setCannotAppliedToTeam} 添加的阵营排除。
     *
     * @param teams 要取消的阵营，{@code null} 会被忽略
     * @return this
     */
    public SREModifier removeCannotAppliedToTeam(RoleTeam... teams) {
        if (teams != null) {
            for (RoleTeam team : teams) {
                if (team != null) {
                    this.cannotBeAppliedToTeams.remove(team);
                }
            }
        }
        return this;
    }

    /**
     * 该职业是否因阵营排除（{@link #cannotBeAppliedToTeams}）而不应获得此修饰符。
     *
     * @param role 目标职业，{@code null} 时返回 false
     */
    public boolean isTeamExcluded(SRERole role) {
        if (role == null || this.cannotBeAppliedToTeams.isEmpty()) {
            return false;
        }
        for (RoleTeam team : this.cannotBeAppliedToTeams) {
            if (team.matches(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 只作用于指定阵营：设置后，只有这些阵营的职业会获得此修饰符（可多次调用叠加，命中其中任意一个即可）。
     *
     * <p>
     * 例：{@code setCanOnlyBeAppliedToTeam(RoleTeam.CIVILIAN, RoleTeam.SHERIFF)} 表示只给好人阵营。
     * 若同时设置了 {@link #setCannotAppliedToTeam}，黑名单优先（命中黑名单必定不通过）。
     *
     * @param teams 阵营，{@code null} 会被忽略
     * @return this
     */
    public SREModifier setCanOnlyBeAppliedToTeam(RoleTeam... teams) {
        if (teams != null) {
            for (RoleTeam team : teams) {
                if (team != null) {
                    this.canOnlyBeAppliedToTeams.add(team);
                }
            }
        }
        return this;
    }

    /**
     * 取消 {@link #setCanOnlyBeAppliedToTeam} 添加的阵营白名单。
     *
     * @param teams 要取消的阵营，{@code null} 会被忽略
     * @return this
     */
    public SREModifier removeCanOnlyBeAppliedToTeam(RoleTeam... teams) {
        if (teams != null) {
            for (RoleTeam team : teams) {
                if (team != null) {
                    this.canOnlyBeAppliedToTeams.remove(team);
                }
            }
        }
        return this;
    }

    /**
     * 阵营综合判定：该职业是否允许获得此修饰符。
     *
     * <ul>
     * <li>命中 {@link #cannotBeAppliedToTeams} 中任意一个阵营 → 不允许</li>
     * <li>{@link #canOnlyBeAppliedToTeams} 非空且一个都没命中 → 不允许</li>
     * <li>两者都未设置（或白名单非空但职业未知）→ 允许</li>
     * </ul>
     *
     * @param role 目标职业，{@code null} 时只做黑名单判定
     */
    public boolean isTeamApplicable(SRERole role) {
        if (isTeamExcluded(role)) {
            return false;
        }
        if (this.canOnlyBeAppliedToTeams.isEmpty() || role == null) {
            // 职业未知时按原 canOnlyBeAppliedTo 的处理方式：不否决
            return true;
        }
        for (RoleTeam team : this.canOnlyBeAppliedToTeams) {
            if (team.matches(role)) {
                return true;
            }
        }
        return false;
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
        this.addFlag("inner.other_gamemode");
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
