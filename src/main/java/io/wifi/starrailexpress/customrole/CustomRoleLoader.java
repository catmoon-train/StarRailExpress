package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.ExtraEffectRole;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.customrole.CustomRoleData.EffectEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private static boolean mapRestrictionHandlerRegistered = false;

    /**
     * 重新加载所有自定义职业
     */
    public static void reload(MinecraftServer server) {
        // 先清除旧的自定义职业
        List<String> toRemove = new ArrayList<>();
        for (var entry : TMMRoles.ROLES.entrySet()) {
            if (entry.getKey().getPath().startsWith("customrole:")) {
                toRemove.add(entry.getKey().toString());
            }
        }
        for (String key : toRemove) {
            TMMRoles.ROLES.remove(ResourceLocation.parse(key));
        }
        registeredRoles.clear();
        loadedRoles.clear();
        instinctMaxRanges.clear();
        instinctSameColor.clear();

        // 从服务器世界目录加载配置
        var level = server.overworld();
        var worldPath = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        CustomRoleConfig config = CustomRoleConfig.loadFromFile(worldPath);

        // 如果 world 存档目录没有配置，则尝试回退读取默认的 config 目录（方便服务端/单机保存位置不一致时使用）
        if (config == null || config.roles == null || config.roles.isEmpty()) {
            try {
                CustomRoleConfig defaultConfig = CustomRoleConfig.loadFromDefaultPath();
                if (defaultConfig != null && defaultConfig.roles != null && !defaultConfig.roles.isEmpty()) {
                    config = defaultConfig;
                }
            } catch (Exception ignored) {}
        }

        for (CustomRoleData data : config.roles) {
            try {
                SRERole role = createRole(data);
                TMMRoles.registerRole(role);
                registeredRoles.put(data.englishId, role);
                loadedRoles.put(data.englishId, data);

                // 存储本能透视配置
                if (data.canUseInstinct) {
                    if (!"*".equals(data.instinctMaxRange)) {
                        try {
                            int maxBlocks = Integer.parseInt(data.instinctMaxRange.trim());
                            instinctMaxRanges.put(data.englishId, maxBlocks * maxBlocks); // 存储平方值便于比较
                        } catch (NumberFormatException ignored) {}
                    }
                    instinctSameColor.put(data.englishId, data.instinctSameColorFrame);
                }

                SRE.LOGGER.info("[CustomRole] Registered custom role: {}", data.englishId);
            } catch (Exception e) {
                SRE.LOGGER.error("[CustomRole] Failed to register custom role: {}", data.englishId, e);
            }
        }

        // 注册本能透视事件处理器（仅客户端，通过内部类避免服务端加载客户端类）
        if (FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
            ClientInstinctHandler.register();
        }

        // 处理互斥、绑定生成、地图限制等（postInit 需要所有角色已注册）
        postInit();

        SRE.LOGGER.info("[CustomRole] Loaded {} custom roles", config.roles.size());
    }

    public static CustomRoleData getCustomRoleData(String englishId) {
        var result = loadedRoles.get(englishId);
        if (result != null) return result;
        // 回退：尝试从 world 存档或默认 config 加载
        try {
            if (io.wifi.starrailexpress.SRE.SERVER != null) {
                var cfg = CustomRoleConfig.loadPreferWorldPath(io.wifi.starrailexpress.SRE.SERVER);
                return cfg.findRole(englishId);
            } else {
                var cfg = CustomRoleConfig.loadFromDefaultPath();
                return cfg.findRole(englishId);
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static SRERole getRegisteredRole(String englishId) {
        return registeredRoles.get(englishId);
    }

    /**
     * 根据配置创建 SRERole 实例
     */
    private static SRERole createRole(CustomRoleData data) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("customrole", data.englishId);

        // 解析颜色
        int color = (data.colorR << 16) | (data.colorG << 8) | data.colorB;

        // 解析心情类型
        SRERole.MoodType mood = "FAKE".equalsIgnoreCase(data.moodType)
            ? SRERole.MoodType.FAKE : SRERole.MoodType.REAL;

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
                if (cleaned.isEmpty()) continue;
                try {
                    ResourceLocation effectRL = ResourceLocation.parse(cleaned);
                    var effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectRL);
                    if (effectHolder.isPresent()) {
                        effects.add(new MobEffectInstance(effectHolder.get(),
                            -1, effEntry.amplifier, false, false, false));
                    }
                } catch (Exception ignored) {}
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
        if (data.canUseInstinct) role.setCanUseInstinct(true);
        if (data.ableToPickUpRevolver != null) role.setAbleToPickUpRevolver(data.ableToPickUpRevolver);
        if (data.setNeutrals != null && data.setNeutrals) role.setNeutrals(true);
        if (data.setNeutralForKiller != null && data.setNeutralForKiller) role.setNeutralForKiller(true);
        if (data.setVigilanteTeam != null && data.setVigilanteTeam) role.setVigilanteTeam(true);
        if (data.canSeeTeammateKiller != null) role.setCanSeeTeammateKiller(data.canSeeTeammateKiller);
        role.setOccupiedRoleCount(data.occupiedRoleCount);
        role.setMax(data.maxCount);
        if (data.canAutoAddMoney != null) role.setCanAutoAddMoney(data.canAutoAddMoney);
        role.setCanBeRandomedByOtherRoles(data.canBeRandomedByOtherRoles);
        if (data.canIgnoreBlackout != null) role.setCanIgnoreBlackout(data.canIgnoreBlackout);
        if (data.canSeeBodyItems != null) role.setCanSeeBodyItems(data.canSeeBodyItems);
        if (data.canSeeBodyRoleInfo != null) role.setCanSeeBodyRoleInfo(data.canSeeBodyRoleInfo);
        if (data.canSeeBodyDeathReason != null) role.setCanSeeBodyDeathReason(data.canSeeBodyDeathReason);
        if (data.canSeeBodyKiller != null) role.setCanSeeBodyKiller(data.canSeeBodyKiller);

        // === 生成选项 ===
        if (data.enableChance >= 0 && !data.useRareChance) role.setEnableChance(data.enableChance);
        if (data.useRareChance && data.enableRareChance >= 0) role.setEnableRareChance(data.enableRareChance);
        if (data.enableNeededPlayerCount >= 0) role.setEnableNeededPlayerCount(data.enableNeededPlayerCount);

        // 互斥和绑定生成（需要在所有角色注册完成后处理，这里只存储引用）
        // 这些将在 postInit 中处理

        // === 职业能力选项 ===
        // 初始物品
        if (!data.initialItems.isEmpty()) {
            List<ItemStack> stacks = new ArrayList<>();
            for (CustomRoleData.InitialItemEntry entry : data.initialItems) {
                if (entry.itemId == null || entry.itemId.isEmpty()) continue;
                try {
                    ResourceLocation itemId = ResourceLocation.parse(entry.itemId);
                    Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(itemId);
                    if (itemOpt.isPresent()) {
                        int count = Math.max(1, entry.count);
                        stacks.add(new ItemStack(itemOpt.get(), count));
                    }
                } catch (Exception ignored) {}
            }
            if (role instanceof CustomNormalRole customRole) {
                customRole.setDefaultItems(stacks);
            }
        }

        // 技能
        if (data.enableAbility && !data.abilitySkillCommands.isEmpty()) {
            final List<String> commands = new ArrayList<>(data.abilitySkillCommands);
            final int cooldownSeconds = data.abilityCooldownSeconds;

            RoleSkill.register(role, context -> {
                ServerPlayer player = context.player();
                SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);

                if (ability.cooldown > 0) {
                    return;
                }

                // Execute all commands
                for (String cmd : commands) {
                    String processed = cmd
                        .replace("<player>", player.getGameProfile().getName())
                        .replace("~ ~ ~", String.format("%.1f %.1f %.1f",
                            player.getX(), player.getY(), player.getZ()));
                    player.getServer().getCommands().performPrefixedCommand(
                        player.getServer().createCommandSourceStack(), processed);
                }

                ability.setCooldown(cooldownSeconds * 20);
                ability.sync();
            });
        }

        return role;
    }

    /**
     * 后期处理：设置互斥、绑定生成、地图限制等
     */
    public static void postInit() {
        CustomRoleConfig config = CustomRoleConfig.getInstance();

        for (CustomRoleData data : config.roles) {
            SRERole role = registeredRoles.get(data.englishId);
            if (role == null) continue;

            // 双向互斥
            for (String oppId : data.twoWayOpposingJobs) {
                SRERole oppRole = findRole(oppId);
                if (oppRole != null) {
                    role.addTwoWayOpposingJobs(oppRole);
                }
            }

            // 单向互斥
            for (String oppId : data.opposingJobs) {
                SRERole oppRole = findRole(oppId);
                if (oppRole != null) {
                    role.addOpposingJobs(oppRole);
                }
            }

            // 绑定生成
            for (String bindId : data.bindWithRoles) {
                SRERole bindRole = findRole(bindId);
                if (bindRole != null) {
                    org.agmas.harpymodloader.modded_murder.RoleAssignmentManager.addOccupationRole(role, bindRole);
                }
            }
        }

        // 注册地图限制事件处理（仅首次，避免重复注册）
        if (!mapRestrictionHandlerRegistered) {
            registerMapRestrictionHandler();
            mapRestrictionHandlerRegistered = true;
        }
    }

    /**
     * 注册限定地图刷新的事件处理器
     * 在游戏初始化时，检查自定义职业的地图限制列表，
     * 如果列表非空且当前地图不在列表中，则将该职业最大数量设为0
     */
    private static void registerMapRestrictionHandler() {
        org.agmas.harpymodloader.events.GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
            CustomRoleConfig config = CustomRoleConfig.getInstance();

            // 获取当前地图ID
            final String currentMap = getCurrentMapName(serverLevel);

            for (CustomRoleData data : config.roles) {
                if (data.mapRestrictedTo == null || data.mapRestrictedTo.isEmpty()) {
                    continue; // 没有地图限制，所有地图都可以刷新
                }

                SRERole role = registeredRoles.get(data.englishId);
                if (role == null) continue;

                final String mapName = currentMap;
                boolean allowed = data.mapRestrictedTo.stream()
                        .anyMatch(mapId -> mapId.trim().equalsIgnoreCase(mapName));

                if (!allowed) {
                    // 当前地图不在允许列表中，禁用该职业
                    org.agmas.harpymodloader.Harpymodloader.setRoleMaximum(role.identifier(), 0);
                    SRE.LOGGER.info("[CustomRole] Map restriction: disabled '{}' (map: {})",
                            data.englishId, mapName);
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
     * 客户端本能透视事件处理器
     * 这是内部类，编译为独立 .class 文件（CustomRoleLoader$ClientInstinctHandler.class），
     * 不会被 JVM 在加载外层类时解析，避免服务端因引用客户端类而崩溃
     */
    private static class ClientInstinctHandler {
        static void register() {
            io.wifi.starrailexpress.event.OnGetInstinctHighlight.EVENT.register((target, isInstinctEnabled) -> {
                if (!(target instanceof net.minecraft.world.entity.player.Player)) return -1;
                net.minecraft.world.entity.player.Player targetPlayer = (net.minecraft.world.entity.player.Player) target;
                net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                if (client.player == null) return -1;
                if (!isInstinctEnabled) return -1;

                io.wifi.starrailexpress.cca.SREGameWorldComponent gameWorld =
                    io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(client.player.level());
                if (gameWorld == null) return -1;
                SRERole role = gameWorld.getRole(client.player);
                if (role == null) return -1;
                if (!"customrole".equals(role.identifier().getNamespace())) return -1;

                String englishId = role.identifier().getPath();

                Integer maxRangeSq = instinctMaxRanges.get(englishId);
                if (maxRangeSq != null) {
                    double distSq = client.player.distanceToSqr(targetPlayer);
                    if (distSq > maxRangeSq) return -2;
                }

                Boolean sameColor = instinctSameColor.get(englishId);
                if (sameColor != null && sameColor) {
                    if (io.wifi.starrailexpress.client.SREClient.gameComponent != null
                        && io.wifi.starrailexpress.client.SREClient.gameComponent.isKillerTeamRole(role)) {
                        return java.awt.Color.RED.getRGB();
                    }
                    return java.awt.Color.GREEN.getRGB();
                }

                return -1;
            });
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
                                    new ItemStack(theItem), entry.price, ShopEntry.Type.TOOL
                                ) {
                                    @Override
                                    public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                                        // 禁止重复购买：检查快捷栏是否已有该物品
                                        if (!entry.allowDuplicate) {
                                            for (var stack : player.getInventory().items) {
                                                if (stack.is(theItem)) return false;
                                            }
                                        }
                                        boolean result = super.onBuy(player);
                                        // 冷却
                                        if (result && cooldownTicks > 0 && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                                            sp.getCooldowns().addCooldown(theItem, cooldownTicks);
                                        }
                                        return result;
                                    }
                                });
                            }
                        } catch (Exception ignored) {}
                    }
                    break;
                }
                case "psycho":
                    entries.add(new ShopEntry(
                        io.wifi.starrailexpress.index.TMMItems.PSYCHO_MODE.getDefaultInstance(),
                        entry.price, ShopEntry.Type.WEAPON
                    ) {
                        @Override
                        public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                            return io.wifi.starrailexpress.cca.SREPlayerShopComponent.usePsychoMode(player);
                        }
                    });
                    break;
                case "blackout":
                    entries.add(new ShopEntry(
                        io.wifi.starrailexpress.index.TMMItems.BLACKOUT.getDefaultInstance(),
                        entry.price, ShopEntry.Type.TOOL
                    ) {
                        @Override
                        public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                            return io.wifi.starrailexpress.cca.SREPlayerShopComponent.useBlackout(player);
                        }
                    });
                    break;
                case "monitor_fail":
                    entries.add(new ShopEntry(
                        io.wifi.starrailexpress.index.TMMItems.MONITOR_BROKEN.getDefaultInstance(),
                        entry.price, ShopEntry.Type.TOOL
                    ) {
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
                                    if (player.getServer() != null) {
                                        for (String cmd : cmds) {
                                            String processed = cmd
                                                .replace("<player>", player.getGameProfile().getName())
                                                .replace("~ ~ ~", String.format("%.1f %.1f %.1f",
                                                    player.getX(), player.getY(), player.getZ()));
                                            player.getServer().getCommands().performPrefixedCommand(
                                                player.getServer().createCommandSourceStack(), processed);
                                        }
                                    }
                                    // 冷却
                                    if (cooldownTicks > 0 && !display.isEmpty()
                                            && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                                        sp.getCooldowns().addCooldown(display.getItem(), cooldownTicks);
                                    }
                                    return true;
                                }
                            });
                        } catch (Exception ignored) {}
                    }
                    break;
                }
            }
        }
        return entries;
    }
}
