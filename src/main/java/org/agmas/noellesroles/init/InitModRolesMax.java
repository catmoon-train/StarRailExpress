package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.SREConfig.AutoPresetInfo;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentManager;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.RedHouseRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class InitModRolesMax {
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
            ;
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

        // 滑头鬼每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.SLIPPERY_GHOST_ID, 1);

        // 不应该刷新
        Harpymodloader.setRoleMaximum(SpecialGameModeRoles.CUSTOM_PENDING, 0);

        // 工程师每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.ENGINEER_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.BASEBALL_PLAYER_ID, 0);
        Harpymodloader.setRoleMaximum(ModRoles.LOCKSMITH_ID, 0);
        Harpymodloader.setRoleMaximum(ModRoles.MA_CHEN_XU, 0);
        Harpymodloader.setRoleMaximum(ModRoles.GUEST_GHOST, 0);

        Harpymodloader.setOccupationRole(ModRoles.ENGINEER, ModRoles.LOCKSMITH);
        Harpymodloader.setOccupationRole(RedHouseRoles.FURANDORU, RedHouseRoles.PACHURI);
        Harpymodloader.setOccupationRole(ModRoles.MA_CHEN_XU, ModRoles.GUEST_GHOST);
        Harpymodloader.setOccupationRole(ModRoles.GANGSTERS, ModRoles.FITTER);

        // 拳击手每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.BOXER_ID, 1);

        // 小偷每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.THIEF_ID, 1);

        // 秉烛人每局最多 1 个（具体是否出现由动态规则控制）
        Harpymodloader.setRoleMaximum(ModRoles.CANDLE_BEARER_ID, 1);

        // 鹈鹕每局最多 1 个（具体是否出现由动态概率控制）
        Harpymodloader.setRoleMaximum(ModRoles.PELICAN_ID, 1);

        // 邮差每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.POSTMAN_ID, 1);

        // 私家侦探每局只能有 1 个
        Harpymodloader.setRoleMaximum(ModRoles.DETECTIVE_ID, 1);

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
        Harpymodloader.setRoleMaximum(ModRoles.ELF_ID, 1);

        // 巡警
        Harpymodloader.setRoleMaximum(ModRoles.PATROLLER_ID, 1);

        // 特警（默认为0，在动态设置中根据地图判断）
        Harpymodloader.setRoleMaximum(ModRoles.SWAST_ID, 0);

        // 武术教官（默认为0，在动态设置中根据警卫数量判断）
        Harpymodloader.setRoleMaximum(ModRoles.MARTIAL_ARTS_INSTRUCTOR_ID, 0);

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
        Harpymodloader.setRoleMaximum(ModRoles.BEST_VIGILANTE_ID, 0); // 默认为0，在 InitModRolesMax 中动态设置

        // 红海军 - 设置为0（不会自然生成，只能通过远征队修饰符获得）
        Harpymodloader.setRoleMaximum(ModRoles.BETTER_VIGILANTE_ID, 0);

        // 作家 - 默认为0，在 InitModRolesMax 中动态设置（0.5%概率刷新）
        Harpymodloader.setRoleMaximum(ModRoles.WRITER_ID, 0);

        // 电报员 - 默认为0，在 InitModRolesMax 中动态设置（0.5%概率刷新）
        Harpymodloader.setRoleMaximum(ModRoles.TELEGRAPHER_ID, 0);

        // 设置角色最大数量
        Harpymodloader.setRoleMaximum(ModRoles.POISONER_ID, 0);
        // 和医生一起生成
        Harpymodloader.setRoleMaximum(ModRoles.DOCTOR_ID, 0);
        Harpymodloader.setRoleMaximum(ModRoles.ATTENDANT_ID, 1);
        Harpymodloader.setRoleMaximum(ModRoles.CORONER_ID, 1);

        // 同时出现
        RoleAssignmentManager.addOccupationRole(ModRoles.POISONER, ModRoles.DOCTOR);
        RoleAssignmentManager.addOccupationRole(ModRoles.INFECTED, ModRoles.DOCTOR);
        RoleAssignmentManager.addOccupationRole(RedHouseRoles.BAKA, ModRoles.EXAMPLER);
        RoleAssignmentManager.addOccupationRole(ModRoles.DIO, ModRoles.JOJO);
        RoleAssignmentManager.addOccupationRole(ModRoles.WATER_GHOST, ModRoles.DIVER);

        Harpymodloader.setRoleMaximum(ModRoles.CONDUCTOR_ID, NoellesRolesConfig.HANDLER.instance().conductorMax);
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
        Harpymodloader.setRoleMaximum(ModRoles.EXECUTIONER_ID, NoellesRolesConfig.HANDLER.instance().executionerMax);
        Harpymodloader.setRoleMaximum(ModRoles.VULTURE_ID, NoellesRolesConfig.HANDLER.instance().vultureMax);
        Harpymodloader.setRoleMaximum(ModRoles.JESTER_ID, NoellesRolesConfig.HANDLER.instance().jesterMax);
        Harpymodloader.setRoleMaximum(ModRoles.MORPHLING_ID, NoellesRolesConfig.HANDLER.instance().morphlingMax);
        Harpymodloader.setRoleMaximum(ModRoles.SILENCER_ID, NoellesRolesConfig.HANDLER.instance().silencerMax);
        Harpymodloader.setRoleMaximum(ModRoles.BARTENDER_ID, NoellesRolesConfig.HANDLER.instance().bartenderMax);
        Harpymodloader.setRoleMaximum(ModRoles.NOISEMAKER_ID, NoellesRolesConfig.HANDLER.instance().noisemakerMax);
        Harpymodloader.setRoleMaximum(ModRoles.PHANTOM_ID, NoellesRolesConfig.HANDLER.instance().phantomMax);
        Harpymodloader.setRoleMaximum(ModRoles.AWESOME_BINGLUS_ID,
                NoellesRolesConfig.HANDLER.instance().awesomeBinglusMax);
        Harpymodloader.setRoleMaximum(ModRoles.SWAPPER_ID, NoellesRolesConfig.HANDLER.instance().swapperMax);
        Harpymodloader.setRoleMaximum(ModRoles.VOODOO_ID, NoellesRolesConfig.HANDLER.instance().voodooMax);
        Harpymodloader.setRoleMaximum(ModRoles.CORONER_ID, NoellesRolesConfig.HANDLER.instance().coronerMax);
        Harpymodloader.setRoleMaximum(ModRoles.RECALLER_ID, NoellesRolesConfig.HANDLER.instance().recallerMax);
        Harpymodloader.setRoleMaximum(ModRoles.BROADCASTER_ID, NoellesRolesConfig.HANDLER.instance().broadcasterMax);
        Harpymodloader.setRoleMaximum(ModRoles.GAMBLER_ID, NoellesRolesConfig.HANDLER.instance().gamblerMax);
        Harpymodloader.setRoleMaximum(ModRoles.GLITCH_ROBOT_ID, NoellesRolesConfig.HANDLER.instance().glitchRobotMax);
        Harpymodloader.setRoleMaximum(ModRoles.GHOST_ID, NoellesRolesConfig.HANDLER.instance().ghostMax);
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

        Harpymodloader.setRoleMaximum(ModRoles.DIVER_ID, 0);
        Harpymodloader.setRoleMaximum(ModRoles.WATER_GHOST_ID, 0);
        Harpymodloader.setRoleMaximum(ModRoles.SEA_KING_ID, 0);

        // 叛徒设置为0
        Harpymodloader.setRoleMaximum(TraitorAndModifiers.TRAITOR_ID, 0);

        // 飞行员和影隼初始为0
        Harpymodloader.setRoleMaximum(ModRoles.PILOT_ID, 0);
        Harpymodloader.setRoleMaximum(ModRoles.SHADOW_FALCON_ID, 0);

        // 设置飞行员和影隼绑定生成
        RoleAssignmentManager.addOccupationRole(ModRoles.SHADOW_FALCON, ModRoles.PILOT);
    }

    public static void registerDynamic() {
        GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
            if (!Harpymodloader.isMojangVerify) {
                return;
            }
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

            // 获取地图是否可跳跃
            boolean canJumpMap = false;
            var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(serverLevel);
            if (areas != null) {
                canJumpMap = areas.canJump;
            }

            {
                NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
                var killerRoleCount = (int) Math.floor((float) players_count / (float) 6);

                if (killerRoleCount > 1) {
                    Harpymodloader.setRoleMaximum(SERoles.NECROMANCER.getIdentifier(),
                            players_count >= config.minPlayerForNecromancer ? 1 : 0);
                    Harpymodloader.setRoleMaximum(SERoles.AVARICIOUS.getIdentifier(),
                            players_count >= config.minPlayerForAvaricious ? 1 : 0);
                    Harpymodloader.setRoleMaximum(SERoles.INITIATE.getIdentifier(),
                            players_count >= config.minPlayerForInitiate ? 1 : 0);
                } else {
                    Harpymodloader.setRoleMaximum(SERoles.NECROMANCER.getIdentifier(), 0);
                    Harpymodloader.setRoleMaximum(SERoles.AVARICIOUS.getIdentifier(), 0);
                    Harpymodloader.setRoleMaximum(SERoles.INITIATE.getIdentifier(), 0);
                }
            }
            {
                // 杀手中立（只处理没有配置的职业：无概率 且 无显式 setMax）
                var neutralRoles = new ArrayList<SRERole>(TMMRoles.ROLES.values());
                neutralRoles.removeIf((r) -> {
                    if (r.isNeutrals() && r.isNeutralForKiller() && r.enableChance < 0 && r.maxCount <= 0)
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
                                && (r.enableChance >= 0 || r.maxCount > 0))
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

            if (players_count >= config.minPlayerForTouhouRoles && random.nextInt(0, 100) < TOUHOU_CHANCE) {
                Harpymodloader.setRoleMaximum(RedHouseRoles.BAKA_ID, 1);
                Harpymodloader.setRoleMaximum(RedHouseRoles.PACHURI, 1);
                Harpymodloader.setRoleMaximum(RedHouseRoles.REMILIA, 1);
                Harpymodloader.setRoleMaximum(RedHouseRoles.FURANDORU, 1);
                if (canJumpMap) {
                    Harpymodloader.setRoleMaximum(RedHouseRoles.HOAN_MEIRIN, 1);
                } else {
                    Harpymodloader.setRoleMaximum(RedHouseRoles.HOAN_MEIRIN, 0);
                }
            } else {
                Harpymodloader.setRoleMaximum(RedHouseRoles.BAKA_ID, 0);
                Harpymodloader.setRoleMaximum(RedHouseRoles.HOAN_MEIRIN, 0);
                Harpymodloader.setRoleMaximum(RedHouseRoles.PACHURI, 0);
                Harpymodloader.setRoleMaximum(RedHouseRoles.REMILIA, 0);
                Harpymodloader.setRoleMaximum(RedHouseRoles.FURANDORU, 0);
            }

            // 红尘客 - 从配置读取概率 (100 - 配置值 = 不刷新的概率)
            if (players_count >= config.minPlayerForWayfarer
                    && random.nextInt(0, 100) >= (100 - config.chanceOfWayfarer)) {
                Harpymodloader.setRoleMaximum(ModRoles.WAYFARER_ID, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.WAYFARER_ID, 0);
            }

            // 毒师 - 从配置读取概率
            if (players_count >= config.minPlayerForPoisoner && random.nextInt(0, 100) < config.chanceOfPoisoner) {
                Harpymodloader.setRoleMaximum(ModRoles.POISONER_ID, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.POISONER_ID, 0);
            }

            if (players_count >= config.minPlayerForEggRoles && random.nextInt(0, 100) <= EGGS_CHANCE) {
                Harpymodloader.setRoleMaximum(ModRoles.DIO, 1);
                Harpymodloader.setRoleMaximum(RedHouseRoles.MAID_SAKUYA, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.DIO, 0);
                Harpymodloader.setRoleMaximum(RedHouseRoles.MAID_SAKUYA, 0);
            }

            // 魔术师 - 从配置读取概率
            if (players_count >= config.minPlayerForMagician && random.nextInt(0, 100) <= config.chanceOfMagician) {
                Harpymodloader.setRoleMaximum(ModRoles.MAGICIAN_ID, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.MAGICIAN_ID, 0);
            }

            // 迷失杀手 - 从配置读取概率
            if (random.nextInt(0, 100) <= config.chanceOfLostKiller) {
                Harpymodloader.setRoleMaximum(ModRoles.LOST_KILLER_ID, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.LOST_KILLER_ID, 0);
            }

            // 死灵法师数量 - 从配置读取最小玩家数和概率
            if (players_count >= config.minPlayerForNecromancer
                    && random.nextInt(0, 100) <= config.chanceOfNecromancer) {
                Harpymodloader.setRoleMaximum(SERoles.NECROMANCER, 1);
            } else {
                Harpymodloader.setRoleMaximum(SERoles.NECROMANCER, 0);
            }

            // 监察员 - 从配置读取概率
            if (random.nextInt(0, 100) <= config.chanceOfMonitor) {
                Harpymodloader.setRoleMaximum(ModRoles.MONITOR_ID, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.MONITOR_ID, 0);
            }

            // 年兽 - 从配置读取概率
            if (random.nextInt(0, 100) < config.chanceOfNianShou) {
                Harpymodloader.setRoleMaximum(ModRoles.NIAN_SHOU_ID, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.NIAN_SHOU_ID, 0);
            }
            if (players_count >= config.minPlayerForRecorder) {
                Harpymodloader.setRoleMaximum(ModRoles.RECORDER, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.RECORDER, 0);
            }
            // 秃鹫数量
            if (players_count >= config.minPlayerForVulture) {
                Harpymodloader.setRoleMaximum(ModRoles.VULTURE, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.VULTURE, 0);
            }
            // 纵火犯数量 - 从配置读取最小玩家数
            if (players_count >= config.minPlayerForArsonist) {
                Harpymodloader.setRoleMaximum(SERoles.ARSONIST, 1);
            } else {
                Harpymodloader.setRoleMaximum(SERoles.ARSONIST, 0);
            }

            // 秉烛人数量 - 从配置读取最小玩家数
            if (players_count >= config.minPlayerForCandleBearer) {
                Harpymodloader.setRoleMaximum(ModRoles.CANDLE_BEARER_ID, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.CANDLE_BEARER_ID, 0);
            }

            // 钟表匠数量 - 从配置读取最小玩家数
            if (players_count >= config.minPlayerForClockmaker) {
                Harpymodloader.setRoleMaximum(ModRoles.CLOCKMAKER_ID, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.CLOCKMAKER_ID, 0);
            }

            // 仇杀客数量 - 从配置读取最小玩家数
            if (players_count >= config.minPlayerForBloodFeudist) {
                Harpymodloader.setRoleMaximum(ModRoles.BLOOD_FEUDIST_ID, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.BLOOD_FEUDIST_ID, 0);
            }
            // machenxu
            {
                boolean isMachenxuMap = false;
                var machenxuMap = new ArrayList<>(NoellesRolesConfig.HANDLER.instance().maChenXuMaps);
                if (machenxuMap != null && machenxuMap.size() > 0) {
                    isMachenxuMap = machenxuMap.contains(currentMap);
                }
                if (isMachenxuMap) {
                    Harpymodloader.setRoleMaximum(ModRoles.MA_CHEN_XU, 1);
                } else {
                    Harpymodloader.setRoleMaximum(ModRoles.MA_CHEN_XU, 0);
                }
            }
            // 特殊警卫刷新逻辑 - 从配置读取最小玩家数
            {
                int allSpecialPoliceCount = 0;

                if (players_count >= config.minPlayerForSpecialPolice5) {
                    allSpecialPoliceCount = 5;
                } else if (players_count >= config.minPlayerForSpecialPolice4) {
                    allSpecialPoliceCount = 4;
                } else if (players_count >= config.minPlayerForSpecialPolice3) {
                    allSpecialPoliceCount = 3;
                } else if (players_count >= config.minPlayerForSpecialPolice2) {
                    allSpecialPoliceCount = 2;
                } else if (players_count >= config.minPlayerForSpecialPolice1) {
                    allSpecialPoliceCount = 1;
                } else {
                    allSpecialPoliceCount = 0;
                }

                // 基础角色：巡警、武术教官、游侠各有概率生成（从配置读取）
                int PATROLLER_COUNT = 0;
                int MARTIAL_ARTS_INSTRUCTOR_COUNT = 0;
                int ELF_COUNT = 0;

                // 巡警：从配置读取概率，其中一定概率生成两个
                if (random.nextInt(0, 100) < config.chanceOfPatroller) {
                    PATROLLER_COUNT = 1;
                    if (random.nextInt(0, 100) < config.chanceOfDoublePatroller) {
                        PATROLLER_COUNT = 2;
                    }
                }

                // 武术教官：从配置读取概率
                if (random.nextInt(0, 100) < config.chanceOfMartialArtsInstructor) {
                    MARTIAL_ARTS_INSTRUCTOR_COUNT = 1;
                }

                // 游侠：从配置读取概率，其中一定概率生成两个
                if (random.nextInt(0, 100) < config.chanceOfElf) {
                    ELF_COUNT = 1;
                    if (random.nextInt(0, 100) < config.chanceOfDoubleElf) {
                        ELF_COUNT = 2;
                    }
                }

                // 根据allSpecialPoliceCount限制总数
                int currentTotal = PATROLLER_COUNT + MARTIAL_ARTS_INSTRUCTOR_COUNT + ELF_COUNT;
                while (currentTotal > allSpecialPoliceCount) {
                    // 随机减少一个角色
                    int reduceTarget = random.nextInt(3);
                    if (reduceTarget == 0 && PATROLLER_COUNT > 0) {
                        PATROLLER_COUNT--;
                    } else if (reduceTarget == 1 && MARTIAL_ARTS_INSTRUCTOR_COUNT > 0) {
                        MARTIAL_ARTS_INSTRUCTOR_COUNT--;
                    } else if (reduceTarget == 2 && ELF_COUNT > 0) {
                        ELF_COUNT--;
                    }
                    currentTotal = PATROLLER_COUNT + MARTIAL_ARTS_INSTRUCTOR_COUNT + ELF_COUNT;
                }

                // 特警和更好的义警初始为0
                int SWAST_COUNT = 0;
                int BEST_VIGILANTE_COUNT = 0;

                // 判断是否为特警可用地图 (areas1, areas3, areas4, areas7, areas10)
                boolean isSwastMap = false;
                var swastMaps = new ArrayList<>(NoellesRolesConfig.HANDLER.instance().swastMaps);
                if (swastMaps != null && swastMaps.size() > 0) {
                    isSwastMap = swastMaps.contains(currentMap);
                }

                // 如果是特警可用地图且有可用警卫位置，从配置读取概率随机替换一个为特警
                if (isSwastMap && currentTotal > 0 && currentTotal >= allSpecialPoliceCount - 1
                        && random.nextInt(0, 100) < config.chanceOfSwast) {
                    SWAST_COUNT = 1;
                    // 随机选择替换的角色
                    int replaceTarget = random.nextInt(3);
                    if (replaceTarget == 0 && PATROLLER_COUNT > 0) {
                        PATROLLER_COUNT--;
                    } else if (replaceTarget == 1 && MARTIAL_ARTS_INSTRUCTOR_COUNT > 0) {
                        MARTIAL_ARTS_INSTRUCTOR_COUNT--;
                    } else if (replaceTarget == 2 && ELF_COUNT > 0) {
                        ELF_COUNT--;
                    } else if (PATROLLER_COUNT > 0) {
                        PATROLLER_COUNT--;
                    } else if (MARTIAL_ARTS_INSTRUCTOR_COUNT > 0) {
                        MARTIAL_ARTS_INSTRUCTOR_COUNT--;
                    } else if (ELF_COUNT > 0) {
                        ELF_COUNT--;
                    }
                }

                // 更好的义警符合条件时，从配置读取概率（基于10000），随机替换一个为更好的义警
                int totalRoles = PATROLLER_COUNT + MARTIAL_ARTS_INSTRUCTOR_COUNT + ELF_COUNT + SWAST_COUNT;
                if (random.nextInt(0, 10000) < config.chanceOfBestVigilante && totalRoles > 0) {
                    BEST_VIGILANTE_COUNT = 1;
                    // 随机选择替换的角色
                    int replaceTarget = random.nextInt(4);
                    if (replaceTarget == 0 && PATROLLER_COUNT > 0) {
                        PATROLLER_COUNT--;
                    } else if (replaceTarget == 1 && MARTIAL_ARTS_INSTRUCTOR_COUNT > 0) {
                        MARTIAL_ARTS_INSTRUCTOR_COUNT--;
                    } else if (replaceTarget == 2 && ELF_COUNT > 0) {
                        ELF_COUNT--;
                    } else if (replaceTarget == 3 && SWAST_COUNT > 0) {
                        SWAST_COUNT--;
                    } else if (PATROLLER_COUNT > 0) {
                        PATROLLER_COUNT--;
                    } else if (MARTIAL_ARTS_INSTRUCTOR_COUNT > 0) {
                        MARTIAL_ARTS_INSTRUCTOR_COUNT--;
                    } else if (ELF_COUNT > 0) {
                        ELF_COUNT--;
                    } else if (SWAST_COUNT > 0) {
                        SWAST_COUNT--;
                    }
                }

                Harpymodloader.setRoleMaximum(ModRoles.PATROLLER, PATROLLER_COUNT);
                Harpymodloader.setRoleMaximum(ModRoles.ELF, ELF_COUNT);
                Harpymodloader.setRoleMaximum(ModRoles.SWAST_ID, SWAST_COUNT);
                Harpymodloader.setRoleMaximum(ModRoles.MARTIAL_ARTS_INSTRUCTOR_ID, MARTIAL_ARTS_INSTRUCTOR_COUNT);
                Harpymodloader.setRoleMaximum(ModRoles.BEST_VIGILANTE_ID, BEST_VIGILANTE_COUNT);

                if (allSpecialPoliceCount == 0) {
                    Harpymodloader.setRoleMaximum(ModRoles.PATROLLER, 0);
                    Harpymodloader.setRoleMaximum(ModRoles.ELF, 0);
                    Harpymodloader.setRoleMaximum(ModRoles.SWAST_ID, 0);
                    Harpymodloader.setRoleMaximum(ModRoles.MARTIAL_ARTS_INSTRUCTOR_ID, 0);
                    Harpymodloader.setRoleMaximum(ModRoles.BEST_VIGILANTE_ID, 0);
                }
            }

            // 水下角色（海王、潜水员、水鬼）- 仅在水下地图必定生成
            {
                boolean isUnderwaterMap = false;
                var underwaterMaps = new ArrayList<>(NoellesRolesConfig.HANDLER.instance().underwaterRolesMaps);
                if (underwaterMaps != null && underwaterMaps.size() > 0) {
                    isUnderwaterMap = underwaterMaps.contains(currentMap);
                }
                if (isUnderwaterMap) {
                    Harpymodloader.setRoleMaximum(ModRoles.WATER_GHOST_ID, 1);
                    Harpymodloader.setRoleMaximum(ModRoles.SEA_KING_ID, 1);
                } else {
                    Harpymodloader.setRoleMaximum(ModRoles.SEA_KING_ID, 0);
                    Harpymodloader.setRoleMaximum(ModRoles.WATER_GHOST_ID, 0);
                }
            }

            // 飞行员和影隼（空港角色）- 仅在空港地图必定生成
            {
                boolean isKonggangMap = false;
                var konggangMaps = new ArrayList<>(NoellesRolesConfig.HANDLER.instance().konggangMaps);
                if (konggangMaps != null && konggangMaps.size() > 0) {
                    isKonggangMap = konggangMaps.contains(currentMap);
                }
                if (isKonggangMap) {
                    Harpymodloader.setRoleMaximum(ModRoles.PILOT_ID, 0);
                    Harpymodloader.setRoleMaximum(ModRoles.SHADOW_FALCON_ID, 1);
                } else {
                    Harpymodloader.setRoleMaximum(ModRoles.PILOT_ID, 0);
                    Harpymodloader.setRoleMaximum(ModRoles.SHADOW_FALCON_ID, 0);
                }
            }
            // 布谷鸟地图限制 - 默认在所有地图刷新，只有cuckooMaps中配置了地图时才仅在特定地图刷新
            {
                var cuckooMaps = new ArrayList<>(NoellesRolesConfig.HANDLER.instance().cuckooMaps);
                if (cuckooMaps != null && cuckooMaps.size() > 0) {
                    if (!cuckooMaps.contains(currentMap)) {
                        Harpymodloader.setRoleMaximum(ModRoles.CUCKOO_ID, 0);
                    }
                }
            }
            // WRITER (作家) - 从配置读取概率
            if (random.nextInt(0, 100) <= config.chanceOfWriter) {
                Harpymodloader.setRoleMaximum(ModRoles.WRITER_ID, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.WRITER_ID, 0);
            }

            // 棒球员 - 从配置读取概率
            if (random.nextInt(0, 100) <= config.chanceOfBaseballPlayer) {
                Harpymodloader.setRoleMaximum(ModRoles.BASEBALL_PLAYER_ID, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.BASEBALL_PLAYER_ID, 0);
            }

            // TELEGRAPHER (电报员) - 从配置读取概率
            if (random.nextInt(0, 100) <= config.chanceOfTelegrapher) {
                Harpymodloader.setRoleMaximum(ModRoles.TELEGRAPHER_ID, 1);
            } else {
                Harpymodloader.setRoleMaximum(ModRoles.TELEGRAPHER_ID, 0);
            }
        });
    }

    private static void autoRoleMaxCount(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        for (var roleInfo : TMMRoles.ROLES.entrySet()) {
            ResourceLocation name = roleInfo.getKey();
            SRERole role = roleInfo.getValue();
            int count = role.getRoundMaxCount(serverLevel, gameWorldComponent, players);
            if (count >= 0) {
                Harpymodloader.setRoleMaximum(name, count);
            }
        }
    }

    private static void autoModifierMaxCount(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        for (SREModifier modifier : HMLModifiers.MODIFIERS) {
            int count = modifier.getRoundMaxCount(serverLevel, gameWorldComponent, players);
            if (count >= 0) {
                Harpymodloader.MODIFIER_MAX.put(modifier.identifier(), count);
            }
        }
    }

    /**
     * 从配置应用角色概率设置
     */
    private static void applyRoleChanceFromConfig() {
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();

        // 建筑师 - 从配置读取概率和最小玩家数
        ModRoles.BUILDER.setEnableChance(config.chanceOfBuilder).setEnableNeededPlayerCount(config.minPlayerForBuilder);

        // 杜鹃 - 45%概率
        ModRoles.CUCKOO.setEnableChance(config.chanceOfCuckoo);

        // 苦力怕 - 20%概率
        ModRoles.CREEPER.setEnableChance(config.chanceOfCreeper);

        // 画家 - 50%概率
        ModRoles.PAINTER.setEnableChance(config.chanceOfPainter);

        // 雇佣兵 - 从配置读取概率和最小玩家数
        ModRoles.MERCENARY.setEnableChance(config.chanceOfMercenary)
                .setEnableNeededPlayerCount(config.minPlayerForMercenary);

        // 愚者 - 从配置读取概率和最小玩家数
        ModRoles.THE_FOOL.setEnableChance(config.chanceOfTheFool)
                .setEnableNeededPlayerCount(config.minPlayerForTheFool);

        // 猫死灵法师 - 从配置读取概率和最小玩家数
        ModRoles.CAT_NECROMANCER.setEnableChance(config.chanceOfCatNecromancer)
                .setEnableNeededPlayerCount(config.minPlayerForCatNecromancer);

        // 更好的义警 - 小概率（基于10000）
        ModRoles.BEST_VIGILANTE.setEnableRareChance(config.chanceOfBestVigilante);

        // 静语者 - 从配置读取概率和最大数量
        ModRoles.SILENCER.setEnableChance(config.chanceOfSilencer).setMax(config.silencerMax);

        // StupidExpress 角色配置
        // 失忆者
        SERoles.AMNESIAC.setEnableNeededPlayerCount(config.minPlayerForAmnesiac)
                .setEnableChance(config.chanceOfAmnesiac);

        // 悍匪 - 75%概率
        ModRoles.GANGSTERS.setEnableChance(75);
        // 钳工 - 与悍匪绑定，由悍匪概率控制
        ModRoles.FITTER.setEnableChance(75);

        // 鹈鹕 - 从配置读取概率和最小玩家数
        ModRoles.PELICAN.setEnableChance(config.chanceOfPelican)
                .setEnableNeededPlayerCount(config.minPlayerForPelican);

        // 教父 - 从配置读取概率和最小玩家数
        ModRoles.GODFATHER.setEnableChance(config.chanceOfGodfather)
                .setEnableNeededPlayerCount(config.mafiaMinimumPlayers);

        // 对没有 enableChance 的杀手方中立职业，默认 max=1、概率 75%
        for (var entry : TMMRoles.ROLES.entrySet()) {
            var role = entry.getValue();
            if (role.enableChance < 0 && role.isNeutralForKiller()) {
                role.setMax(1);
                role.setEnableChance(75);
            }
        }
    }

    public static void initModifiersCount(int players) {
        Random random = new Random();
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        // LOVERS
        REFUGEE_CHANCE = config.chanceOfModifierRefugee;
        if (REFUGEE_CHANCE < 0) {
            REFUGEE_CHANCE = 0;
        }
        EGGS_CHANCE = config.chanceOfEggRoles;
        if (EGGS_CHANCE < 0) {
            EGGS_CHANCE = 0;
        }
        TOUHOU_CHANCE = config.chanceOfTouhouRoles;
        /// REFUGEE
        if (players >= config.minPlayerForRefugee && random.nextInt(0, 100) <= REFUGEE_CHANCE) {
            StupidExpress.LOGGER.info("Modifier [Refugee] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(SEModifiers.REFUGEE.identifier(), 1);
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("refugee"), 0);
        }

        /// TINY
        StupidExpress.LOGGER.info("Modifier [Tiny] enabled in this round!");
        Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("tiny"), players / random.nextInt(4, 18));

        /// TALL
        StupidExpress.LOGGER.info("Modifier [Tall] enabled in this round!");
        Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("tall"), players / random.nextInt(4, 18));

        /// FEATHER
        StupidExpress.LOGGER.info("Modifier [Feather] enabled in this round!");
        Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("feather"), players / random.nextInt(8, 32));

        /// MAGNATE
        if (random.nextInt(0, 100) < config.chanceOfMagnate) {
            StupidExpress.LOGGER.info("Modifier [Magnate] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("magnate"), 2);
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("magnate"), 0);
        }

        /// TASKMASTER
        if (random.nextInt(0, 100) < config.chanceOfTaskmaster) {
            StupidExpress.LOGGER.info("Modifier [Taskmaster] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("taskmaster"), players / random.nextInt(8, 24));
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("taskmaster"), 0);
        }

        /// ALLERGIST
        if (random.nextInt(0, 100) < config.chanceOfAllergist) {
            StupidExpress.LOGGER.info("Modifier [Allergist] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("allergist"), 1);
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("allergist"), 0);
        }

        /// CURSED
        if (players >= config.minPlayerForCursed && random.nextInt(0, 100) < config.chanceOfCursed) {
            StupidExpress.LOGGER.info("Modifier [Cursed] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("cursed"), 1);
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("cursed"), 0);
        }

        /// SECRETIVE
        if (players >= config.minPlayerForSecretive && random.nextInt(0, 100) < config.chanceOfSecretive) {
            StupidExpress.LOGGER.info("Modifier [Secretive] enabled in this round! (2)");
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("secretive"), players / random.nextInt(8, 24));
        } else {
            StupidExpress.LOGGER.info("Modifier [Secretive] enabled in this round! (1)");
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("secretive"), 1);
        }

        /// KNIGHT
        if (players >= config.minPlayerForKnight && random.nextInt(0, 100) < config.chanceOfKnight) {
            StupidExpress.LOGGER.info("Modifier [Knight] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("knight"), 1);
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("knight"), 0);
        }

        /// JEB_
        if (random.nextInt(0, 100) < config.chanceOfJeb) {
            StupidExpress.LOGGER.info("Modifier [Jeb_] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("jeb_"), 1);
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("jeb_"), 0);
        }

        /// VIGOROUS (矫健)
        if (random.nextInt(0, 100) < config.chanceOfVigorous) {
            StupidExpress.LOGGER.info("Modifier [Vigorous] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("vigorous"), 2);
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("vigorous"), 0);
        }

        /// UNYIELDING (不屈)
        if (random.nextInt(0, 100) < config.chanceOfUnyielding) {
            StupidExpress.LOGGER.info("Modifier [Unyielding] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("unyielding"), 2);
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("unyielding"), 0);
        }

        /// PARANOID (偏执)
        if (random.nextInt(0, 100) < config.chanceOfParanoid) {
            StupidExpress.LOGGER.info("Modifier [Paranoid] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("paranoid"), 1);
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("paranoid"), 0);
        }

        SPLIT_PERSONALITY_CHANCE = NoellesRolesConfig.HANDLER.instance().chanceOfModifierSplitPersonality;
        if (SPLIT_PERSONALITY_CHANCE < 0) {
            SPLIT_PERSONALITY_CHANCE = 0;
        }
        /// SPLIT_PERSONALITY
        if (players >= config.minPlayerForSplitPersonality && random.nextInt(0, 100) < SPLIT_PERSONALITY_CHANCE) {
            StupidExpress.LOGGER.info("Modifier [Split Personality] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("split_personality"), 1);
        } else {
            Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("split_personality"), 0);
            if (players >= config.minPlayerForSplitPersonality
                    && random.nextInt(0, 100) <= config.chanceOfModifierLovers) {
                StupidExpress.LOGGER.info("Modifier [Lovers] enabled in this round!");
                Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("lovers"), 1);
            } else {
                Harpymodloader.MODIFIER_MAX.put(StupidExpress.id("lovers"), 0);
            }
        }

        /// EXPEDITION (远征队)
        if (random.nextInt(0, 100) < config.chanceOfExpedition) {
            StupidExpress.LOGGER.info("Modifier [Expedition] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(Noellesroles.id("expedition"), 1);
        } else {
            Harpymodloader.MODIFIER_MAX.put(Noellesroles.id("expedition"), 0);
        }

        /// TAXED (纳税)
        if (random.nextInt(0, 100) < config.chanceOfTaxed) {
            StupidExpress.LOGGER.info("Modifier [Taxed] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(Noellesroles.id("taxed"), 1);
        } else {
            Harpymodloader.MODIFIER_MAX.put(Noellesroles.id("taxed"), 0);
        }

        /// INTROVERTED (内向)
        if (random.nextInt(0, 100) < config.chanceOfIntroverted) {
            StupidExpress.LOGGER.info("Modifier [Introverted] enabled in this round!");
            Harpymodloader.MODIFIER_MAX.put(Noellesroles.id("introverted"), 1);
        } else {
            Harpymodloader.MODIFIER_MAX.put(Noellesroles.id("introverted"), 0);
        }
    }
}
