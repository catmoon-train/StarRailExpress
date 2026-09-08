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

package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.SREConfig.AutoPresetInfo;
import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.api.EggRoleInterface;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.TouhouRoleInterface;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentManager;
import org.agmas.harpymodloader.modifiers.EggModifier;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.harpymodloader.modifiers.TouhouModifier;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.config.SpawnInfoConfig;
import org.agmas.noellesroles.config.SpawnInfoConfig.SpawnInfo;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;

import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class InitModRolesMax {
    public static Random random = new Random();
    public static boolean isEggEnabled = false;
    public static boolean isTouhouEnabled = false;

    public static void autoChangePresent() {
        // 自动切换预设：游戏结束时应用配置的预设，使其在下一局游戏中生效
        io.wifi.starrailexpress.SREConfig sreConfig = io.wifi.starrailexpress.SREConfig.instance();
        if (sreConfig.enableRoundBasedAutoPreset) {
            // 按游戏轮数自动切换预设
            sreConfig.roundBasedCurrentRound++;
            int round = sreConfig.roundBasedCurrentRound;
            int need = 0;
            AutoPresetInfo selectedInfo = null;
            for (AutoPresetInfo info : SREConfig.instance().roundBasedPreset) {
                need += info.advanceCount;
                if (round >= need) {
                    selectedInfo = info;
                    break;
                }
            }
            String nextPreset;
            if (selectedInfo != null) {
                nextPreset = selectedInfo.presetName;
            } else {
                nextPreset = sreConfig.roundBasedPresetAllRoles;
                sreConfig.enableRoundBasedAutoPreset = false;
                SREConfig.HANDLER.save();
                org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.save();
            }
            org.agmas.harpymodloader.config.HarpyModLoaderConfig hml = org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER
                    .instance();
            if (nextPreset == null || nextPreset.isBlank()) {
                // 全部职业启用：清空禁用列表
                hml.getDisabled().clear();
                hml.disabledModifiers.clear();
                sreConfig.enableRoundBasedAutoPreset = false;
                SREConfig.HANDLER.save();
                org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.save();
                SRE.LOGGER.info("[AutoPreset] 第{}局结束，已启用全部职业", round);
            } else {
                boolean applied = org.agmas.noellesroles.commands.PresetCommand.applyPresetByName(nextPreset);
                if (applied) {
                    SRE.LOGGER.info("[AutoPreset] 第{}局结束，已自动应用预设: {}", round, nextPreset);
                } else {
                    SRE.LOGGER.warn("[AutoPreset] 第{}局结束，未找到预设 '{}'，跳过自动切换", round, nextPreset);
                }
            }
            // 保存当前使用预设和已进行轮数到配置
            sreConfig.roundBasedCurrentPreset = (nextPreset != null) ? nextPreset : "";
            io.wifi.starrailexpress.SREConfig.HANDLER.save();
        }
    }

    public static int SPLIT_PERSONALITY_CHANCE = 10; // 10 in 100
    public static int REFUGEE_CHANCE = 10; // 10 in 100
    public static int EGGS_CHANCE = 10;
    public static int TOUHOU_CHANCE = 10;

    public static void registerStatics() {
        // 无需注册默认为1.
        // ==================== 设置角色数量限制 ====================
        // 某些角色可能需要限制每局游戏中的数量
        // 复仇者每局只能有 1 个
        // 同时出现
        Harpymodloader.addOccupationRole(ModRoles.ENGINEER, ModRoles.LOCKSMITH);
        Harpymodloader.addOccupationRole(ModRoles.MA_CHEN_XU, ModRoles.GUEST_GHOST);
        Harpymodloader.addOccupationRole(ModRoles.GANGSTERS, ModRoles.FITTER);

        RoleAssignmentManager.addOccupationRole(ModRoles.POISONER, ModRoles.DOCTOR);
        RoleAssignmentManager.addOccupationRole(ModRoles.INFECTED, ModRoles.DOCTOR);
        RoleAssignmentManager.addOccupationRole(ModRoles.DIO, ModRoles.JOJO);
        RoleAssignmentManager.addOccupationRole(ModRoles.WATER_GHOST, ModRoles.DIVER);

        Harpymodloader.setRoleMaximum(ModRoles.CONDUCTOR_ID, 0);
        Harpymodloader.setRoleMaximum(THRedHouseRoles.MAID_SAKUYA, 0);
        Harpymodloader.setRoleMaximum(ModRoles.DIO, 0);
        Harpymodloader.setRoleMaximum(ModRoles.BETTER_VIGILANTE, 0);
        Harpymodloader.setRoleMaximum(THRedHouseRoles.BAKA, 0);
        Harpymodloader.setRoleMaximum(THRedHouseRoles.HOAN_MEIRIN, 0);
        Harpymodloader.setRoleMaximum(THRedHouseRoles.PACHURI, 0);
        Harpymodloader.setRoleMaximum(THRedHouseRoles.FURANDORU, 0);
        Harpymodloader.setRoleMaximum(THRedHouseRoles.REMILIA, 0);
        Harpymodloader.setRoleMaximum(ModRoles.MANIPULATOR, 0);
        Harpymodloader.setRoleMaximum(ModRoles.EXECUTIONER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.VULTURE_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.JESTER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.MORPHLING_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.SILENCER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.BARTENDER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.NOISEMAKER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.PHANTOM_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.AWESOME_BINGLUS_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.SWAPPER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.VOODOO_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.CORONER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.RECALLER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.BROADCASTER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.GAMBLER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.GLITCH_ROBOT_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.GHOST_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.THIEF_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.MERCENARY_ID, 0);
        Harpymodloader.setRoleMaximum(ModRoles.BANDIT_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.BOMBER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.OLDMAN_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.JOJO_ID, 0);
        Harpymodloader.setRoleMaximum(ModRoles.CHEF_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.FORTUNETELLER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.WIND_YAOSE_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.RESCUER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.FIREFIGHTER_ID, 1);

        // 叛徒设置为0
        Harpymodloader.setRoleMaximum(TraitorAndModifiers.TRAITOR_ID, 0);

        // 飞行员和影隼初始为0

        // 设置飞行员和影隼绑定生成
        RoleAssignmentManager.addOccupationRole(ModRoles.SHADOW_FALCON, ModRoles.PILOT);

        // 设置猎人和驯马师绑定生成
        RoleAssignmentManager.addOccupationRole(ModRoles.HUNTER, ModRoles.TAMER);
    }

    public static void registerDynamic() {
        GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
            // 从配置应用角色概率
            applyRoleChanceFromConfig();

            // 获取当前地图ID
            String currentMap = "unknown";
            AreasSettings areasSettings = new AreasSettings();
            if (serverLevel.getServer() != null) {
                var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(serverLevel);
                if (areas != null && areas.mapName != null && areas.areasSettings != null) {
                    currentMap = areas.mapName;
                    areasSettings = areas.areasSettings;
                }
            }

            autoRoleMaxCount(serverLevel, gameWorldComponent, players, areasSettings);
            autoModifierMaxCount(serverLevel, gameWorldComponent, players, areasSettings);

            autoChangePresent();

            final int players_count = serverLevel.getServer().getPlayerCount();
            initModifiersCount(players_count);

            // 彩蛋角色/修饰符数量
            if (players_count >= NoellesRolesConfig.instance().minPlayerForEggRoles
                    && random.nextInt(0, 100) <= EGGS_CHANCE) {
                isEggEnabled = true;
                for (var a : TMMRoles.ROLES.values()) {
                    if (a instanceof EggRoleInterface) {
                        int max = a.getRoundMaxCount(serverLevel, gameWorldComponent, players, currentMap,
                                areasSettings);
                        if (max >= 0) {
                            Harpymodloader.setRoleMaximum(a, max);
                        }
                    }
                }

                for (var a : HMLModifiers.MODIFIERS) {
                    if (a instanceof EggModifier) {
                        int max = a.getRoundMaxCount(serverLevel, gameWorldComponent, players, currentMap);
                        if (max >= 0) {
                            Harpymodloader.MODIFIER_MAX.put(a.identifier(), max);
                        }
                    }
                }
            } else {
                isEggEnabled = false;

                for (var a : HMLModifiers.MODIFIERS) {
                    if (a instanceof EggModifier) {
                        Harpymodloader.MODIFIER_MAX.put(a.identifier(), 0);
                    }
                }
                for (var a : TMMRoles.ROLES.values()) {
                    if (a instanceof EggRoleInterface) {
                        Harpymodloader.setRoleMaximum(a, 0);
                    }
                }

            }

            {
                // 好人中立
                var neutralRoles = new ArrayList<SRERole>();
                for (var r : TMMRoles.ROLES.values()) {
                    if (SREDisableManager.isRoleDisabled(r))
                        continue;
                    if (r.isNeutrals() && r.isNeutralForInnocent()) {
                        int maxCount = Harpymodloader.ROLE_MAX.getOrDefault(r.identifier(), 0);
                        if (maxCount > 0) {
                            while (maxCount > 0) {
                                neutralRoles.add(r);
                                maxCount--;
                            }
                            Harpymodloader.setRoleMaximum(r, 0);
                        }
                    }
                }
                Collections.shuffle(neutralRoles);
                int neutralsForInnocent = 0;
                neutralsForInnocent = Math.max(0, (int) (players_count / 18f));
                for (int i = 0; i < neutralsForInnocent && i < neutralRoles.size(); i++) {
                    var r = neutralRoles.get(i);
                    Harpymodloader.setRoleMaximum(r, Harpymodloader.ROLE_MAX.getOrDefault(r.identifier(), 0) + 1);
                }
            }
            {
                // 杀手中立
                var neutralRoles = new ArrayList<SRERole>();
                for (var r : TMMRoles.ROLES.values()) {
                    if (SREDisableManager.isRoleDisabled(r))
                        continue;
                    if (r.isNeutrals() && r.isNeutralForKiller()) {
                        int maxCount = Harpymodloader.ROLE_MAX.getOrDefault(r.identifier(), 0);
                        if (maxCount > 0) {
                            while (maxCount > 0) {
                                neutralRoles.add(r);
                                maxCount--;
                            }
                            Harpymodloader.setRoleMaximum(r, 0);
                        }
                    }
                }
                Collections.shuffle(neutralRoles);
                int neutralForKillers = 0;
                neutralForKillers = Math.max(0, (int) (players_count / 9f));
                for (int i = 0; i < neutralForKillers && i < neutralRoles.size(); i++) {
                    var r = neutralRoles.get(i);
                    Harpymodloader.setRoleMaximum(r, Harpymodloader.ROLE_MAX.getOrDefault(r.identifier(), 0) + 1);
                }
            }
            // 动态大小
            Random random = new Random();

            // 获取配置
            NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();

            // 东方角色/修饰符数量
            if (players_count >= config.minPlayerForTouhouRoles && random.nextInt(0, 100) < TOUHOU_CHANCE) {
                isTouhouEnabled = true;
                for (var a : TMMRoles.ROLES.values()) {
                    if (a instanceof TouhouRoleInterface) {
                        int max = a.getRoundMaxCount(serverLevel, gameWorldComponent, players, currentMap,
                                areasSettings);
                        if (max >= 0) {
                            Harpymodloader.setRoleMaximum(a, max);
                        }
                    }
                }
                for (var a : HMLModifiers.MODIFIERS) {
                    if (a instanceof TouhouModifier) {
                        int max = a.getRoundMaxCount(serverLevel, gameWorldComponent, players, currentMap);
                        if (max >= 0) {
                            Harpymodloader.MODIFIER_MAX.put(a.identifier(), max);
                        }
                    }
                }
            } else {
                isTouhouEnabled = false;
                for (var a : TMMRoles.ROLES.values()) {
                    if (a instanceof TouhouRoleInterface) {
                        Harpymodloader.setRoleMaximum(a, 0);
                    }
                }
                for (var a : HMLModifiers.MODIFIERS) {
                    if (a instanceof TouhouModifier) {
                        Harpymodloader.setModifierMaximum(a, 0);
                    }
                }
            }

            applySpecialVigilanteRoles(serverLevel, players_count, config, random, currentMap);
        });
    }

    private static void applySpecialVigilanteRoles(ServerLevel serverLevel, int playersCount,
            NoellesRolesConfig config, Random random,
            String currentMap) {
        int limit = getSpecialVigilanteLimit(playersCount, config);
        ArrayList<SRERole> specialVigilantes = new ArrayList<>();
        var roleMaxBackup = new HashMap<>(Harpymodloader.ROLE_MAX);
        for (var role : TMMRoles.ROLES.values()) {
            // 跳过禁用的
            if (SREDisableManager.isRoleDisabled(role))
                continue;
            if (role.isSpecialVigilante() && roleMaxBackup.get(role.identifier()) > 0) {
                // 仅处理启用的
                specialVigilantes.add(role);
                Harpymodloader.setRoleMaximum(role, 0);
            }
        }
        if (limit <= 0) {
            return;
        }
        Collections.shuffle(specialVigilantes);
        ArrayList<SRERole> selected = new ArrayList<>();
        for (var role : specialVigilantes) {
            // 不需要比较 chance，初始化前已经比较过一次了。
            selected.add(role);
            if (role.canRefreshableSpecialVigilante()) {
                int secondChance = role.getRefreshableSpecialVigilanteChance();
                if (secondChance >= 0 && random.nextInt(0, 10000) < secondChance) {
                    selected.add(role);
                }
            }
        }
        while (selected.size() > limit) {
            selected.remove(random.nextInt(selected.size()));
        }
        for (var role : selected) {
            Harpymodloader.setRoleMaximum(role, Harpymodloader.ROLE_MAX.getOrDefault(role.identifier(), 0) + 1);
        }
    }

    private static int getSpecialVigilanteLimit(int playersCount, NoellesRolesConfig config) {
        if (playersCount >= config.minPlayerForSpecialPolice5) {
            return 5;
        }
        if (playersCount >= config.minPlayerForSpecialPolice4) {
            return 4;
        }
        if (playersCount >= config.minPlayerForSpecialPolice3) {
            return 3;
        }
        if (playersCount >= config.minPlayerForSpecialPolice2) {
            return 2;
        }
        if (playersCount >= config.minPlayerForSpecialPolice1) {
            return 1;
        }
        return 0;
    }

    private static void autoRoleMaxCount(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players, AreasSettings areasSettings) {
        var areacca = AreasWorldComponent.KEY.get(serverLevel);
        var mapName = areacca.mapName;
        for (var entry : TMMRoles.ROLES.entrySet()) {
            if (entry.getValue() instanceof TouhouRoleInterface)
                continue;
            if (entry.getValue() instanceof EggRoleInterface)
                continue;
            ResourceLocation name = entry.getKey();
            SRERole role = entry.getValue();
            int count = role.getRoundMaxCount(serverLevel, gameWorldComponent, players, mapName, areasSettings);
            if (count >= 0) {
                Harpymodloader.setRoleMaximum(name, count);
            }
        }
    }

    private static void autoModifierMaxCount(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players, AreasSettings areasSettings) {
        var areacca = AreasWorldComponent.KEY.get(serverLevel);
        var mapName = areacca.mapName;
        for (SREModifier modifier : HMLModifiers.MODIFIERS) {
            if (modifier instanceof TouhouModifier)
                continue;
            if (modifier instanceof EggModifier)
                continue;
            int count = modifier.getRoundMaxCount(serverLevel, gameWorldComponent, players, mapName);
            if (count >= 0 || count == -2) {
                Harpymodloader.MODIFIER_MAX.put(modifier.identifier(), count);
            }
        }
    }

    /**
     * 从配置应用角色概率设置
     */
    private static void applyRoleChanceFromConfig() {
        SpawnInfoConfig config = SpawnInfoConfig.instance();

        for (var entry : HMLModifiers.MODIFIERS) {
            SpawnInfo spinfo = config.modifierDetails.getSpawnInfo(entry);
            if (spinfo != null && entry.canSetSpawnInfoInConfig())
                entry.setSpawnInfo(spinfo);
        }
        for (var entry : TMMRoles.ROLES.entrySet()) {
            SpawnInfo spinfo = config.roleDetails.getSpawnInfo(entry.getValue());
            if (spinfo != null && entry.getValue().canSetSpawnInfoInConfig())
                entry.getValue().setSpawnInfo(spinfo);
        }

        // 对没有 enableChance 的杀手方中立职业，默认 max=1、概率 75%
        for (var entry : TMMRoles.ROLES.entrySet()) {
            var role = entry.getValue();
            if (role.isNeutrals()) {
                if (role.spawnInfo.maxSpawn < 0) {
                    // 不允许设置为 -1
                    role.setDefaultMax(1);
                }
            }
            if (role.spawnInfo.enableChance < 0 && role.canSetSpawnInfoInConfig()
                    && (role.isNeutrals() && (role.isNeutralForInnocent() || role.isNeutralForKiller()))) {
                role.spawnInfo.enableChance = 7500;
            }
        }
    }

    public static void initModifiersCount(int players) {
        Random random = new Random();
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        // LOVERS
        EGGS_CHANCE = config.chanceOfEggRoles;
        if (EGGS_CHANCE < 0) {
            EGGS_CHANCE = 0;
        }
        TOUHOU_CHANCE = config.chanceOfTouhouRoles;

        /// TINY
        Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("tiny"), players / random.nextInt(4, 18));

        /// TALL
        Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("tall"), players / random.nextInt(4, 18));

        /// FEATHER
        Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("feather"), players / random.nextInt(8, 32));

        /// TASKMASTER
        if (random.nextInt(0, 100) < config.chanceOfTaskmaster) {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("taskmaster"), players / random.nextInt(8, 24));
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("taskmaster"), 0);
        }

        /// SECRETIVE
        if (players >= config.minPlayerForSecretive && random.nextInt(0, 100) < config.chanceOfSecretive) {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("secretive"), players / random.nextInt(8, 24));
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("secretive"), 0);
        }

        /// SPLIT_PERSONALITY
        if (Harpymodloader.MODIFIER_MAX.getOrDefault(SEModifiers.SPLIT_PERSONALITY.identifier(), 0) > 0) {
        } else {
            if (players >= config.minPlayerForLovers
                    && random.nextInt(0, 100) <= config.chanceOfModifierLovers) {
                StupidExpress.LOGGER.info("Modifier [Lovers] enabled in this round!");
                Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("lovers"), 1);
            } else {
                Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("lovers"), 0);
            }
        }
    }
}
