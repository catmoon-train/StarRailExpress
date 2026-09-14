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
        registerAll(config);
        SRE.LOGGER.info("[CustomModifier] Loaded {} custom modifiers", config.modifiers.size());
    }

    /** 客户端重载（从 config 目录读取同步副本）。 */
    public static void reloadClient() {
        removeAll();
        CustomModifierConfig config = CustomModifierConfig.loadFromDefaultPath();
        registerAll(config);
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
            }
            HMLModifiers.MODIFIERS.remove(modifier);
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

    private static void registerAll(CustomModifierConfig config) {
        if (config == null || config.modifiers == null)
            return;
        List<CustomModifierData> pending = new ArrayList<>();
        // 第一遍：创建并注册（关联关系需要所有实例都存在，放到第二遍）
        for (CustomModifierData data : config.modifiers) {
            try {
                if (data == null || data.englishId == null || data.englishId.isBlank())
                    continue;
                SREModifier modifier = createModifier(data);
                if (modifier == null)
                    continue;
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
        if (findExistingById(data.englishId) != null) {
            SRE.LOGGER.error("[CustomModifier] Duplicated modifier id: {}", data.englishId);
            return null;
        }

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

        HMLModifiers.registerModifier(modifier);
        return modifier;
    }

    /** 应用关联设置（双向/单向关联与移除，仅作用于介绍页面）。 */
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
    }

    // ==================== 查询 ====================

    /** 按 englishId 取配置数据。 */
    public static CustomModifierData getCustomModifierData(String englishId) {
        return loadedModifiers.get(englishId);
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
        for (SREModifier modifier : HMLModifiers.MODIFIERS) {
            if (modifier.identifier() != null && modifier.identifier().getPath().equals(path))
                return modifier;
        }
        return null;
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
