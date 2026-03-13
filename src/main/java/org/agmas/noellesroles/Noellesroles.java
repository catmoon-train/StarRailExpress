package org.agmas.noellesroles;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.TMMRoles;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.ChairWheelRaceGame;
import org.agmas.noellesroles.init.*;
import org.agmas.noellesroles.modifier.NRModifiers;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.commands.*;
import org.agmas.noellesroles.presets.Preset;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RightClickBlockManager;
import org.agmas.noellesroles.utils.RoleUtils;
import org.agmas.noellesroles.blood.BloodMain;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.repack.HSRConstants;
import org.agmas.noellesroles.repack.HSRItems;
import org.agmas.noellesroles.repack.HSRSounds;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Collator;
import java.util.*;

public class Noellesroles implements ModInitializer {
    public static final String MOD_ID = "noellesroles";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ArrayList<Role> VANNILA_ROLES = new ArrayList<>();
    public static final ArrayList<ResourceLocation> VANNILA_ROLE_IDS = new ArrayList<>();
    public static final String fuckMojang = Decode("4075a514cc856d7e4bdf11132a9178b9337997a6955635e5c56e07ff089b3a7a");

    public static boolean gunsCooled = false;
    // ==================== 初始物品配置 ====================
    public static String isOnlineMode = null;

    public static List<Role> getAllRoles() {
        ArrayList<Role> clone = new ArrayList<>(TMMRoles.ROLES.values());
        return clone;
    }

    public static List<Role> getEnableRoles_ServerSide() {
        ArrayList<Role> clone = new ArrayList<>(TMMRoles.ROLES.values());
        clone.removeIf(
                r -> {
                    if (HarpyModLoaderConfig.HANDLER.instance().disabled.contains(r.getIdentifier().toString()))
                        return true;
                    if (String.valueOf(r.identifier()).equals("starrailexpress:discovery_civilian"))
                        return true;
                    return false;
                });
        return clone;
    }

    /**
     * Get Role type(int) for a role
     * 
     * @param role
     * @return - 0: Innocent and Cannot Use Killer
     *         - 1: Innocent but can Use Killer
     *         - 2: Neturals but not for killer
     *         - 3: Neturals for killer
     *         - 4: Killer
     */
    public static int getRoleType$Int(Role role) {
        if (role == null)
            return -1;
        if (role.isInnocent() && !role.canUseKiller()) {
            return 0;
        }
        if (role.isInnocent()) {
            return 1;
        }

        if (role.isNeutrals() && !role.isNeutralForKiller()) {
            return 2;
        }
        if (role.isNeutrals() && role.isNeutralForKiller()) {
            return 3;
        }
        if (!role.isInnocent() && !role.canUseKiller() && !role.isNeutralForKiller()) {
            return 2;
        }
        if (!role.isInnocent() && !role.canUseKiller() && role.isNeutralForKiller()) {
            return 3;
        }
        if (role.canUseKiller()) {
            return 4;
        }
        return -1; // Unknown
    }

    public static List<Role> getAllRolesSorted() {
        return getAllRolesSorted(false);
    }

    public static List<Role> getAllRolesSorted(boolean killerFirst) {
        ArrayList<Role> clone = new ArrayList<>(TMMRoles.ROLES.values());
        Collator collator = Collator.getInstance();
        clone.sort((a, b) -> {
            int rt_a = getRoleType$Int(a);
            int rt_b = getRoleType$Int(b);
            if (a != null && b != null) {
                if (rt_a > rt_b)
                    return killerFirst ? -1 : 1;
                if (rt_a < rt_b)
                    return killerFirst ? 1 : -1;
                if (a.identifier().getNamespace().equals(b.identifier().getNamespace())) {
                    String r_a = RoleUtils.getRoleName(a).getString();
                    String r_b = RoleUtils.getRoleName(b).getString();
                    return collator.compare(r_a, r_b);
                } else {
                    String nameSpaceA = a.identifier().getNamespace();
                    String nameSpaceB = b.identifier().getNamespace();
                    return collator.compare(nameSpaceA, nameSpaceB);
                }
            } else {
                return 0;
            }
        });
        return clone;
    }

    private static String Decode(String string) {
        return "ad636239a06098ecbc3176df6c0e1a3aaaecb1db2f6a71180ec5a26940bd4c8b";
    }

    public static List<Role> getEnableKillerRoles() {
        ArrayList<Role> clone = new ArrayList<>(TMMRoles.ROLES.values());
        clone.removeIf(
                r -> !r.canUseKiller()
                        || r.getIdentifier() == ModRoles.DIO_ID
                        || HarpyModLoaderConfig.HANDLER.instance().disabled.contains(r.getIdentifier().toString()));
        return clone;
    }

    public static @NotNull ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }

    @Override
    public void onInitialize() {
        RightClickBlockManager.init();
        ArgumentTypeRegistry.registerArgumentType(
                Noellesroles.id("color"), // 唯一 ID
                ModColorArgument.class, // 你的参数类
                SingletonArgumentInfo.contextFree(ModColorArgument::color) // 工厂方法
        );
        HSRConstants.init();
        Harpymodloader.HIDDEN_MODIFIERS.add(SEModifiers.REFUGEE.identifier().getPath());
        // 初始化模组角色列表
        ModRoles.init();
        // 初始化修饰符
        NRModifiers.init();
        // 初始化初始物品映射
        RoleInitialItems.initializeInitialItems();

        // 初始化原版角色列表
        initializeVanillaRoles();

        // 加载配置
        NoellesRolesConfig.HANDLER.load();
        RicesRoleRhapsody.onInitialize1();

        // 初始化系统组件
        NRSounds.initialize();
        registerMaxRoleCount();

        // 注册事件处理器
        ModEventsRegister.registerEvents();

        // 注册命令
        BroadcastCommand.register();
        AdminFreeCamCommand.register();
        SetRoleMaxCommand.register();
        ConfigCommand.register();
        LootCommand.register();
        VTCommand.register();
        GameFunctionsCommand.register();
        RoomCommand.register();
        StuckCommand.register();
        DisplayItemCommand.register();
        GoodsManagerCommand.register();

        // 加载预设配置
        Preset.PresetManager.loadPresets();

        // 注册预设命令
        PresetCommand.register();

        // 注册网络包类型
        ModPackets.registerPackets();

        // 注册网络处理器
        ModPacketsReciever.registerPackets();
        // 初始化HSR组件
        HSRItems.init();
        HSRSounds.init();

        // 注册商店
        RoleShopHandler.shopRegister();
        ModEventsRegister.registerPredicate();

        // 注册方块
        ModBlocks.initialize();

        SREGameModes.registerGameMode(ChairWheelRaceGame.identifier, new ChairWheelRaceGame());

        // 注册血液粒子工厂
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Noellesroles.id("deathblood"),
                BloodMain.BLOOD_PARTICLE);

        // 注册药水效果
        ModEffects.init();
    }

    /**
     * 初始化原版角色列表
     */
    private void initializeVanillaRoles() {
        VANNILA_ROLES.add(TMMRoles.KILLER);
        VANNILA_ROLES.add(TMMRoles.VIGILANTE);
        VANNILA_ROLES.add(TMMRoles.CIVILIAN);
        VANNILA_ROLES.add(TMMRoles.LOOSE_END);
        VANNILA_ROLE_IDS.add(TMMRoles.LOOSE_END.identifier());
        VANNILA_ROLE_IDS.add(TMMRoles.VIGILANTE.identifier());
        VANNILA_ROLE_IDS.add(TMMRoles.CIVILIAN.identifier());
        VANNILA_ROLE_IDS.add(TMMRoles.KILLER.identifier());
    }

    private void registerMaxRoleCount() {
        InitModRolesMax.registerStatics();
        InitModRolesMax.registerDynamic();
    }
}