package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.customrole.CustomRoleData.EffectEntry;
import io.wifi.starrailexpress.customrole.CustomRoleData.InstinctModeData;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

/**
 * 自定义职业加载器
 * 负责从 CustomRoleConfig 读取配置并注册为 SRERole
 */
public class CustomRoleLoader {

    private static final Map<String, CustomRoleData> loadedRoles = new HashMap<>();
    private static final Map<String, SRERole> registeredRoles = new HashMap<>();
    // 自定义职业的本能透视配置
    private static final Map<String, Integer> instinctMaxRanges = new HashMap<>(); // englishId -> maxBlocksSquared
    private static final Map<String, Boolean> instinctSameColor = new HashMap<>(); // englishId -> sameColorFrame
    private static final Map<String, Boolean> instinctUnlimitedTeammate = new HashMap<>(); // englishId -> unlimitedTeammate
    // 新版直觉模式存储：englishRoleId -> 模式列表
    private static final Map<String, List<InstinctModeData>> instinctModeDataMap = new HashMap<>();
    // 技能初始冷却配置：roleIdentifier -> initialCooldownTicks
    private static final Map<ResourceLocation, Integer> initialCooldownMap = new HashMap<>();
    private static boolean mapRestrictionHandlerRegistered = false;
    private static boolean initialCooldownHandlerRegistered = false;
    private static boolean instinctHandlerRegistered = false;
    private static boolean gameEndHandlerRegistered = false;

    // 游戏结束时自动执行的指令：englishRoleId -> 指令列表
    private static final Map<String, List<String>> gameEndCommandsByRoleId = new HashMap<>();

    // 自定义胜利条件数据：englishRoleId -> CustomRoleData（用于运行时的胜利判定）
    private static final Map<String, CustomRoleData> customWinDataMap = new HashMap<>();

    /**
     * 重新加载所有自定义职业
     */
    public static void reload(MinecraftServer server) {
        // 清除旧数据
        initialCooldownMap.clear();
        gameEndCommandsByRoleId.clear();
        customWinDataMap.clear();
        // 先清除旧的自定义职业
        List<String> toRemove = new ArrayList<>();
        for (var entry : TMMRoles.ROLES.entrySet()) {
            if (entry.getValue() instanceof CustomNormalRole || "customrole".equals(entry.getKey().getNamespace())) {
                toRemove.add(entry.getKey().toString());
                // 同时清除已注册的技能，避免 re-register 时报 "already registered"
                RoleSkill.unregister(entry.getKey());
                // 清除 INITIAL_ITEMS_MAP 中的条目
                org.agmas.noellesroles.init.RoleInitialItems.INITIAL_ITEMS_MAP.remove(entry.getValue());
            }
        }
        // 在移除旧自定义职业前，先清理其它职业/修饰符对它的关联引用，
        // 否则重载后旧 SRERole 实例会残留在 relatedRoles/opposingRoles/occupationRoles 中，
        // 导致 postInit 重新绑定时出现重复，表现为“其它相关职业”出现两个相同的自定义职业。
        for (var entry : TMMRoles.ROLES.entrySet()) {
            if (entry.getValue() instanceof CustomNormalRole || "customrole".equals(entry.getKey().getNamespace())) {
                removeRoleReferences(entry.getValue());
            }
        }
        for (String key : toRemove) {
            TMMRoles.ROLES.remove(ResourceLocation.parse(key));
        }
        registeredRoles.clear();
        loadedRoles.clear();
        instinctMaxRanges.clear();
        instinctSameColor.clear();
        instinctUnlimitedTeammate.clear();
        instinctModeDataMap.clear();

        // 从服务器世界目录加载配置
        var level = server.overworld();
        var worldPath = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        CustomRoleConfig config = CustomRoleConfig.loadFromFile(worldPath);

        // world 存档目录没有配置时，使用空配置（不从 config 目录回退）
        if (config == null || config.roles == null || config.roles.isEmpty()) {
            config = new CustomRoleConfig();
            config.roles = new java.util.ArrayList<>();
        }

        for (CustomRoleData data : config.roles) {
            try {
                SRERole role = createRole(data);
                TMMRoles.registerRole(role);
                registeredRoles.put(data.englishId, role);
                loadedRoles.put(data.englishId, data);

                SRE.LOGGER.info("[CustomRole] Registered custom role: {}", data.englishId);
            } catch (Exception e) {
                SRE.LOGGER.error("[CustomRole] Failed to register custom role: {}", data.englishId, e);
            }
        }

        // 注册本能透视事件处理器（仅客户端，通过内部类避免服务端加载客户端类，仅首次注册）
        registerClientInstinctHandler();

        // 处理互斥、绑定生成、地图限制等（postInit 需要所有角色已注册）
        postInit();

        SRE.LOGGER.info("[CustomRole] Loaded {} custom roles", config.roles.size());
    }

    /**
     * 客户端重载自定义职业（从本地 config 目录读取）
     * 在收到服务端同步包并写入本地文件后调用
     */
    public static void reloadClient() {
        // 清除旧的客户端注册的自定义职业（包括技能注册，避免 re-register 抛异常）
        List<String> toRemove = new ArrayList<>();
        List<SRERole> removedRoles = new ArrayList<>();
        for (var entry : TMMRoles.ROLES.entrySet()) {
            if (entry.getValue() instanceof CustomNormalRole || "customrole".equals(entry.getKey().getNamespace())) {
                toRemove.add(entry.getKey().toString());
                removedRoles.add(entry.getValue());
                RoleSkill.unregister(entry.getKey());
                org.agmas.noellesroles.init.RoleInitialItems.INITIAL_ITEMS_MAP.remove(entry.getValue());
            }
        }
        // 同时清除 registeredRoles 中的旧角色技能（TMMRoles.ROLES 可能已被 clearCache 清空）
        for (var entry : registeredRoles.entrySet()) {
            ResourceLocation roleId = ResourceLocation.fromNamespaceAndPath("customrole", entry.getKey());
            RoleSkill.unregister(roleId);
        }
        // 在移除旧自定义职业前，先清理其它职业/修饰符对它的关联引用，
        // 否则重载后旧 SRERole 实例会残留在 relatedRoles/opposingRoles/occupationRoles 中，
        // 导致 postInit 重新绑定时出现重复，表现为“其它相关职业”出现两个相同的自定义职业。
        for (SRERole oldRole : removedRoles) {
            removeRoleReferences(oldRole);
        }
        toRemove.forEach(id -> TMMRoles.ROLES.remove(ResourceLocation.parse(id)));
        registeredRoles.clear();
        loadedRoles.clear();
        instinctMaxRanges.clear();
        instinctSameColor.clear();
        instinctUnlimitedTeammate.clear();
        instinctModeDataMap.clear();

        // 从客户端本地 config 目录加载（网络同步写入的）
        CustomRoleConfig config = CustomRoleConfig.loadFromDefaultPath();
        for (CustomRoleData data : config.roles) {
            try {
                SRERole role = createRole(data);
                TMMRoles.registerRole(role);
                loadedRoles.put(data.englishId, data);
                registeredRoles.put(data.englishId, role);

                // 注册报幕文本（客户端），确保欢迎报到能显示自定义职业
                try {
                    io.wifi.starrailexpress.client.gui.RoleAnnouncementTexts.registerRoleAnnouncementText(
                            role.identifier(),
                            new io.wifi.starrailexpress.client.gui.RoleAnnouncementTexts.RoleAnnouncementText(
                                    role.identifier(), role.getColor()));
                } catch (Throwable ignored) {
                }

                // 为拥有 instinctModes 的职业注册直觉模式事件处理器
                if (!data.instinctModes.isEmpty()) {
                    ClientInstinctHandler.registerModeEvents(data, role);
                }
            } catch (Exception e) {
                SRE.LOGGER.error("[CustomRole-Client] Failed to register: {}", data.englishId, e);
            }
        }

        // 注册本能透视事件处理器（客户端，仅首次）
        registerClientInstinctHandler();

        postInit();
        SRE.LOGGER.info("[CustomRole-Client] Reloaded {} custom roles from local config", config.roles.size());
    }

    /**
     * 从所有其它职业与修饰符的关联集合中移除指定（即将被卸载的）职业引用。
     * <p>
     * 自定义职业重载时会创建新的 {@link SRERole} 实例，而旧实例此前可能已被加入其它职业的
     * {@code relatedRoles}/{@code opposingRoles}/{@code occupationRoles}（例如通过
     * {@code bindWithRoles}/{@code twoWayOpposingJobs} 在 {@link #postInit()} 中建立绑定）。
     * 若只从 {@link TMMRoles#ROLES} 移除旧实例而不清理这些引用，重载后旧实例仍会残留，
     * 导致职业介绍的“其它相关职业”中出现两个相同的自定义职业。
     */
    private static void removeRoleReferences(SRERole oldRole) {
        for (SRERole other : TMMRoles.ROLES.values()) {
            other.relatedRoles.remove(oldRole);
            other.opposingRoles.remove(oldRole);
            other.occupationRoles.remove(oldRole);
        }
        for (SREModifier modifier : HMLModifiers.MODIFIERS) {
            modifier.relatedRoles.remove(oldRole);
        }
    }

    public static CustomRoleData getCustomRoleData(String englishId) {
        var result = loadedRoles.get(englishId);
        if (result != null)
            return result;
        // 回退：尝试从 world 存档加载（服务端）
        try {
            if (io.wifi.starrailexpress.SRE.SERVER != null) {
                var cfg = CustomRoleConfig.loadPreferWorldPath(io.wifi.starrailexpress.SRE.SERVER);
                var found = cfg.findRole(englishId);
                if (found != null)
                    return found;
            }
        } catch (Exception ignored) {
        }

        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            // 客户端回退：尝试从本地 config 目录（网络同步写入的）
            try {
                var cfg = CustomRoleConfig.loadFromDefaultPath();
                var found = cfg.findRole(englishId);
                if (found != null)
                    return found;
            } catch (Exception ignored) {
            }
            // 最终回退：尝试从客户端内存中的网络同步数据
            try {
                var cd = io.wifi.starrailexpress.client.network.CustomRoleClientNetwork.getSyncedRole(englishId);
                if (cd != null)
                    return cd;
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    public static SRERole getRegisteredRole(String englishId) {
        return registeredRoles.get(englishId);
    }

    /**
     * 根据配置创建 SRERole 实例
     */
    private static SRERole createRole(CustomRoleData data) {
        data.englishId = data.englishId.toLowerCase(); // 兜底：确保英文id全小写
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("customrole", data.englishId);

        // 解析颜色
        int color = (data.colorR << 16) | (data.colorG << 8) | data.colorB;

        // 解析心情类型
        SRERole.MoodType mood = "FAKE".equalsIgnoreCase(data.moodType)
                ? SRERole.MoodType.FAKE
                : SRERole.MoodType.REAL;

        // 解析体力
        int maxSprintTime;
        if (data.infiniteSprint) {
            maxSprintTime = -1; // 无限
        } else {
            int civilianSprint = io.wifi.starrailexpress.api.TMMRoles.CIVILIAN.getMaxSprintTime();
            maxSprintTime = (int) (civilianSprint * data.sprintMultiplier);
        }

        // 解析初始药水效果 (EffectEntry with amplifier)
        ArrayList<MobEffectInstance> effects = new ArrayList<>();
        if (!data.initialEffects.isEmpty()) {
            for (EffectEntry effEntry : data.initialEffects) {
                String cleaned = effEntry.effectId.trim();
                if (cleaned.isEmpty())
                    continue;
                try {
                    ResourceLocation effectRL = ResourceLocation.parse(cleaned);
                    var effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectRL);
                    if (effectHolder.isPresent()) {
                        effects.add(new MobEffectInstance(effectHolder.get(),
                                -1, effEntry.amplifier, false, false, false));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // 创建商店
        List<ShopEntry> shop = createShopEntries(data);

        // 创建角色（使用 CustomNormalRole 以支持自定义商店）
        SRERole role = new CustomNormalRole(id, color, data.isInnocent, data.canUseKiller,
                mood, maxSprintTime, data.canSeeTime,
                effects.isEmpty() ? new ArrayList<>() : effects, shop);

        // === 高级定义 ===
        role.setCanSeeCoin(data.canSeeCoin);
        if (data.canUseInstinct) {
            role.setCanUseInstinctAndNightVision(true);

            // 新版直觉模式优先
            if (!data.instinctModes.isEmpty()) {
                // 将模式 0 的默认值应用到 SRERole（静态配置作为兜底）
                InstinctModeData mode0 = data.instinctModes.get(0);
                role.setInstinctType(
                        parseInstinctType(mode0.seeingOff),
                        parseInstinctType(mode0.seeingOn));
                role.setBeSeenInstinctType(
                        parseInstinctType(mode0.beSeenOff),
                        parseInstinctType(mode0.beSeenOn));
                // 存储模式数据供运行时切换和事件处理器使用
                instinctModeDataMap.put(data.englishId, new ArrayList<>(data.instinctModes));
                // 也存储旧版范围配置供旧版处理器兜底
                if (!"*".equals(mode0.maxRange)) {
                    try {
                        int maxBlocks = Integer.parseInt(mode0.maxRange.trim());
                        instinctMaxRanges.put(data.englishId, maxBlocks * maxBlocks);
                    } catch (NumberFormatException ignored) {
                    }
                }
                instinctUnlimitedTeammate.put(data.englishId, mode0.unlimitedTeammate);
            } else {
                // 旧版直觉配置
                // 存储本能透视范围配置（供 ClientInstinctHandler 查询）
                if (!"*".equals(data.instinctMaxRange)) {
                    try {
                        int maxBlocks = Integer.parseInt(data.instinctMaxRange.trim());
                        instinctMaxRanges.put(data.englishId, maxBlocks * maxBlocks); // 存储平方值
                    } catch (NumberFormatException ignored) {
                    }
                }
                instinctSameColor.put(data.englishId, data.instinctSameColorFrame);
                instinctUnlimitedTeammate.put(data.englishId, data.instinctUnlimitedTeammate);
            }
        } else {
            role.setCanUseInstinctAndNightVision(false);
        }
        // 独立夜视开关：若显式设置，覆盖上面组合 setter 的夜视值（true=本能+夜视，false=仅本能无夜视）
        if (data.instinctNightVision != null)
            role.setInstinctNightVision(data.instinctNightVision);
        if (data.ableToPickUpRevolver != null)
            role.setAbleToPickUpRevolver(data.ableToPickUpRevolver);
        if (data.setNeutrals != null)
            role.setNeutrals(data.setNeutrals);
        if (data.setNeutralForKiller != null)
            role.setNeutralForKiller(data.setNeutralForKiller);
        if (data.setVigilanteTeam != null)
            role.setVigilanteTeam(data.setVigilanteTeam);
        if (data.canSeeTeammateKiller != null)
            role.setCanSeeTeammateKillerRole(data.canSeeTeammateKiller);
        role.setOccupiedRoleCount(data.occupiedRoleCount);
        role.setDefaultMax(data.maxCount);
        if (data.canAutoAddMoney != null)
            role.setCanAutoAddMoney(data.canAutoAddMoney);
        role.setCanBeRandomedByOtherRoles(data.canBeRandomedByOtherRoles);
        if (data.canIgnoreBlackout != null)
            role.setCanIgnoreBlackout(data.canIgnoreBlackout);
        if (data.canSeeBodyItems != null)
            role.setCanSeeBodyItems(data.canSeeBodyItems);
        if (data.canSeeBodyRoleInfo != null)
            role.setCanSeeBodyRoleInfo(data.canSeeBodyRoleInfo);
        if (data.canSeeBodyDeathReason != null)
            role.setCanSeeBodyDeathReason(data.canSeeBodyDeathReason);
        if (data.canSeeBodyKiller != null)
            role.setCanSeeBodyKiller(data.canSeeBodyKiller);

        // === 职业通用属性补全 ===
        if (data.neutralForInnocent != null)
            role.setNeutralForInnocent(data.neutralForInnocent);
        if (data.canSeeBodyName != null)
            role.setCanSeeBodyName(data.canSeeBodyName);
        if (data.canUseSkillWhileSpectator != null)
            role.setCanUseSkillWhileSpectator(data.canUseSkillWhileSpectator);
        if (data.mafiaTeam != null)
            role.setMafiaTeam(data.mafiaTeam);
        if (data.canBePoisoned != null)
            role.setCanBePoisoned(data.canBePoisoned);
        if (data.hiddenForRoleRotation != null)
            role.setHiddenForRoleRotation(data.hiddenForRoleRotation);
        if (data.specialMapRole != null && !"ALL".equalsIgnoreCase(data.specialMapRole)) {
            try {
                role.setSpecialMapRole(SRERole.SpecialMapRoleMap.valueOf(data.specialMapRole.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (data.specialVigilante != null)
            role.setSpecialVigilante(data.specialVigilante);
        if (data.refreshableSpecialVigilante != null)
            role.setRefreshableSpecialVigilante(data.refreshableSpecialVigilanteChance, data.refreshableSpecialVigilante);
        if (data.canJumpManhole != null)
            role.setCanJumpManhole(data.canJumpManhole);
        if (data.canAcrossFog != null)
            role.setCanAcrossFog(data.canAcrossFog);
        if (data.canUseSabotage != null)
            role.setCanUseSabotage(data.canUseSabotage);

        // === 生成选项 ===
        if (data.enableChance >= 0 && !data.useRareChance)
            role.setDefaultEnableChance(data.enableChance * 100);
        if (data.useRareChance && data.enableRareChance >= 0)
            role.setDefaultEnableChance(data.enableRareChance);
        if (data.enableNeededPlayerCount >= 0)
            role.setDefaultEnableNeededPlayerCount(data.enableNeededPlayerCount);
        if (data.defaultEnableMaxPlayerCount >= 0)
            role.setDefaultEnableMaxPlayerCount(data.defaultEnableMaxPlayerCount);

        // 自定义职业直接设置 spawnInfo 并禁止配置覆盖，确保工具设置的值始终生效
        role.setCanSetSpawnInfoInConfig(false);
        org.agmas.noellesroles.config.SpawnInfoConfig.SpawnInfo customSpawn = new org.agmas.noellesroles.config.SpawnInfoConfig.SpawnInfo();
        customSpawn.setMax(role.defaultMaxCount);
        if (role.defaultEnableNeedPlayerCount >= 0)
            customSpawn.setMinEnabledPlayer(role.defaultEnableNeedPlayerCount);
        if (role.defaultEnableChance >= 0)
            customSpawn.setEnableChance(role.defaultEnableChance);
        if (data.mapRestrictedTo != null) {
            for (String mapId : data.mapRestrictedTo) {
                if (mapId == null)
                    continue;
                String trimmed = mapId.trim();
                if (!trimmed.isEmpty()) {
                    customSpawn.map.add(trimmed);
                }
            }
        }
        role.setSpawnInfo(customSpawn);

        // 互斥和绑定生成（需要在所有角色注册完成后处理，这里只存储引用）
        // 这些将在 postInit 中处理

        // === 职业能力选项 ===
        // 初始物品
        if (!data.initialItems.isEmpty()) {
            List<ItemStack> stacks = new ArrayList<>();
            for (CustomRoleData.InitialItemEntry entry : data.initialItems) {
                if (entry.itemId == null || entry.itemId.isEmpty())
                    continue;
                try {
                    ResourceLocation itemId = ResourceLocation.parse(entry.itemId);
                    Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(itemId);
                    if (itemOpt.isPresent()) {
                        int count = Math.max(1, entry.count);
                        stacks.add(new ItemStack(itemOpt.get(), count));
                    }
                } catch (Exception ignored) {
                }
            }
            if (role instanceof CustomNormalRole customRole) {
                customRole.setDefaultItems(stacks);
                // 注册到 RoleInitialItems.INITIAL_ITEMS_MAP，确保所有发放路径都能获取
                List<java.util.function.Supplier<ItemStack>> suppliers = new ArrayList<>();
                for (ItemStack stack : stacks) {
                    final ItemStack snapshot = stack.copy(); // 捕获当前快照
                    suppliers.add(() -> snapshot.copy());
                }
                org.agmas.noellesroles.init.RoleInitialItems.INITIAL_ITEMS_MAP.put(role, suppliers);
            }
        }

        // 技能
        if (data.enableAbility && (!data.abilitySkillCommands.isEmpty() || !data.abilityDelayedCommands.isEmpty())) {
            final List<String> commands = new ArrayList<>(data.abilitySkillCommands);
            final List<String> delayedCommands = new ArrayList<>(data.abilityDelayedCommands);
            final int cooldownSeconds = data.abilityCooldownSeconds;
            final int delaySeconds = data.abilityDelaySeconds;

            ResourceLocation customSkillId = ResourceLocation.fromNamespaceAndPath(
                    role.identifier().getNamespace(), role.identifier().getPath() + "_ability");
            RoleSkill.register(role, RoleSkill.skill(
                    customSkillId,
                    "skill.sre.custom_role.ability",
                    context -> {
                        ServerPlayer player = context.player();
                        // 死亡/旁观者不能使用技能；若角色设置了 canUseSkillWhileSpectator 则允许旁观者释放
                        if (player.isSpectator() && !role.canUseSkillWhileSpectator()) {
                            return false;
                        }
                        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);

                        if (ability.cooldown > 0) {
                            return false;
                        }

                        // 执行即时指令（支持 @a @p @r @s 选择器）
                        for (String cmd : commands) {
                            executeConfiguredCommand(cmd, player);
                        }

                        // 延迟执行指令
                        if (!delayedCommands.isEmpty() && delaySeconds > 0) {
                            final UUID playerUuid = player.getUUID();
                            final ServerLevel level = player.serverLevel();
                            GameUtils.serverTaskQueue
                                    .add(new ServerTaskInfoClasses.SchedulerTask(delaySeconds * 20, () -> {
                                        ServerPlayer target = level.getServer().getPlayerList().getPlayer(playerUuid);
                                        if (target == null
                                                || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(target))
                                            return;
                                        for (String cmd : delayedCommands) {
                                            executeConfiguredCommand(cmd, target);
                                        }
                                    }));
                        }

                        return true;
                    }).cooldownSeconds(cooldownSeconds).build());

            // 存储技能初始冷却（游戏开始后首次分配角色时应用）
            if (data.abilityInitialCooldownSeconds > 0) {
                initialCooldownMap.put(role.identifier(), data.abilityInitialCooldownSeconds * 20);
            }
        }

        // 存储独立胜利条件数据（仅中立 && !setNeutralForKiller 时可用）
        if (data.enableCustomWin && data.setNeutrals != null && data.setNeutrals
                && data.setNeutralForKiller != null && !data.setNeutralForKiller) {
            customWinDataMap.put(data.englishId, data);
        }

        // 存储游戏结束时执行指令
        if (!data.gameEndCommands.isEmpty()) {
            gameEndCommandsByRoleId.put(data.englishId, new ArrayList<>(data.gameEndCommands));
            registerGameEndHandlerIfNeeded();
        }

        return role;
    }

    // ==================== 游戏结束自动执行指令 ====================

    private static void registerGameEndHandlerIfNeeded() {
        if (gameEndHandlerRegistered || gameEndCommandsByRoleId.isEmpty())
            return;
        gameEndHandlerRegistered = true;

        OnGameEnd.EVENT.register((level, comp) -> {
            if (!gameEndCommandsByRoleId.isEmpty()) {
                executeGameEndCommands(level, comp);
            }
        });
    }

    private static void executeGameEndCommands(ServerLevel level, SREGameWorldComponent comp) {
        for (ServerPlayer player : level.players()) {
            var role = comp.getRole(player);
            if (role == null)
                continue;
            String key = role.identifier().getPath();
            if (role instanceof CustomNormalRole || "customrole".equals(role.identifier().getNamespace())) {
                // 自定义职业用 englishId 匹配
                key = key.substring(key.lastIndexOf('/') + 1); // 去掉路径前缀
            }
            List<String> cmds = gameEndCommandsByRoleId.get(key);
            if (cmds == null)
                continue;
            for (String cmd : cmds) {
                executeConfiguredCommand(cmd, player);
            }
        }
    }

    /**
     * 后期处理：设置互斥、绑定生成、地图限制等
     */
    public static void postInit() {
        CustomRoleConfig config = CustomRoleConfig.getInstance();

        for (CustomRoleData data : config.roles) {
            SRERole role = registeredRoles.get(data.englishId);
            if (role == null)
                continue;

            // 双向互斥
            for (String oppId : data.twoWayOpposingJobs) {
                SRERole oppRole = findRole(oppId);
                if (oppRole != null) {
                    role.addTwoWayOpposingRole(oppRole);
                }
            }

            // 单向互斥
            for (String oppId : data.opposingJobs) {
                SRERole oppRole = findRole(oppId);
                if (oppRole != null) {
                    role.addOpposingRole(oppRole);
                }
            }

            // 绑定生成
            for (String bindId : data.bindWithRoles) {
                SRERole bindRole = findRole(bindId);
                if (bindRole != null) {
                    Harpymodloader.addOccupationRole(role, bindRole);
                }
            }
        }

        // 注册地图限制事件处理（仅首次，避免重复注册）
        if (!mapRestrictionHandlerRegistered) {
            registerMapRestrictionHandler();
            mapRestrictionHandlerRegistered = true;
        }

        // 注册技能初始冷却事件处理（仅首次）
        if (!initialCooldownHandlerRegistered) {
            registerInitialCooldownHandler();
            initialCooldownHandlerRegistered = true;
        }
    }

    /**
     * 注册限定地图刷新的事件处理器
     * 在游戏初始化时，检查自定义职业的地图限制列表，
     * 如果列表非空且当前地图不在列表中，则将该职业最大数量设为0
     */
    private static void registerMapRestrictionHandler() {
        org.agmas.harpymodloader.events.GameInitializeEvent.EVENT
                .register((serverLevel, gameWorldComponent, players) -> {
                    CustomRoleConfig config = CustomRoleConfig.getInstance();

                    // 获取当前地图ID
                    final String currentMap = getCurrentMapName(serverLevel);

                    for (CustomRoleData data : config.roles) {
                        if (data.mapRestrictedTo == null || data.mapRestrictedTo.isEmpty()) {
                            continue; // 没有地图限制，所有地图都可以刷新
                        }

                        SRERole role = registeredRoles.get(data.englishId);
                        if (role == null)
                            continue;

                        final String mapName = currentMap == null ? "" : currentMap.trim();
                        boolean allowed = data.mapRestrictedTo.stream()
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .anyMatch(mapId -> mapId.equalsIgnoreCase(mapName));

                        if (!allowed) {
                            // 当前地图不在允许列表中，禁用该职业
                            org.agmas.harpymodloader.Harpymodloader.setRoleMaximum(role.identifier(), 0);
                            SRE.LOGGER.info("[CustomRole] Map restriction: disabled '{}' (map: {})",
                                    data.englishId, mapName);
                        }
                    }
                });
    }

    /**
     * 注册技能初始冷却事件处理器
     * 在角色分配给玩家后，检查是否需要设置初始冷却
     */
    private static void registerInitialCooldownHandler() {
        org.agmas.harpymodloader.events.ModdedRoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayer serverPlayer))
                return;
            Integer cooldownTicks = initialCooldownMap.get(role.identifier());
            if (cooldownTicks != null && cooldownTicks > 0) {
                SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(serverPlayer);
                var definitions = RoleSkill.getDefinitions(role);
                if (definitions.isEmpty()) {
                    ability.setCooldown(cooldownTicks);
                } else {
                    ability.ensureSkills(definitions);
                    ability.setSkillCooldown(definitions.getFirst().id(), cooldownTicks);
                }
            }
        });
    }

    private static String getCurrentMapName(net.minecraft.server.level.ServerLevel serverLevel) {
        if (serverLevel.getServer() != null) {
            var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(serverLevel);
            if (areas != null && areas.mapName != null) {
                return areas.mapName;
            }
        }
        return "unknown";
    }

    private static SRERole findRole(String roleId) {
        // Try as ResourceLocation
        ResourceLocation id;
        if (roleId.contains(":")) {
            id = ResourceLocation.parse(roleId);
        } else {
            id = SRE.id(roleId);
        }
        return TMMRoles.ROLES.get(id);
    }

    /**
     * 获取自定义职业的本能透视最大范围（平方值），用于客户端事件处理
     */
    public static Integer getInstinctMaxRange(String englishId) {
        return instinctMaxRanges.get(englishId);
    }

    /**
     * 获取自定义职业的本能透视同色框设置，用于客户端事件处理
     */
    public static Boolean getInstinctSameColor(String englishId) {
        return instinctSameColor.get(englishId);
    }

    /**
     * 解析直觉类型字符串。
     * 支持预定义常量名（DEFAULT/NONE/KILLER_INSTINCT/OBSERVER_ROLE_COLOR/TARGET_ROLE_COLOR）
     * 以及自定义颜色格式 {@code CUSTOM(0xAARRGGBB)}。
     */
    public static InstinctType parseInstinctType(String str) {
        if (str == null || str.isEmpty())
            return InstinctType.DEFAULT;
        String upper = str.toUpperCase().trim();
        if (upper.startsWith("CUSTOM(") && upper.endsWith(")")) {
            String hex = upper.substring(7, upper.length() - 1).trim();
            try {
                long color = Long.decode(hex);
                return InstinctType.custom((int) color);
            } catch (NumberFormatException e) {
                SRE.LOGGER.warn("[CustomRole] Invalid CUSTOM color: {}", str);
                return InstinctType.DEFAULT;
            }
        }
        try {
            return InstinctType.valueOf(upper);
        } catch (IllegalArgumentException e) {
            SRE.LOGGER.warn("[CustomRole] Unknown instinct type: {}, falling back to DEFAULT", str);
            return InstinctType.DEFAULT;
        }
    }

    /**
     * 注册自定义职业的本能透视事件处理器（仅客户端）。
     * <p>
     * 必须在 {@code InstinctRenderer.registerInstinctEvents()} 之前注册，
     * 否则通用的本能处理器会先于本处理器返回结果，导致自定义职业的
     * 本能透视范围（instinctMaxRange）限制被忽略，玩家会超出设定范围仍被透视。
     */
    public static void registerClientInstinctHandler() {
        if (!instinctHandlerRegistered
                && FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
            ClientInstinctHandler.register();
            instinctHandlerRegistered = true;
        }
    }

    /**
     * 客户端本能透视事件处理器
     * 这是内部类，编译为独立 .class 文件（CustomRoleLoader$ClientInstinctHandler.class），
     * 不会被 JVM 在加载外层类时解析，避免服务端因引用客户端类而崩溃
     */
    private static class ClientInstinctHandler {
        static void register() {
            // 注册旧版本能透视后处理（仅对未使用 instinctModes 的角色生效）
            io.wifi.starrailexpress.event.client.CommonInstinctEvents.ALIVE_COMMON_AFTER_EVENT
                    .register((self, target, isInstinctEnabled) -> {
                        if (!(target instanceof net.minecraft.world.entity.player.Player))
                            return TrueFalseAndCustomResult.pass();
                        net.minecraft.world.entity.player.Player targetPlayer = (net.minecraft.world.entity.player.Player) target;
                        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                        if (client.player == null)
                            return TrueFalseAndCustomResult.pass();
                        if (!isInstinctEnabled)
                            return TrueFalseAndCustomResult.pass();

                        io.wifi.starrailexpress.cca.SREGameWorldComponent gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY
                                .get(client.player.level());
                        if (gameWorld == null)
                            return TrueFalseAndCustomResult.pass();
                        SRERole role = gameWorld.getRole(client.player);
                        if (role == null)
                            return TrueFalseAndCustomResult.pass();
                        if (!"customrole".equals(role.identifier().getNamespace())
                                || !(role instanceof CustomNormalRole))
                            return TrueFalseAndCustomResult.pass();

                        String englishId = role.identifier().getPath();

                        // 使用 instinctModes 的角色由 OBSERVER_HIGHLIGHT_EVENT 专门处理，这里跳过
                        if (instinctModeDataMap.containsKey(englishId))
                            return TrueFalseAndCustomResult.pass();

                        // 无限制透视队友：对同阵营玩家无视范围限制
                        Boolean unlimitedTeammate = instinctUnlimitedTeammate.get(englishId);
                        boolean isSameTeam = false;
                        if (unlimitedTeammate != null && unlimitedTeammate) {
                            SRERole targetRole = gameWorld.getRole(targetPlayer);
                            if (targetRole != null) {
                                boolean selfKiller = gameWorld.isKillerTeamRole(role);
                                boolean targetKiller = gameWorld.isKillerTeamRole(targetRole);
                                boolean selfInno = gameWorld.isInnocentTeamRole(role);
                                boolean targetInno = gameWorld.isInnocentTeamRole(targetRole);
                                if ((selfKiller && targetKiller) || (selfInno && targetInno)) {
                                    isSameTeam = true;
                                }
                            }
                        }

                        if (!isSameTeam) {
                            Integer maxRangeSq = instinctMaxRanges.get(englishId);
                            if (maxRangeSq != null) {
                                double distSq = client.player.distanceToSqr(targetPlayer);
                                if (distSq > maxRangeSq)
                                    return TrueFalseAndCustomResult.no();
                            }
                        }

                        Boolean sameColor = instinctSameColor.get(englishId);
                        if (sameColor != null && sameColor) {
                            if (io.wifi.starrailexpress.client.SREClient.gameComponent != null
                                    && io.wifi.starrailexpress.client.SREClient.gameComponent.isKillerTeamRole(role)) {
                                return TrueFalseAndCustomResult.custom(java.awt.Color.RED.getRGB());
                            }
                            return TrueFalseAndCustomResult.custom(java.awt.Color.GREEN.getRGB());
                        }

                        return TrueFalseAndCustomResult.pass();
                    });
        }

        /**
         * 为拥有 instinctModes 的自定义职业注册 OBSERVER / TARGET 事件处理器。
         * 始终使用模式 0。
         */
        static void registerModeEvents(CustomRoleData data, SRERole role) {
            ResourceLocation roleId = role.identifier();
            List<InstinctModeData> modes = data.instinctModes;
            if (modes == null || modes.isEmpty())
                return;

            instinctModeDataMap.put(data.englishId, new ArrayList<>(modes));
            InstinctModeData mode = modes.get(0);

            // OBSERVER：自定义职业「看别人」
            io.wifi.starrailexpress.event.client.RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(roleId,
                    (client, self, target, hasInstinct) -> {
                        if (!(target instanceof net.minecraft.world.entity.player.Player))
                            return TrueFalseAndCustomResult.pass();
                        net.minecraft.world.entity.player.Player tp = (net.minecraft.world.entity.player.Player) target;

                        if (!isWithinRange(self, tp, mode))
                            return TrueFalseAndCustomResult.disallow();

                        InstinctType type = hasInstinct
                                ? parseInstinctType(mode.seeingOn)
                                : parseInstinctType(mode.seeingOff);

                        return applyType(type, self, tp, role,
                                io.wifi.starrailexpress.client.SREClient.gameComponent != null
                                        ? io.wifi.starrailexpress.client.SREClient.gameComponent.getRole(tp)
                                        : null,
                                TrueFalseAndCustomResult.pass());
                    });

            // TARGET：别人看自定义职业
            io.wifi.starrailexpress.event.client.RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(roleId,
                    (client, self, target, hasInstinct) -> {
                        if (!(target instanceof net.minecraft.world.entity.player.Player))
                            return TrueFalseAndCustomResult.pass();
                        net.minecraft.world.entity.player.Player tp = (net.minecraft.world.entity.player.Player) target;

                        if (!isWithinRange(self, tp, mode))
                            return TrueFalseAndCustomResult.disallow();

                        InstinctType type = hasInstinct
                                ? parseInstinctType(mode.beSeenOn)
                                : parseInstinctType(mode.beSeenOff);

                        return applyType(type, self, tp,
                                io.wifi.starrailexpress.client.SREClient.gameComponent != null
                                        ? io.wifi.starrailexpress.client.SREClient.gameComponent.getRole(self)
                                        : null,
                                role,
                                TrueFalseAndCustomResult.pass());
                    });
        }

        private static TrueFalseAndCustomResult applyType(InstinctType type,
                net.minecraft.world.entity.player.Player self,
                net.minecraft.world.entity.player.Player target,
                SRERole selfRole, SRERole targetRole,
                TrueFalseAndCustomResult fallback) {
            if (type.isNone())
                return TrueFalseAndCustomResult.disallow();
            if (type.isObserverRoleColor()) {
                if (selfRole == null) return TrueFalseAndCustomResult.disallow();
                return TrueFalseAndCustomResult.custom(selfRole.getColor());
            }
            if (type.isTargetRoleColor()) {
                if (targetRole == null) return TrueFalseAndCustomResult.disallow();
                return TrueFalseAndCustomResult.custom(targetRole.getColor());
            }
            if (type.isCustom())
                return TrueFalseAndCustomResult.custom(type.getColor());
            if (type.isKillerInstinct())
                return TrueFalseAndCustomResult.pass();
            return fallback;
        }

        private static boolean isWithinRange(net.minecraft.world.entity.player.Player viewer,
                net.minecraft.world.entity.player.Player target, InstinctModeData mode) {
            if (mode == null) return true;
            if (mode.unlimitedTeammate
                    && io.wifi.starrailexpress.client.SREClient.gameComponent != null) {
                SRERole vr = io.wifi.starrailexpress.client.SREClient.gameComponent.getRole(viewer);
                SRERole tr = io.wifi.starrailexpress.client.SREClient.gameComponent.getRole(target);
                if (vr != null && tr != null) {
                    if ((io.wifi.starrailexpress.client.SREClient.gameComponent.isKillerTeamRole(vr)
                            && io.wifi.starrailexpress.client.SREClient.gameComponent.isKillerTeamRole(tr))
                            || (io.wifi.starrailexpress.client.SREClient.gameComponent.isInnocentTeamRole(vr)
                                    && io.wifi.starrailexpress.client.SREClient.gameComponent.isInnocentTeamRole(tr)))
                        return true;
                }
            }
            if (!"*".equals(mode.maxRange)) {
                try {
                    int maxBlocks = Integer.parseInt(mode.maxRange.trim());
                    if (viewer.distanceToSqr(target) > (double) maxBlocks * maxBlocks)
                        return false;
                } catch (NumberFormatException ignored) {
                }
            }
            return true;
        }
    }

    /**
     * 创建商店条目（带冷却和禁止重复购买支持）
     */
    public static List<ShopEntry> createShopEntries(CustomRoleData data) {
        List<ShopEntry> entries = new ArrayList<>();
        for (CustomRoleData.ShopEntryData entry : data.shopEntries) {
            final int cooldownTicks = entry.cooldownSeconds * 20;
            switch (entry.type) {
                case "item": {
                    if (!entry.itemId.isEmpty()) {
                        try {
                            ResourceLocation itemId = ResourceLocation.parse(entry.itemId);
                            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
                            if (item.isPresent()) {
                                final Item theItem = item.get();
                                entries.add(new ShopEntry(
                                        new ItemStack(theItem), entry.price, ShopEntry.Type.TOOL) {
                                    @Override
                                    public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                                        // 禁止重复购买：检查快捷栏是否已有该物品
                                        if (!entry.allowDuplicate) {
                                            for (var stack : player.getInventory().items) {
                                                if (stack.is(theItem))
                                                    return false;
                                            }
                                        }
                                        boolean result = super.onBuy(player);
                                        // 冷却
                                        if (result && cooldownTicks > 0
                                                && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                                            sp.getCooldowns().addCooldown(theItem, cooldownTicks);
                                        }
                                        return result;
                                    }
                                });
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    break;
                }
                case "psycho":
                    entries.add(new ShopEntry(
                            io.wifi.starrailexpress.index.TMMItems.PSYCHO_MODE.getDefaultInstance(),
                            entry.price, ShopEntry.Type.WEAPON) {
                        @Override
                        public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                            return io.wifi.starrailexpress.cca.SREPlayerShopComponent.usePsychoMode(player);
                        }
                    });
                    break;
                case "blackout":
                    entries.add(new ShopEntry(
                            io.wifi.starrailexpress.index.TMMItems.BLACKOUT.getDefaultInstance(),
                            entry.price, ShopEntry.Type.TOOL) {
                        @Override
                        public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                            return io.wifi.starrailexpress.cca.SREPlayerShopComponent.useBlackout(player);
                        }
                    });
                    break;
                case "monitor_fail":
                    entries.add(new ShopEntry(
                            io.wifi.starrailexpress.index.TMMItems.MONITOR_BROKEN.getDefaultInstance(),
                            entry.price, ShopEntry.Type.TOOL) {
                        @Override
                        public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                            return io.wifi.starrailexpress.cca.SREPlayerShopComponent.useMonitorBroken(player,
                                    io.wifi.starrailexpress.SREConfig.instance().monitorBrokenDuration * 20);
                        }
                    });
                    break;
                case "custom": {
                    if (!entry.itemId.isEmpty() && !entry.commands.isEmpty()) {
                        final List<String> cmds = new ArrayList<>(entry.commands);
                        try {
                            ResourceLocation itemId = ResourceLocation.parse(entry.itemId);
                            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
                            ItemStack display = item.map(ItemStack::new).orElse(ItemStack.EMPTY);
                            // 设置自定义商品名称
                            if (!entry.displayName.isEmpty()) {
                                display.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                                        net.minecraft.network.chat.Component.literal(entry.displayName));
                            }
                            entries.add(new ShopEntry(display, entry.price, ShopEntry.Type.TOOL) {
                                @Override
                                public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                                    for (String cmd : cmds) {
                                        executeConfiguredCommand(cmd, player);
                                    }
                                    // 冷却
                                    if (cooldownTicks > 0 && !display.isEmpty()
                                            && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                                        sp.getCooldowns().addCooldown(display.getItem(), cooldownTicks);
                                    }
                                    return true;
                                }
                            });
                        } catch (Exception ignored) {
                        }
                    }
                    break;
                }
            }
        }
        return entries;
    }

    private static void executeConfiguredCommand(String cmd, net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null || cmd == null
                || cmd.isBlank()) {
            return;
        }

        String processed = processCommandSelectors(cmd
                .replace("<player>", serverPlayer.getGameProfile().getName())
                .replace("~ ~ ~", String.format("%.1f %.1f %.1f",
                        serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ())),
                serverPlayer);
        try {
            serverPlayer.getServer().getCommands().performPrefixedCommand(
                    serverPlayer.getServer().createCommandSourceStack()
                            .withPermission(SREConfig.instance().customRolePermission)
                            .withSuppressedOutput()
                            .withEntity(serverPlayer)
                            .withLevel(serverPlayer.serverLevel())
                            .withPosition(serverPlayer.position())
                            .withRotation(serverPlayer.getRotationVector()),
                    processed);
        } catch (Exception e) {
            SRE.LOGGER.warn("[CustomRole] Failed to execute configured command '{}': {}", processed, e.getMessage());
        }
    }

    /**
     * 处理指令中的 @p 选择器（其余 @s @a @r 由 Minecraft 原生解析）
     * 
     * @p → 距离当前玩家最近的存活玩家（排除自己）
     */
    private static String processCommandSelectors(String cmd, net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer sp))
            return cmd;

        // @p → 最近的其他存活玩家（排除自己）
        if (cmd.contains("@p")) {
            var level = sp.serverLevel();
            var alivePlayers = level.getPlayers(p -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p));
            ServerPlayer nearest = null;
            double minDist = Double.MAX_VALUE;
            for (ServerPlayer p : alivePlayers) {
                if (p == sp)
                    continue;
                double dist = sp.distanceToSqr(p);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = p;
                }
            }
            cmd = cmd.replace("@p",
                    nearest != null ? nearest.getGameProfile().getName() : sp.getGameProfile().getName());
        }

        return cmd;
    }

    // ==================== 自定义独立胜利判定 ====================

    /**
     * 检查所有启用了独立胜利的自定义角色是否满足胜利条件。
     * 在 {@link org.agmas.noellesroles.CustomWinnerClass} 中调用。
     *
     * @return WinStatus.CUSTOM 如果某自定义角色胜利，否则 WinStatus.NOT_MODIFY
     */
    public static WinStatus checkCustomRoleWins(ServerLevel serverLevel, WinStatus currentWinStatus) {
        if (customWinDataMap.isEmpty())
            return WinStatus.NOT_MODIFY;

        var gameComponent = SREGameWorldComponent.KEY.get(serverLevel);
        int alivePlayerCount = 0;
        for (var p : serverLevel.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(p))
                alivePlayerCount++;
        }

        for (var entry : customWinDataMap.entrySet()) {
            CustomRoleData data = entry.getValue();
            ResourceLocation roleId = ResourceLocation.fromNamespaceAndPath("customrole", data.englishId);
            SRERole role = TMMRoles.ROLES.get(roleId);
            if (role == null)
                continue;

            // 找到拥有此自定义角色的存活玩家
            ServerPlayer customPlayer = null;
            for (var p : serverLevel.players()) {
                if (GameUtils.isPlayerAliveAndSurvival(p) && gameComponent.isRole(p, role)) {
                    customPlayer = p;
                    break;
                }
            }
            if (customPlayer == null)
                continue;

            // 条件6: 当场上只剩下自己和某职业时 (类似教父)
            if (!data.customWinLastWithRoles.isEmpty() && (currentWinStatus == WinStatus.KILLERS
                    || currentWinStatus == WinStatus.PASSENGERS || currentWinStatus == WinStatus.TIME)) {
                // 检查场上是否只有自己 + 指定职业
                boolean onlySelfAndSpecifiedRoles = true;
                for (var p : serverLevel.players()) {
                    if (!GameUtils.isPlayerAliveAndSurvival(p) || p == customPlayer)
                        continue;
                    SRERole pRole = gameComponent.getRole(p);
                    if (pRole == null) {
                        onlySelfAndSpecifiedRoles = false;
                        break;
                    }
                    String pRoleId = pRole.identifier().toString();
                    boolean matched = false;
                    for (String allowedId : data.customWinLastWithRoles) {
                        if (pRoleId.equals(allowedId.trim())) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        onlySelfAndSpecifiedRoles = false;
                        break;
                    }
                }
                if (onlySelfAndSpecifiedRoles) {
                    doCustomWin(serverLevel, data, customPlayer);
                    return WinStatus.CUSTOM;
                }
                // 阻止游戏提前结束（场上还有自己和指定职业）
                if (currentWinStatus != WinStatus.TIME) {
                    return WinStatus.NONE;
                }
            }

            // 条件5: 当场上一共只剩下自己存活时 (类似纵火犯)
            if (data.customWinLastAlive && alivePlayerCount == 1) {
                doCustomWin(serverLevel, data, customPlayer);
                return WinStatus.CUSTOM;
            }
            // 阻止游戏结束（纵火犯式）
            if (data.customWinLastAlive && (currentWinStatus == WinStatus.KILLERS
                    || currentWinStatus == WinStatus.PASSENGERS)) {
                return WinStatus.NONE;
            }

            // 条件4: 存活到最后 (类似芙兰朵露)
            if (data.customWinSurviveToLast && (alivePlayerCount <= 1 || currentWinStatus == WinStatus.TIME)) {
                doCustomWin(serverLevel, data, customPlayer);
                return WinStatus.CUSTOM;
            }
            if (data.customWinSurviveToLast && !currentWinStatus.equals(WinStatus.NONE)) {
                return WinStatus.NONE;
            }

            // 条件7: 拥有指定标签时躺在床上取得独立胜利 (类似小偷)
            if (!data.customWinTagSleep.isEmpty() && customPlayer.getTags().contains(data.customWinTagSleep)
                    && customPlayer.isSleeping()) {
                doCustomWin(serverLevel, data, customPlayer);
                return WinStatus.CUSTOM;
            }

            // 条件8: 当玩家拥有某个物品时取得独立胜利
            if (!data.customWinHeldItem.isEmpty()) {
                boolean hasItem = false;
                ResourceLocation itemId = ResourceLocation.tryParse(data.customWinHeldItem);
                if (itemId != null) {
                    var itemOpt = BuiltInRegistries.ITEM.getOptional(itemId);
                    if (itemOpt.isPresent()) {
                        for (var stack : customPlayer.getInventory().items) {
                            if (stack.is(itemOpt.get())) {
                                hasItem = true;
                                break;
                            }
                        }
                    }
                }
                if (hasItem) {
                    doCustomWin(serverLevel, data, customPlayer);
                    return WinStatus.CUSTOM;
                }
            }
        }

        return WinStatus.NOT_MODIFY;
    }

    private static void doCustomWin(ServerLevel serverLevel, CustomRoleData data, ServerPlayer winner) {
        int color = (data.colorR << 16) | (data.colorG << 8) | data.colorB;
        var roundComponent = SREGameRoundEndComponent.KEY.get(serverLevel);
        boolean hasCustomText = !data.customWinTitle.isEmpty() || !data.customWinSubtitle.isEmpty();

        if (hasCustomText && roundComponent != null) {
            // 使用 CUSTOM_COMPONENT 模式直接显示用户自定义文本
            if (!data.customWinTitle.isEmpty()) {
                roundComponent.CustomWinnerTitle = Component.literal(data.customWinTitle
                        .replace("<player>", winner.getGameProfile().getName()));
            }
            if (!data.customWinSubtitle.isEmpty()) {
                roundComponent.CustomWinnerSubtitle = Component.literal(data.customWinSubtitle
                        .replace("<player>", winner.getGameProfile().getName()));
            }
            if (roundComponent.CustomWinnerTitle == null) {
                roundComponent.CustomWinnerTitle = Component.literal("");
            }
            roundComponent.CustomWinnerColor = color;
            roundComponent.CustomWinnerID = data.englishId;
            roundComponent.setRoundEndData(serverLevel.players(), WinStatus.CUSTOM_COMPONENT);
            GameUtils.stopGame(serverLevel);
        } else {
            // 无自定义文本时使用 CUSTOM 模式，走翻译键
            RoleUtils.customWinnerWin(serverLevel, data.englishId, color);
        }
    }
}
