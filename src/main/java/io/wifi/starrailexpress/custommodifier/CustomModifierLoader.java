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

package io.wifi.starrailexpress.custommodifier;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 自定义修饰符加载器。
 *
 * <p>
 * 与 {@code CustomRoleLoader} 对应：服务端从存档读 {@code sre_custom_modifiers.json} 建修饰符，
 * 客户端从 config 目录读同步副本，统一注册进 {@link HMLModifiers#MODIFIERS}。
 */
public final class CustomModifierLoader {

    /** englishId -> 配置数据 */
    private static final Map<String, CustomModifierData> loadedModifiers = new HashMap<>();
    /** englishId -> 已注册的修饰符实例 */
    private static final Map<String, SREModifier> registeredModifiers = new HashMap<>();

    private CustomModifierLoader() {
    }

    // ==================== 重载 ====================

    /** 服务端重载（从世界存档读取，权威）。 */
    public static void reload(MinecraftServer server) {
        removeAll();
        CustomModifierConfig config;
        if (server != null) {
            Path worldPath = server.getWorldPath(LevelResource.ROOT);
            config = CustomModifierConfig.loadFromFile(worldPath);
        } else {
            config = CustomModifierConfig.getInstance();
        }
        if (config == null || config.modifiers == null) {
            config = new CustomModifierConfig();
        }
        registerAll(config, server);
        SRE.LOGGER.info("[CustomModifier] Loaded {} custom modifiers", config.modifiers.size());
    }

    /** 客户端重载（从 config 目录读取同步副本）。 */
    public static void reloadClient() {
        removeAll();
        CustomModifierConfig config = CustomModifierConfig.loadFromDefaultPath();
        registerAll(config, null);
        SRE.LOGGER.info("[CustomModifier-Client] Reloaded {} custom modifiers from local config",
                config.modifiers == null ? 0 : config.modifiers.size());
    }

    /** 清理所有已注册的自定义修饰符（离开服务器 / 重载前）。 */
    public static void removeAll() {
        for (SREModifier modifier : new ArrayList<>(HMLModifiers.MODIFIERS)) {
            if (!CustomModifierEntry.isCustomModifier(modifier))
                continue;
            // 先清掉其它职业/修饰符里对它的关联引用，避免重载后残留旧实例
            for (SRERole role : TMMRoles.ROLES.values()) {
                role.relatedModifiers.remove(modifier);
            }
            for (SREModifier other : HMLModifiers.MODIFIERS) {
                other.relatedModifiers.remove(modifier);
                // 互斥列表同样要清，否则重载后旧实例残留在 opposingModifiers 里，
                // 既会造成互斥判定失效（按实例比较），也会让介绍页出现重复条目
                other.opposingModifiers.remove(modifier);
            }
            // 走注册表接口，保证 MODIFIERS_BY_PATH 索引与列表同步，
            // 否则重载后同名自定义修饰符会被判成重复而注册失败
            HMLModifiers.unregisterModifier(modifier);
        }
        loadedModifiers.clear();
        registeredModifiers.clear();
        HMLModifiers.refreshVersionTags();
    }

    /** 与 {@code CustomRoleLoader.removeClientCache()} 对应。 */
    public static void removeClientCache() {
        removeAll();
    }

    // ==================== 注册 ====================

    /**
     * 注册所有配置中的自定义修饰符。
     *
     * @param server 服务端重载时传入，注册失败（id 冲突）会向全体玩家播报；客户端传 {@code null}
     */
    private static void registerAll(CustomModifierConfig config, MinecraftServer server) {
        if (config == null || config.modifiers == null)
            return;
        List<CustomModifierData> pending = new ArrayList<>();
        // 第一遍：创建并注册（关联关系需要所有实例都存在，放到第二遍）
        for (CustomModifierData data : config.modifiers) {
            try {
                if (data == null || data.englishId == null || data.englishId.isBlank())
                    continue;
                SREModifier modifier = createModifier(data);
                if (modifier == null) {
                    notifyDuplicated(server, data);
                    continue;
                }
                registeredModifiers.put(data.englishId, modifier);
                loadedModifiers.put(data.englishId, data);
                pending.add(data);
            } catch (Exception e) {
                SRE.LOGGER.error("[CustomModifier] Failed to register modifier: {}",
                        data == null ? "null" : data.englishId, e);
            }
        }
        // 第二遍：关联设置（仅作用于介绍页面）
        for (CustomModifierData data : pending) {
            SREModifier modifier = registeredModifiers.get(data.englishId);
            if (modifier == null)
                continue;
            try {
                applyRelations(data, modifier);
            } catch (Exception e) {
                SRE.LOGGER.error("[CustomModifier] Failed to apply relations: {}", data.englishId, e);
            }
        }
        HMLModifiers.refreshVersionTags();
    }

    /** 依据配置创建一个自定义修饰符并注册进 {@link HMLModifiers#MODIFIERS}。 */
    public static SREModifier createModifier(CustomModifierData data) {
        if (data == null || data.englishId == null || data.englishId.isBlank())
            return null;

        SREModifier modifier = new CustomModifierEntry(data);

        // 基础
        modifier.setHidden(data.hidden);

        // 生成设置
        modifier.setDefaultMax(data.defaultMax);
        modifier.setDefaultEnableChance(data.defaultEnableChance);
        modifier.setDefaultEnableNeededPlayerCount(data.enableNeededPlayerCount);
        modifier.setDefaultMaxPlayerCount(data.enableMaxPlayerCount);
        if (data.spawnMaps != null && !data.spawnMaps.isEmpty()) {
            modifier.setDefaultSpawnMaps(data.spawnMaps.toArray(new String[0]));
        }

        // 生成限制：阵营
        RoleTeam[] cannotTeams = parseTeams(data.cannotAppliedToTeams);
        if (cannotTeams.length > 0) {
            modifier.setCannotAppliedToTeam(cannotTeams);
        }
        RoleTeam[] onlyTeams = parseTeams(data.canOnlyAppliedToTeams);
        if (onlyTeams.length > 0) {
            modifier.setCanOnlyBeAppliedToTeam(onlyTeams);
        }
        // 生成限制：职业
        HashSet<SRERole> cannotRoles = parseRoles(data.cannotBeAppliedTo);
        if (!cannotRoles.isEmpty()) {
            modifier.setCannotBeAppliedTo(cannotRoles);
        }
        HashSet<SRERole> onlyRoles = parseRoles(data.canOnlyBeAppliedTo);
        if (!onlyRoles.isEmpty()) {
            modifier.setCanOnlyBeAppliedTo(onlyRoles);
        }

        // 运行时：每刻检查全局效果 / 条件触发
        CustomModifierRuntime.init();
        modifier.setServerGameTickEvent(player -> CustomModifierRuntime.serverTick(player, modifier));

        SREModifier registered = HMLModifiers.registerCustomModifier(modifier);
        if (registered == null) {
            SRE.LOGGER.error("[CustomModifier] Skipped duplicated modifier id: {}", data.englishId);
            return null;
        }
        return registered;
    }

    /**
     * 注册失败（id 冲突）时在服务端向全体玩家播报，与 {@code CustomRoleLoader} 的重复职业提示保持一致。
     * 客户端重载与单机无玩家列表时不播报，只留日志。
     */
    private static void notifyDuplicated(MinecraftServer server, CustomModifierData data) {
        if (server == null)
            return;
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("sre.custom_modifier.error.duplicated", displayNameOf(data), data.englishId)
                        .withStyle(ChatFormatting.RED),
                false);
    }

    /** 播报用名称：优先显示名，未填写时回退到英文 ID。 */
    private static String displayNameOf(CustomModifierData data) {
        if (data == null)
            return "";
        if (data.displayName == null || data.displayName.isBlank())
            return data.englishId == null ? "" : data.englishId;
        return data.displayName;
    }

    /**
     * 应用关联与互斥设置。
     *
     * <p>
     * 关联字段（{@code bothRelated*} / {@code related*} / {@code removeRelated*}）仅作用于介绍页面；
     * 互斥字段（{@code twoWayOpposingModifiers} / {@code opposingModifiers}）还会影响生成与运行时
     * （见 {@code ModifierOpposingHelper}）。
     */
    private static void applyRelations(CustomModifierData data, SREModifier modifier) {
        for (String id : safe(data.bothRelatedRoles)) {
            SRERole role = findRole(id);
            if (role != null)
                modifier.addBothRelatedRole(role);
        }
        for (String id : safe(data.relatedRoles)) {
            SRERole role = findRole(id);
            if (role != null)
                modifier.addRelatedRole(role);
        }
        for (String id : safe(data.removeRelatedRoles)) {
            SRERole role = findRole(id);
            if (role != null)
                modifier.removeRelatedRole(role);
        }
        for (String id : safe(data.bothRelatedModifiers)) {
            SREModifier other = findModifier(id);
            if (other != null && other != modifier)
                modifier.addBothRelatedModifier(other);
        }
        for (String id : safe(data.relatedModifiers)) {
            SREModifier other = findModifier(id);
            if (other != null && other != modifier)
                modifier.addRelatedModifier(other);
        }
        for (String id : safe(data.removeRelatedModifiers)) {
            SREModifier other = findModifier(id);
            if (other != null)
                modifier.removeRelatedModifier(other);
        }
        // 互斥修饰符：双向声明会同时写入对方，因此只在一侧填写也能生效
        for (String id : safe(data.twoWayOpposingModifiers)) {
            SREModifier other = findModifier(id);
            if (other != null && other != modifier)
                modifier.addTwoWayOpposingModifier(other);
        }
        for (String id : safe(data.opposingModifiers)) {
            SREModifier other = findModifier(id);
            if (other != null && other != modifier)
                modifier.addOpposingModifier(other);
        }
    }

    // ==================== 查询 ====================

    /** 按 englishId 取配置数据。 */
    public static CustomModifierData getCustomModifierData(String englishId) {
        return loadedModifiers.get(englishId);
    }

    /**
     * 按 englishId 取配置数据，找不到时忽略大小写再找一次。
     *
     * <p>
     * 修饰符实例的 {@code identifier} 会把 id 转成小写，而注册表的键用的是配置里原始的大小写。
     * 工具里若只改了 id 的大小写，玩家身上已有的旧实例按原名就会查不到，
     * 会被误判成「已删除」而停用；这里补一次忽略大小写的匹配。
     * 不会歧义：{@link HMLModifiers#registerModifier(SREModifier)} 按小写后的 path 全局去重，
     * 仅大小写不同的两个 id 只有一个能注册成功。
     */
    public static CustomModifierData getCustomModifierDataIgnoreCase(String englishId) {
        if (englishId == null || englishId.isBlank())
            return null;
        CustomModifierData exact = loadedModifiers.get(englishId);
        if (exact != null)
            return exact;
        for (Map.Entry<String, CustomModifierData> entry : loadedModifiers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(englishId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 该修饰符对应的自定义配置数据（非自定义修饰符返回 null）。 */
    public static CustomModifierData getDataOf(SREModifier modifier) {
        if (!(modifier instanceof CustomModifierEntry entry))
            return null;
        return entry.getData();
    }

    /** 取已注册的自定义修饰符实例。 */
    public static SREModifier getRegisteredModifier(String englishId) {
        return registeredModifiers.get(englishId);
    }

    /** 已加载的自定义修饰符数据快照（用于编辑界面）。 */
    public static List<CustomModifierData> getAllData() {
        return new ArrayList<>(loadedModifiers.values());
    }

    /**
     * 查找已注册的同名自定义修饰符（限于 {@link CustomModifierData#NAMESPACE}，忽略大小写）。
     *
     * <p>
     * 仅作查询用：实际注册时的去重由 {@link HMLModifiers#registerCustomModifier} 按 path 全局判定，
     * 比这里更严格（自定义修饰符也会与内置修饰符的 path 冲突）。
     */
    public static SREModifier findExistingById(String englishId) {
        for (SREModifier modifier : HMLModifiers.MODIFIERS) {
            if (modifier.identifier() != null
                    && CustomModifierData.NAMESPACE.equals(modifier.identifier().getNamespace())
                    && modifier.identifier().getPath().equalsIgnoreCase(englishId)) {
                return modifier;
            }
        }
        return null;
    }

    /** 按「完整 id 或路径」查找职业。 */
    public static SRERole findRole(String configuredId) {
        if (configuredId == null || configuredId.isBlank())
            return null;
        String id = configuredId.trim();
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location != null) {
            SRERole role = TMMRoles.ROLES.get(location);
            if (role != null)
                return role;
        }
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        for (var entry : TMMRoles.ROLES.entrySet()) {
            if (entry.getKey().getPath().equals(path))
                return entry.getValue();
        }
        return null;
    }

    /** 按「完整 id 或路径」查找修饰符。 */
    public static SREModifier findModifier(String configuredId) {
        if (configuredId == null || configuredId.isBlank())
            return null;
        String id = configuredId.trim();
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location != null) {
            SREModifier modifier = HMLModifiers.getModifier(location);
            if (modifier != null)
                return modifier;
        }
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return HMLModifiers.getModifierByPath(path);
    }

    // ==================== 工具 ====================

    private static HashSet<SRERole> parseRoles(List<String> ids) {
        HashSet<SRERole> result = new HashSet<>();
        for (String id : safe(ids)) {
            SRERole role = findRole(id);
            if (role != null)
                result.add(role);
        }
        return result;
    }

    private static RoleTeam[] parseTeams(List<String> names) {
        List<RoleTeam> result = new ArrayList<>();
        for (String name : safe(names)) {
            try {
                result.add(RoleTeam.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                SRE.LOGGER.warn("[CustomModifier] Unknown team: {}", name);
            }
        }
        return result.toArray(new RoleTeam[0]);
    }

    private static List<String> safe(List<String> list) {
        return list == null ? List.of() : list;
    }
}
