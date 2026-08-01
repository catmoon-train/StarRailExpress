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

package org.agmas.noellesroles.config;

import com.google.gson.annotations.JsonAdapter;
import io.wifi.ConfigCompact.ConfigClassHandler;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;

import java.util.ArrayList;
import java.util.HashMap;

@Config(name = "starrailexpress-spawninfo")
public class SpawnInfoConfig implements ConfigData {

    public static ConfigClassHandler<SpawnInfoConfig> HANDLER = new ConfigClassHandler<>(
            SpawnInfoConfig.class);

    public static class SpawnInfo {
        /**
         * 最小启用玩家数。-1禁用
         */
        public int minEnabledPlayer = -1;
        /**
         * 启用概率，1 = 1/10000。-1禁用
         */
        public int enableChance = -1;
        /**
         * 最大启用玩家数。-1禁用
         */
        public int maxEnabledPlayer = -1;
        /**
         * 最大刷新数量
         */
        public int maxSpawn = 1;
        /**
         * 在什么地图刷新。为空全部
         */
        public ArrayList<String> map = new ArrayList<>();

        public SpawnInfo addMaps(String... maps) {
            for (var t : maps) {
                map.add(t);
            }
            return this;
        }

        public SpawnInfo addMaps(ArrayList<String> maps) {
            map.addAll(maps);
            return this;
        }

        public SpawnInfo setMaps(ArrayList<String> maps) {
            map.clear();
            map.addAll(maps);
            return this;
        }

        public SpawnInfo setMaps(String... maps) {
            this.map.clear();
            for (var t : maps) {
                map.add(t);
            }
            return this;
        }

        public SpawnInfo setMaxEnabledPlayer(int num) {
            this.maxEnabledPlayer = num;
            return this;
        }

        public SpawnInfo setMinEnabledPlayer(int num) {
            this.minEnabledPlayer = num;
            return this;
        }

        public SpawnInfo setEnableChance(int num) {
            this.enableChance = num;
            return this;
        }

        public SpawnInfo setMax(int max) {
            this.maxSpawn = max;
            return this;
        }

        public SpawnInfo() {
        }

        public SpawnInfo(int defaultMinPlayer, int defaultMaxPlayer, int defaultEnableChance, int maxSpawn) {
            this.minEnabledPlayer = defaultMinPlayer;
            this.maxEnabledPlayer = defaultMaxPlayer;
            this.enableChance = defaultEnableChance;
            this.maxSpawn = maxSpawn;
        }

        public SpawnInfo(int defaultMinPlayer, int defaultMaxPlayer, int defaultEnableChance, int maxSpawn,
                ArrayList<String> defaultMaps) {
            this.minEnabledPlayer = defaultMinPlayer;
            this.maxEnabledPlayer = defaultMaxPlayer;
            this.maxSpawn = maxSpawn;
            this.enableChance = defaultEnableChance;
            this.map = new ArrayList<>(defaultMaps);
        }
    }

    @JsonAdapter(RoleSpawnInfoEntriesAdapter.class)
    public static class RoleSpawnInfoEntries {
        public HashMap<ResourceLocation, SpawnInfo> maps = new HashMap<>();
        public int type; // 自动根据 T 设置

        // 无参构造（供 Gson 反序列化使用）
        public RoleSpawnInfoEntries() {
            this.type = 0; // 默认未知
        }

        public SpawnInfo getSpawnInfo(SREModifier modifier) {
            return maps.getOrDefault(modifier.identifier(), null);
        }

        public SpawnInfo getSpawnInfo(SRERole role) {
            return maps.getOrDefault(role.identifier(), null);
        }

        // 内部构造，用于工厂方法
        private RoleSpawnInfoEntries(int type) {
            this.type = type;
        }

        // 根据类型获取对应的 type 值
        private static int getTypeForClass(Class<?> clazz) {
            if (SRERole.class.isAssignableFrom(clazz)) {
                return 1;
            } else if (SREModifier.class.isAssignableFrom(clazz)) {
                return 2;
            }
            return 0;
        }

        // 工厂方法：创建角色默认配置
        public static RoleSpawnInfoEntries createDefaultRoleInfo() {
            RoleSpawnInfoEntries obj = new RoleSpawnInfoEntries(getTypeForClass(SRERole.class));
            for (var entry : TMMRoles.ROLES.entrySet()) {
                SRERole role = entry.getValue();
                if (!role.canSetSpawnInfoInConfig())
                    continue;
                obj.maps.put(entry.getKey(), new SpawnInfo(
                        role.defaultEnableNeedPlayerCount,
                        role.defaultEnableMaxPlayerCount,
                        role.defaultEnableChance,
                        role.defaultMaxCount,
                        role.defaultSpawnMaps));
            }
            return obj;
        }

        // 工厂方法：创建修饰符默认配置
        public static RoleSpawnInfoEntries createDefaultModifierInfo() {
            RoleSpawnInfoEntries obj = new RoleSpawnInfoEntries(getTypeForClass(SREModifier.class));
            for (SREModifier entry : HMLModifiers.MODIFIERS) {
                if (!entry.canSetSpawnInfoInConfig())
                    continue;
                obj.maps.put(entry.identifier(), new SpawnInfo(
                        entry.defaultNeedPlayerCount,
                        entry.defaultMaxPlayerCount,
                        entry.defaultEnableChance,
                        entry.defaultMaxCount,
                        entry.defaultSpawnMaps));
            }
            return obj;
        }
    }

    @ConfigEntry.Gui.Excluded
    public RoleSpawnInfoEntries roleDetails = RoleSpawnInfoEntries.createDefaultRoleInfo();
    @ConfigEntry.Gui.Excluded
    public RoleSpawnInfoEntries modifierDetails = RoleSpawnInfoEntries.createDefaultModifierInfo();
    public static SpawnInfoConfig instance() {
        return HANDLER.instance();
    }
}
