package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.SREConfig.AutoPresetInfo;
import io.wifi.starrailexpress.api.EggRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.data.MapStatusBarType;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentManager;
import org.agmas.harpymodloader.modifiers.EggModifier;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.harpymodloader.modifiers.TouhouModifier;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.config.SpawnInfoConfig;
import org.agmas.noellesroles.config.SpawnInfoConfig.SpawnInfo;
import org.agmas.noellesroles.role.BounsRoles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;

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
        // ==================== 设置角色数量限制 ====================
        // 某些角色可能需要限制每局游戏中的数量
        // 复仇者每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.AVENGER_ID, 1);

        // 捣蛋鬼每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.PRANKSTER_ID, 1);

        // 不应该刷新
        Harpymodloader.setRoleMaximum(SpecialGameModeRoles.CUSTOM_PENDING, 0);

        // 工程师每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.ENGINEER_ID, 1);
        Harpymodloader.setRoleMaximum(BounsRoles.BASEBALL_PLAYER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.LOCKSMITH_ID, 0);
        // 斗士每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.FIGHTER_ID, 1);

        // 小偷每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.THIEF_ID, 1);

        // 秉烛人每局最多 1 个（具体是否出现由动态规则控制）
        Harpymodloader.setRoleMaximum(ModRoles.CANDLE_BEARER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.REASONER_ID, 1);

        // 鹈鹕每局最多 1 个（具体是否出现由动态概率控制）
        Harpymodloader.setRoleMaximum(ModRoles.PELICAN_ID, 1);

        // 探员每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.AGENT_ID, 1);

        // 运动员每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.ATHLETE_ID, 1);

        // 明星每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.SUPERSTAR_ID, 1);

        // 退伍军人每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.VETERAN_ID, 1);

        // 歌手每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.SINGER_ID, 1);

        // 心理学家每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.PSYCHOLOGIST_ID, 1);

        // 咒法师每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.WARLOCK_ID, 1);

        // Dream（梦魇）每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.DREAM_ID, 1);

        // 嬉命人每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.EMBALMER_ID, 1);

        // 窃皮者每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.SKINCRAWLER_ID, 1);

        // 摄影师每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.PHOTOGRAPHER_ID, 1);

        // 阴谋家每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.CONSPIRATOR_ID, 1);

        // 设陷者每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.TRAPPER_ID, 1);

        // 炸弹客每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.BOMBER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.WATCHER_ID, 1);

        // 跟踪者每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.STALKER_ID, 1);

        // 慕恋者每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.ADMIRER_ID, 1);

        Harpymodloader.setRoleMaximum(ModRoles.POISONER, 1);

        Harpymodloader.setRoleMaximum(ModRoles.ADMIRER_ID, 1);

        // 傀儡师每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.PUPPETEER_ID, 1);

        // 记录员每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.RECORDER_ID, 1);

        // 监察员每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.MONITOR_ID, 1);

        // 故障机器人每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.GLITCH_ROBOT_ID, 1);

        // 年兽每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.NIAN_SHOU_ID, 1);

        // 游侠

        // 巡警

        // 特警（默认为0，在动态设置中根据地图判断）

        // 武术教官（默认为0，在动态设置中根据警卫数量判断）

        // 魔术师
        Harpymodloader.setRoleMaximum(ModRoles.MAGICIAN_ID, 1);

        // 迷失杀手 - 由动态概率控制
        Harpymodloader.setRoleMaximum(ModRoles.LOST_KILLER_ID, 0);

        // 强盗
        Harpymodloader.setRoleMaximum(ModRoles.BANDIT_ID, 1);
        // 悍匪
        Harpymodloader.setRoleMaximum(ModRoles.GANGSTERS_ID, 1);
        // 钳工
        Harpymodloader.setRoleMaximum(ModRoles.FITTER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.DIO_ID, 0);

        // 仇杀客 - 仅在12人及以上对局生成
        Harpymodloader.setRoleMaximum(ModRoles.BLOOD_FEUDIST_ID, 1);

        // 钟表匠 - 仅在12人及以上对局生成
        // 注意：具体限制在 InitModRolesMax 中设置

        // 更好的义警 - 仅在12人及以上对局生成，0.5%概率

        // 红海军 - 设置为0（不会自然生成，只能通过远征队修饰符获得）
        Harpymodloader.setRoleMaximum(ModRoles.BETTER_VIGILANTE_ID, 0);

        // 作家 - 默认为0，在 InitModRolesMax 中动态设置（0.5%概率刷新）
        Harpymodloader.setRoleMaximum(BounsRoles.WRITER_ID, 0);

        // 电报员 - 默认为0，在 InitModRolesMax 中动态设置（0.5%概率刷新）
        Harpymodloader.setRoleMaximum(BounsRoles.TELEGRAPHER_ID, 0);

        // 设置角色最大数量
        Harpymodloader.setRoleMaximum(ModRoles.POISONER_ID, 0);
        // 和医生一起生成
        Harpymodloader.setRoleMaximum(ModRoles.DOCTOR_ID, 0);
        Harpymodloader.setRoleMaximum(ModRoles.ATTENDANT_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.CORONER_ID, 1);

        // 同时出现
        Harpymodloader.addOccupationRole(ModRoles.ENGINEER, ModRoles.LOCKSMITH);
        Harpymodloader.addOccupationRole(RedHouseRoles.FURANDORU, RedHouseRoles.PACHURI);
        Harpymodloader.addOccupationRole(ModRoles.MA_CHEN_XU, ModRoles.GUEST_GHOST);
        Harpymodloader.addOccupationRole(ModRoles.GANGSTERS, ModRoles.FITTER);

        RoleAssignmentManager.addOccupationRole(ModRoles.POISONER, ModRoles.DOCTOR);
        RoleAssignmentManager.addOccupationRole(ModRoles.INFECTED, ModRoles.DOCTOR);
        RoleAssignmentManager.addOccupationRole(RedHouseRoles.BAKA, ModRoles.EXAMPLER);
        RoleAssignmentManager.addOccupationRole(ModRoles.DIO, ModRoles.JOJO);
        RoleAssignmentManager.addOccupationRole(ModRoles.WATER_GHOST, ModRoles.DIVER);

        Harpymodloader.setRoleMaximum(ModRoles.CONDUCTOR_ID, 0);
        Harpymodloader.setRoleMaximum(RedHouseRoles.MAID_SAKUYA, 0);
        Harpymodloader.setRoleMaximum(ModRoles.DIO, 0);
        Harpymodloader.setRoleMaximum(ModRoles.BETTER_VIGILANTE, 0);
        Harpymodloader.setRoleMaximum(RedHouseRoles.BAKA, 0);
        Harpymodloader.setRoleMaximum(RedHouseRoles.HOAN_MEIRIN, 0);
        Harpymodloader.setRoleMaximum(RedHouseRoles.PACHURI, 0);
        Harpymodloader.setRoleMaximum(RedHouseRoles.FURANDORU, 0);
        Harpymodloader.setRoleMaximum(RedHouseRoles.REMILIA, 0);
        Harpymodloader.setRoleMaximum(ModRoles.EXAMPLER, 0);
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
            autoRoleMaxCount(serverLevel, gameWorldComponent, players);
            autoModifierMaxCount(serverLevel, gameWorldComponent, players);

            autoChangePresent();

            // 获取当前地图ID
            String currentMap = "unknown";
            if (serverLevel.getServer() != null) {
                var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(serverLevel);
                if (areas != null && areas.mapName != null) {
                    currentMap = areas.mapName;
                }
            }
            final int players_count = serverLevel.getServer().getPlayerCount();
            initModifiersCount(players_count);

            // 彩蛋角色/修饰符数量
            if (players_count >= NoellesRolesConfig.instance().minPlayerForEggRoles
                    && random.nextInt(0, 100) <= EGGS_CHANCE) {
                isEggEnabled = true;
                Harpymodloader.setRoleMaximum(ModRoles.DIO, 1);
                for (var a : TMMRoles.ROLES.values()) {
                    if (a instanceof EggRole) {
                        int max = a.getRoundMaxCount(serverLevel, gameWorldComponent, players, currentMap);
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
                Harpymodloader.setRoleMaximum(ModRoles.DIO, 0);
                isEggEnabled = false;

                for (var a : HMLModifiers.MODIFIERS) {
                    if (a instanceof EggModifier) {
                        Harpymodloader.MODIFIER_MAX.put(a.identifier(), 0);
                    }
                }
                for (var a : TMMRoles.ROLES.values()) {
                    if (a instanceof EggRole) {
                        Harpymodloader.setRoleMaximum(a, 0);
                    }
                }
            }

            {
                // 杀手中立（只处理没有配置的职业：无概率 且 无显式 setMax）
                var neutralRoles = new ArrayList<SRERole>(TMMRoles.ROLES.values());
                neutralRoles.removeIf((r) -> {
                    if (r.isNeutrals() && r.isNeutralForKiller() && (r.spawnInfo.enableChance < 0)
                            && r.defaultMaxCount <= 0)
                        return false;
                    return true;
                });
                Collections.shuffle(neutralRoles);
                for (var r : neutralRoles) {
                    Harpymodloader.setRoleMaximum(r, 0);
                }
                int neutralForKillers = 0;
                neutralForKillers = players_count / 6;
                // 减去已有配置的职业数，避免超额分配
                neutralForKillers -= (int) TMMRoles.ROLES.values().stream()
                        .filter(r -> r.isNeutrals() && r.isNeutralForKiller()
                                && (r.spawnInfo.enableChance >= 0 || r.defaultMaxCount > 0))
                        .count();
                neutralForKillers = Math.max(0, neutralForKillers);
                for (int i = 0; i < neutralForKillers && i < neutralRoles.size(); i++) {
                    Harpymodloader.setRoleMaximum(neutralRoles.get(i), 1);
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
                    if (a instanceof TouhouRole) {
                        int max = a.getRoundMaxCount(serverLevel, gameWorldComponent, players, currentMap);
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
                if (Harpymodloader.ROLE_MAX.getOrDefault(ModRoles.DIO, 0) > 0) {
                    Harpymodloader.setRoleMaximum(RedHouseRoles.MAID_SAKUYA, 1);
                }
                Harpymodloader.setRoleMaximum(RedHouseRoles.BAKA_ID, 1);
                Harpymodloader.setRoleMaximum(RedHouseRoles.PACHURI, 1);
                Harpymodloader.setRoleMaximum(RedHouseRoles.REMILIA, 1);
                Harpymodloader.setRoleMaximum(RedHouseRoles.FURANDORU, 1);
                Harpymodloader.setRoleMaximum(RedHouseRoles.HOAN_MEIRIN, 1);
            } else {
                isTouhouEnabled = false;
                for (var a : TMMRoles.ROLES.values()) {
                    if (a instanceof TouhouRole) {
                        Harpymodloader.setRoleMaximum(a, 0);
                    }
                }
                for (var a : HMLModifiers.MODIFIERS) {
                    if (a instanceof TouhouModifier) {
                        Harpymodloader.setModifierMaximum(a, 0);
                    }
                }
            }

            applySpecialMapRoles(serverLevel, currentMap, config);
            applySpecialVigilanteRoles(serverLevel, players_count, config, random, currentMap);
        });
    }

    private static void applySpecialMapRoles(ServerLevel serverLevel, String currentMap, NoellesRolesConfig config) {
        for (var role : TMMRoles.ROLES.values()) {
            if (!role.isSpecialMapRole()) {
                continue;
            }
            if (isSpecialMapRoleEnabled(serverLevel, role, currentMap, config)) {
                Harpymodloader.setRoleMaximum(role, Math.max(0, role.spawnInfo.maxSpawn));
            } else {
                Harpymodloader.setRoleMaximum(role, 0);
            }
        }
    }

    private static void applySpecialVigilanteRoles(ServerLevel serverLevel, int playersCount,
            NoellesRolesConfig config, Random random,
            String currentMap) {
        int limit = getSpecialVigilanteLimit(playersCount, config);
        ArrayList<SRERole> specialVigilantes = new ArrayList<>();
        var roleMaxBackup = new HashMap<>(Harpymodloader.ROLE_MAX);
        for (var role : TMMRoles.ROLES.values()) {
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
            if (!isSpecialMapRoleEnabled(serverLevel, role, currentMap, config)) {
                continue;
            }
            int chance = role.spawnInfo.enableChance;
            if (chance >= 0 && random.nextInt(0, 10000) < chance) {
                selected.add(role);
                if (role.canRefreshableSpecialVigilante()) {
                    int secondChance = role.getRefreshableSpecialVigilanteChance();
                    if (secondChance >= 0 && random.nextInt(0, 10000) < secondChance) {
                        selected.add(role);
                    }
                }
            } else {
                // 如果chance为-1则表明默认值
                selected.add(role);
                if (role.canRefreshableSpecialVigilante()) {
                    int secondChance = role.getRefreshableSpecialVigilanteChance();
                    if (secondChance >= 0 && random.nextInt(0, 10000) < secondChance) {
                        selected.add(role);
                    }
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

    private static boolean isSpecialMapRoleEnabled(ServerLevel serverLevel, SRERole role, String currentMap,
            NoellesRolesConfig config) {
        return switch (role.getSpecialMapRole()) {
            case ALL -> true;
            case QIYUCUN -> config.maChenXuMaps.contains(currentMap);
            case BIGMAP -> config.swastMaps.contains(currentMap);
            case UNDERWATER -> config.underwaterRolesMaps.contains(currentMap);
            case FLY -> config.airRolesMaps.contains(currentMap);
            case TRAP -> config.trapRolesMaps.contains(currentMap);
            case CAN_JUMP -> {
                var areas = AreasWorldComponent.KEY.get(serverLevel);
                yield areas != null && areas.areasSettings.canJump;
            }
            case MEETING -> {
                var areas = AreasWorldComponent.KEY.get(serverLevel);
                yield areas != null && areas.areasSettings.meetingEnabled;
            }
            case MEETING_VOTE -> {
                var areas = AreasWorldComponent.KEY.get(serverLevel);
                yield areas != null && areas.areasSettings.meetingEnabled
                        && areas.areasSettings.meetingVoteEnabled;
            }
            case MINIGAME_QUEST -> {
                var areas = AreasWorldComponent.KEY.get(serverLevel);
                yield areas != null && areas.areasSettings.minigameQuestEnabled;
            }
            case MAP_STATUS_BAR -> {
                var areas = AreasWorldComponent.KEY.get(serverLevel);
                yield areas != null && areas.areasSettings.mapStatusBar != MapStatusBarType.NONE;
            }
            case HORSE -> config.horseRolesMaps.contains(currentMap);
        };
    }

    private static void autoRoleMaxCount(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        var areacca = AreasWorldComponent.KEY.get(serverLevel);
        var mapName = areacca.mapName;
        for (var entry : TMMRoles.ROLES.entrySet()) {
            if (entry.getValue() instanceof TouhouRole)
                continue;
            if (entry.getValue() instanceof EggRole)
                continue;
            ResourceLocation name = entry.getKey();
            SRERole role = entry.getValue();
            int count = role.getRoundMaxCount(serverLevel, gameWorldComponent, players, mapName);
            if (count >= 0) {
                Harpymodloader.setRoleMaximum(name, count);
            }
        }
    }

    private static void autoModifierMaxCount(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
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
            if (role.spawnInfo.enableChance < 0 && role.isNeutralForKiller()) {
                role.setDefaultMax(1);
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
