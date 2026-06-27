package org.agmas.noellesroles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.screen.DetectiveInspectScreenHandler;
import org.agmas.noellesroles.client.screen.ModScreenHandlers;
import org.agmas.noellesroles.client.screen.PostmanScreenHandler;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.entity.LockEntityManager;
import org.agmas.noellesroles.game.roles.innocence.athlete.AthletePlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.ayayaya.AyayayaPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.boxer.BoxerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.detective.DetectivePlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.locksmith_inspiration.LocksmithInspirationComponent;
import org.agmas.noellesroles.game.roles.innocence.psychologist.PsychologistPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.singer.SingerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.super_star.SuperStarPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.telegrapher.TelegrapherPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.veteran.VeteranKnifeHandler;
import org.agmas.noellesroles.game.roles.killer.conspirator.ConspiratorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.dio.DIOPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.trapper.TrapperPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.admirer.AdmirerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.slippery_ghost.SlipperyGhostPlayerComponent;
import org.agmas.noellesroles.init.*;
import org.agmas.noellesroles.packet.*;
import org.agmas.noellesroles.packet.Loot.*;
import org.agmas.noellesroles.register.RiceEventRegister;
import org.agmas.noellesroles.register.RicePacketTypeRegister;
import org.agmas.noellesroles.register.RiceReceiverRegister;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.Pair;
import org.agmas.noellesroles.utils.lottery.LotteryManager;

import java.util.ArrayList;
import java.util.List;

import static org.agmas.noellesroles.Noellesroles.LOGGER;
import static org.agmas.noellesroles.Noellesroles.MOD_ID;

/**
 * Rice's Role Rhapsody - 哈皮快车角色扩展模组
 * 
 * 这是模组的主入口类，负责：
 * 1. 注册自定义角色
 * 2. 监听角色分配事件
 * 3. 注册网络包
 * 4. 初始化物品和配置
 */
public class RicesRoleRhapsody implements ModInitializer {

    // ==================== 常量定义 ====================

    // ==================== 原版角色列表（用于判断） ====================
    public static final ArrayList<SRERole> VANILLA_ROLES = new ArrayList<>();
    public static final ArrayList<ResourceLocation> VANILLA_ROLE_IDS = new ArrayList<>();

    // ==================== 网络包 ID ====================
    public static final CustomPacketPayload.Type<ConspiratorC2SPacket> CONSPIRATOR_PACKET = ConspiratorC2SPacket.ID;
    public static final CustomPacketPayload.Type<TelegrapherC2SPacket> TELEGRAPHER_PACKET = TelegrapherC2SPacket.ID;
    public static final CustomPacketPayload.Type<PostmanC2SPacket> POSTMAN_PACKET = PostmanC2SPacket.ID;
    public static final CustomPacketPayload.Type<DetectiveC2SPacket> DETECTIVE_PACKET = DetectiveC2SPacket.ID;
    public static final CustomPacketPayload.Type<BoxerAbilityC2SPacket> BOXER_ABILITY_PACKET = BoxerAbilityC2SPacket.ID;
    public static final CustomPacketPayload.Type<StalkerGazeC2SPacket> STALKER_GAZE_PACKET = StalkerGazeC2SPacket.ID;
    public static final CustomPacketPayload.Type<StalkerDashC2SPacket> STALKER_DASH_PACKET = StalkerDashC2SPacket.ID;
    public static final CustomPacketPayload.Type<AthleteAbilityC2SPacket> ATHLETE_ABILITY_PACKET = AthleteAbilityC2SPacket.ID;
    public static final CustomPacketPayload.Type<AdmirerGazeC2SPacket> ADMIRER_GAZE_PACKET = AdmirerGazeC2SPacket.ID;
    public static final CustomPacketPayload.Type<TrapperC2SPacket> TRAPPER_PACKET = TrapperC2SPacket.ID;
    public static final CustomPacketPayload.Type<TrapperSwitchC2SPacket> TRAPPER_SWITCH_PACKET = TrapperSwitchC2SPacket.ID;
    public static final CustomPacketPayload.Type<StarAbilityC2SPacket> STAR_ABILITY_PACKET = StarAbilityC2SPacket.ID;
    public static final CustomPacketPayload.Type<SingerAbilityC2SPacket> SINGER_ABILITY_PACKET = SingerAbilityC2SPacket.ID;
    public static final CustomPacketPayload.Type<VeteranDashC2SPacket> VETERAN_DASH_PACKET = VeteranDashC2SPacket.ID;
    public static final CustomPacketPayload.Type<PsychologistC2SPacket> PSYCHOLOGIST_PACKET = PsychologistC2SPacket.ID;
    public static final CustomPacketPayload.Type<PuppeteerC2SPacket> PUPPETEER_PACKET = PuppeteerC2SPacket.ID;

    public static final CustomPacketPayload.Type<CreeperAbilityC2SPacket> CREEPER_ABILITY_PACKET = CreeperAbilityC2SPacket.ID;
    public static final CustomPacketPayload.Type<ShadowFalconAbilityC2SPacket> SHADOW_FALCON_ABILITY_PACKET = ShadowFalconAbilityC2SPacket.ID;
    public static final CustomPacketPayload.Type<PilotRemoveJetpackC2SPacket> PILOT_REMOVE_JETPACK_PACKET = PilotRemoveJetpackC2SPacket.ID;

    // 建筑师技能包
    public static final CustomPacketPayload.Type<BuilderAbilityC2SPacket> BUILDER_ABILITY_PACKET = BuilderAbilityC2SPacket.ID;

    public static final CustomPacketPayload.Type<LockGameC2Packet> LOCK_GAME_PACKET = LockGameC2Packet.ID;
    public static final CustomPacketPayload.Type<KeyForgeGameC2Packet> KEY_FORGE_GAME_PACKET = KeyForgeGameC2Packet.ID;
    public static final CustomPacketPayload.Type<LootRequestC2SPacket> LOOT_REQUIRE_PACKET = LootRequestC2SPacket.ID;
    public static final CustomPacketPayload.Type<LootMultiRequestC2SPacket> LOOT_MULTI_REQUIRE_PACKET = LootMultiRequestC2SPacket.ID;
    public static final CustomPacketPayload.Type<LootPoolsInfoRequestC2SPacket> LOOT_POOLS_INFO_REQUEST_PACKET = LootPoolsInfoRequestC2SPacket.ID;
    public static final CustomPacketPayload.Type<LootPoolsInfoCheckC2SPacket> LOOT_POOLS_INFO_CHECK_CLIENT_PACKET = LootPoolsInfoCheckC2SPacket.ID;
    public static final CustomPacketPayload.Type<LootDataRefreshC2SPacket> LOOT_DATA_REFRESH_CLIENT_PACKET = LootDataRefreshC2SPacket.ID;

    @Override
    public void onInitialize() {

        // // 1. 初始化原版角色列表（用于后续判断）
        // initVanillaRoles();
        //
        // // 2. 注册自定义角色
        // ModRoles.init();
        //
        // // 3. 注册物品
        // ModItems.init();
        //
        // // 4. 注册实体
        // ModEntities.init();
        //
        // // 5. 注册 ScreenHandlers
        // ModScreenHandlers.init();
        //
        // // 6. 初始化商店
        //
        //
        // // 7. 注册网络包（如果有自定义技能需要客户端-服务端通信）
        // registerPackets();
        //
        // // 8. 注册事件监听
        // registerEvents();
        //
        // // 9. 加载配置（如果使用 YACL）
        // // ModConfig.HANDLER.load();
        //
        // // 10. 注册傀儡师尸体收集事件
        // registerPuppeteerBodyCollect();
    }

    public static void onInitialize1() {

        // 1. 初始化原版角色列表（用于后续判断）
        initVanillaRoles();

        // 2. 注册自定义角色
        // ModRoles.init();

        // 3. 注册物品


        ModEffects.init();

        FunnyItems.init();

        // 4. 注册实体
        ModEntities.init();
        ModMenus.initialize();

        // 5. 注册 ScreenHandlers
        ModScreenHandlers.init();

        // 6. 初始化商店

        // 7. 注册网络包（如果有自定义技能需要客户端-服务端通信）
        registerPackets();

        // 8. 注册事件监听
        registerEvents();
        org.agmas.noellesroles.scene.SceneRuntimeEvents.register();

        // 9. 注册模仿者技能映射
        org.agmas.noellesroles.game.roles.killer.imitator.ImitatorSkillRegistry.registerAll();

        // 9. 加载配置（如果使用 YACL）
        // ModConfig.HANDLER.load();

        // 10. 注册傀儡师尸体收集事件
        RiceEventRegister.registerPuppeteerBodyCollect();
    }

    /**
     * 初始化原版角色列表
     */
    private static void initVanillaRoles() {
        VANILLA_ROLES.add(TMMRoles.KILLER);
        VANILLA_ROLES.add(TMMRoles.VIGILANTE);
        VANILLA_ROLES.add(TMMRoles.CIVILIAN);
        VANILLA_ROLES.add(TMMRoles.LOOSE_END);

        VANILLA_ROLE_IDS.add(TMMRoles.KILLER.identifier());
        VANILLA_ROLE_IDS.add(TMMRoles.VIGILANTE.identifier());
        VANILLA_ROLE_IDS.add(TMMRoles.CIVILIAN.identifier());
        VANILLA_ROLE_IDS.add(TMMRoles.LOOSE_END.identifier());
    }

    /**
     * 注册网络包
     * 用于客户端-服务端通信（例如技能使用）
     */
    private static void registerPackets() {

        // PayloadTypeRegistry.playC2S().register(ThiefStealC2SPacket.ID,
        // ThiefStealC2SPacket.CODEC);

        // 网络数据包类型注册（已归一化至 RicePacketTypeRegister）
        RicePacketTypeRegister.registerPayloadTypes();

        // 服务端网络包接收器注册（已归一化至 RiceReceiverRegister）
        RiceReceiverRegister.registerReceivers();
    }

    /**
     * 注册事件监听
     */
    private static void registerEvents() {

    }

    /**
     * 查找攻击者
     * 遍历附近玩家找到持有对应武器的
     */
    public static Player findAttackerWithWeapon(Player victim, boolean isKnife) {
        // 获取附近5格内的所有玩家
        for (Player player : victim.level().players()) {
            if (player.equals(victim))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(player))
                continue;
            if (player.distanceToSqr(victim) > 25)
                continue; // 5格距离

            ItemStack mainHand = player.getMainHandItem();
            if (isKnife && mainHand.is(io.wifi.starrailexpress.index.TMMItems.KNIFE)) {
                return player;
            }
            if (!isKnife && mainHand.is(io.wifi.starrailexpress.index.TMMItems.BAT)) {
                return player;
            }
        }
        return null;
    }

    /**
     * 角色分配时的处理逻辑
     *
     * @param player 被分配角色的玩家
     * @param role   分配的角色
     */
    public static void onRoleAssigned(Player player, SRERole role) {
        // 重置玩家的技能冷却
        SREAbilityPlayerComponent abilityComponent = ModComponents.ABILITY.get(player);
        abilityComponent.init();

        // ==================== 清除其他角色的组件状态 ====================
        // 当角色改变时，需要清除之前角色的组件状态
        // 这对于傀儡师操控假人变成其他杀手后返回本体的情况尤其重要

        // 如果新角色不是跟踪者，清除跟踪者组件状态
        if (!role.equals(ModRoles.STALKER)) {
            StalkerPlayerComponent stalkerComp = ModComponents.STALKER.get(player);
            if (stalkerComp.isActiveStalker()) {
                stalkerComp.clearAll();
            }
        }

        // 如果新角色不是慕恋者，清除慕恋者组件状态
        if (!role.equals(ModRoles.ADMIRER)) {
            AdmirerPlayerComponent admirerComp = ModComponents.ADMIRER.get(player);
            if (admirerComp.isActiveAdmirer()) {
                admirerComp.clearAll();
            }
        }

        // 如果新角色不是傀儡师，清除傀儡师组件状态（但保留操控假人状态，因为傀儡师返回本体需要这个）
        // 注意：傀儡师操控假人时角色会临时变成其他杀手，但不应该清除傀儡师组件
        // 所以这里只在完全不是傀儡师相关的角色变化时才清除
        if (!role.equals(ModRoles.PUPPETEER)) {
            PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(player);
            // 只有在不是操控假人状态时才清除（操控假人时需要保留状态以便返回）
            if (puppeteerComp.isActivePuppeteer() && !puppeteerComp.isControllingPuppet) {
                puppeteerComp.clearAll();
            }
        }


        // ==================== 复仇者角色处理 ====================
        if (role.equals(ModRoles.AVENGER)) {
            // 重置复仇者组件
            // AvengerPlayerComponent avengerComponent = ModComponents.AVENGER.get(player);
            // avengerComponent.reset();
            //
            // // 随机绑定一个无辜玩家作为保护目标
            // // 延迟执行以确保所有玩家都已分配角色
            // if (player instanceof ServerPlayer serverPlayer) {
            // serverPlayer.getServer().execute(avengerComponent::bindRandomTarget);
            // }

        }

        // ==================== 阴谋家角色处理 ====================
        if (role.equals(ModRoles.CONSPIRATOR)) {
            // 重置阴谋家组件
            ConspiratorPlayerComponent conspiratorComponent = ModComponents.CONSPIRATOR.get(player);
            conspiratorComponent.init();
        }

        // ==================== 捣蛋鬼角色处理 ====================
        if (role.equals(ModRoles.PRANKSTER)) {
            // 重置捣蛋鬼组件
            SlipperyGhostPlayerComponent slipperyGhostComponent = ModComponents.PRANKSTER.get(player);
            slipperyGhostComponent.init();
        }

        // ==================== 工程师角色处理 ====================
        if (role.equals(ModRoles.ENGINEER)) {
            // 工程师不需要特殊组件，只需要商店访问权限
            // 商店逻辑在 EngineerShopMixin 中处理
        }

        // ==================== 斗士角色处理 ====================
        if (role.equals(ModRoles.FIGHTER)) {
            // 重置斗士组件 - 设置开局冷却
            BoxerPlayerComponent boxerComponent = ModComponents.FIGHTER.get(player);
            boxerComponent.init();
        }

        // ==================== 静语者角色处理 ====================
        if (role.equals(ModRoles.SILENCER)) {
            org.agmas.noellesroles.game.roles.killer.silencer.SilencerPlayerComponent silencerComponent =
                org.agmas.noellesroles.game.roles.killer.silencer.SilencerPlayerComponent.KEY.get(player);
            silencerComponent.init();
        }

        // ==================== 探员角色处理 ====================
        if (role.equals(ModRoles.AGENT)) {
            // 重置探员组件
            DetectivePlayerComponent detectiveComponent = ModComponents.AGENT.get(player);
            detectiveComponent.init();
        }

        // ==================== 电报员角色处理 ====================
        if (role.equals(ModRoles.TELEGRAPHER)) {
            // 重置电报员组件
            TelegrapherPlayerComponent telegrapherComponent = ModComponents.TELEGRAPHER.get(player);
            telegrapherComponent.init();
        }

        // ==================== 跟踪者角色处理 ====================
        if (role.equals(ModRoles.STALKER)) {
            // 重置跟踪者组件
            StalkerPlayerComponent stalkerComponent = ModComponents.STALKER.get(player);
            stalkerComponent.init();
        }

        // ==================== 运动员角色处理 ====================
        if (role.equals(ModRoles.ATHLETE)) {
            // 重置运动员组件
            AthletePlayerComponent athleteComponent = ModComponents.ATHLETE.get(player);
            athleteComponent.init();
        }

        // ==================== 慕恋者角色处理 ====================
        if (role.equals(ModRoles.ADMIRER)) {
            // 重置慕恋者组件
            AdmirerPlayerComponent admirerComponent = ModComponents.ADMIRER.get(player);
            admirerComponent.init();
        }

        // ==================== 设陷者角色处理 ====================
        if (role.equals(ModRoles.TRAPPER)) {
            // 重置设陷者组件
            TrapperPlayerComponent trapperComponent = ModComponents.TRAPPER.get(player);
            trapperComponent.init();
        }

        // ==================== 明星角色处理 ====================
        if (role.equals(ModRoles.SUPERSTAR)) {
            // 重置明星组件
            SuperStarPlayerComponent starComponent = ModComponents.STAR.get(player);
            starComponent.init();
        }

        // ==================== 退伍军人角色处理 ====================
        if (role.equals(ModRoles.VETERAN)) {
            // 重置退伍军人组件
            // 不需要（谁写的啊！！！）
        }

        // ==================== 歌手角色处理 ====================
        if (role.equals(ModRoles.SINGER)) {
            // 重置歌手组件
            SingerPlayerComponent singerComponent = ModComponents.SINGER.get(player);
            singerComponent.init();
        }

        // ==================== 心理学家角色处理 ====================
        if (role.equals(ModRoles.PSYCHOLOGIST)) {
            // 重置心理学家组件
            PsychologistPlayerComponent psychComponent = ModComponents.PSYCHOLOGIST.get(player);
            psychComponent.init();
        }

        // ==================== 傀儡师角色处理 ====================
        if (role.equals(ModRoles.PUPPETEER)) {
            PuppeteerPlayerComponent puppeteerComponent = ModComponents.PUPPETEER.get(player);
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());

            // 只有在游戏进行中且傀儡师已被标记时才保留状态（假人死亡返回本体的情况）
            // 游戏结束或新分配角色时都应该重置组件
            boolean isGameRunning = gameWorld != null && gameWorld.isRunning();
            if (isGameRunning && puppeteerComponent.isPuppeteerMarked) {
                LOGGER.info("Puppeteer returned to body - keeping existing state");
            } else {
                LOGGER.info("Puppeteer reset - new game or new puppeteer assignment");
                puppeteerComponent.init();
            }
        }

        // ==================== 示例：根据角色给予物品 ====================
        //

        // ==================== 建筑师角色处理 ====================
        if (role.equals(ModRoles.BUILDER)) {
            org.agmas.noellesroles.game.roles.innocence.builder.BuilderPlayerComponent builderComponent = org.agmas.noellesroles.component.ModComponents.BUILDER.get(player);
            builderComponent.init();
        }
        // if (role.equals(ModRoles.EXAMPLE_ROLE)) {
        // // 给予物品
        // player.giveItemStack(new ItemStack(Items.PAPER));
        //
        // // 设置角色特定的组件数据
        // ExamplePlayerComponent componentKey = ExamplePlayerComponent.KEY.get(player);
        // componentKey.reset();
        // componentKey.sync();
        // }

        // ==================== 示例：设置初始金钱 ====================
        // PlayerShopComponent shopComponent = PlayerShopComponent.KEY.get(player);
        // shopComponent.setBalance(100);

    }

    // ==================== 工具方法 ====================

    /**
     * 创建本模组的资源标识符
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * 判断角色是否为原版角色
     */
    public static boolean isVanillaRole(SRERole role) {
        return VANILLA_ROLES.contains(role);
    }

    /**
     * 判断角色是否为原版角色（通过ID）
     */
    public static boolean isVanillaRole(ResourceLocation roleId) {
        return VANILLA_ROLE_IDS.contains(roleId);
    }
}
