package org.agmas.noellesroles.client;

import net.exmo.sre.repair.network.*;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.InputConstants;
import dev.doctor4t.ratatouille.util.TextUtils;
import io.wifi.ConfigCompact.ui.RoleManageConfigUI;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.StaminaRenderer;
import io.wifi.starrailexpress.client.StatusInit;
import io.wifi.starrailexpress.client.data.ClientRoleRosterCache;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import io.wifi.starrailexpress.client.gui.screen.NewspaperScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.client.util.TMMItemTooltips;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.vote.client.ClientVoteCache;
import io.wifi.starrailexpress.content.vote.client.RoleRotationCache;
import io.wifi.starrailexpress.content.vote.client.VolunteerCache;
import io.wifi.starrailexpress.event.AllowNameRender;
import io.wifi.starrailexpress.event.OnRoundStartWelcomeTimmer;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.BreakArmorPayload;
import io.wifi.starrailexpress.network.packet.EnableTaskHighlightPacket;
import io.wifi.starrailexpress.network.packet.ShowCustomNewspaperPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.blood.BloodMain;
import org.agmas.noellesroles.client.blood.particle.BloodParticle;
import org.agmas.noellesroles.client.commands.SREClientCommand;
import org.agmas.noellesroles.client.event.MutableComponentResult;
import org.agmas.noellesroles.client.event.OnMessageBelowMoneyRenderer;
import org.agmas.noellesroles.client.hud.CommonClientHudRenderer;
import net.exmo.sre.repair.client.RepairEscapeHud;
import net.exmo.sre.repair.client.HunterCageBlockEntityRenderer;

import org.agmas.noellesroles.client.renderer.BreakingBridgeBlockEntityRenderer;
import org.agmas.noellesroles.client.renderer.SREPlushBlockEntityRenderer;
import org.agmas.noellesroles.client.renderer.VendingMachinesBlockEntityRenderer;
import org.agmas.noellesroles.client.screen.*;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.content.block_entity.LotteryMachineBlockEntity;
import org.agmas.noellesroles.content.block_entity.SupplyCrateBlockEntity;
import org.agmas.noellesroles.content.block_entity.VendingMachinesBlockEntity;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.LockEntity;
import org.agmas.noellesroles.content.entity.WheelchairEntityModel;
import org.agmas.noellesroles.content.entity.WheelchairEntityRenderer;
import org.agmas.noellesroles.content.entity.WheelchairFieldItemRenderer;
import org.agmas.noellesroles.content.item.*;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.init.*;
import org.agmas.noellesroles.packet.*;
import org.agmas.noellesroles.packet.Loot.*;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.lottery.LotteryManager;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.awt.*;
import java.util.*;
import java.util.List;

import static org.agmas.noellesroles.client.RicesRoleRhapsodyClient.*;
import static org.agmas.noellesroles.content.effects.TimeStopEffect.clientPositions;
import static org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent.isPlayerBodyEntity;
import static org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent.playerBodyEntities;

public class NoellesrolesClient implements ClientModInitializer {
    public static boolean hasInitStatusBar = false;
    public static int insanityTime = 0;
    private static BlockPos repairHeldSearchTarget = null;
    public static KeyMapping roleIntroClientBind = KeyBindingHelper
            .registerKeyBinding(new KeyMapping("key." + Noellesroles.MOD_ID + ".role_intro",
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.starrailexpress.keybinds"));
    public static KeyMapping mapIntroClientBind = KeyBindingHelper
            .registerKeyBinding(new KeyMapping("key." + Noellesroles.MOD_ID + ".map_intro",
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, "category.starrailexpress.keybinds"));
    public static KeyMapping roleGuessNoteClientBind = KeyBindingHelper
            .registerKeyBinding(new KeyMapping("key." + Noellesroles.MOD_ID + ".guess_role_note",
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "category.starrailexpress.keybinds"));
    public static KeyMapping abilityBind = KeyBindingHelper
            .registerKeyBinding(new KeyMapping("key." + Noellesroles.MOD_ID + ".ability",
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.starrailexpress.keybinds"));
    public static KeyMapping nextAbilityBind = KeyBindingHelper
            .registerKeyBinding(new KeyMapping("key." + Noellesroles.MOD_ID + ".next_ability",
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "category.starrailexpress.keybinds"));
    public static KeyMapping taskInstinctBind = KeyBindingHelper
            .registerKeyBinding(new KeyMapping("key.noellesroles.taskinstinct",
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.starrailexpress.keybinds"));
    public static KeyMapping showHelpDisplay = KeyBindingHelper
            .registerKeyBinding(new KeyMapping("key.noellesroles.show_help_display",
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.starrailexpress.keybinds"));
    public static KeyMapping foolPrayerBind = KeyBindingHelper
            .registerKeyBinding(new KeyMapping("key.noellesroles.fool_prayer",
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.starrailexpress.keybinds"));
    public static boolean isTaskInstinctEnabled = false;
    // 记录被触发启用透视的任务路标位置
    public static Set<BlockPos> enabledTaskMarkerPositions = new HashSet<>();
    public static boolean isShowHelpDisplay = true;
    private static boolean foolMeetingPauseHandled = false;
    /** 操纵师附身期间，本地相机是否已绑定到被操控目标 */
    public static boolean manipulatorCameraBound = false;
    public static Map<UUID, UUID> SHUFFLED_PLAYER_ENTRIES_CACHE = Maps.newHashMap();
    public static Map<UUID, UUID> JEB_SHUFFLED_PLAYER_ENTRIES_CACHE = Maps.newHashMap();
    public static int jebShuffleTime = 0;
    public static final int JEB_SHUFFLE_INTERVAL_TICKS = 20 * 5;
    public static ArrayList<BroadcastMessageInfo> currentBroadcastMessage = new ArrayList<>();
    public static BloodMain bloodMain = new BloodMain();
    public static Map<UUID, AbstractClientPlayer> lastTimeStopRenderPlayer = new HashMap<>();
    public static long lastClientTickTime = 0;
    public static final long CLIENT_TICK_INTERVAL_MS = 50; // 1000ms / 20 ticks per second = 50ms per tick

    private static void refreshJebShuffledCache(LocalPlayer localPlayer) {
        if (localPlayer == null || localPlayer.level() == null) {
            JEB_SHUFFLED_PLAYER_ENTRIES_CACHE.clear();
            return;
        }

        var worldModifiers = WorldModifierComponent.KEY.get(localPlayer.level());
        if (worldModifiers == null) {
            JEB_SHUFFLED_PLAYER_ENTRIES_CACHE.clear();
            return;
        }

        List<UUID> candidates = new ArrayList<>(ClientSkinCache.PLAYER_ENTRIES_CACHE.keySet());
        if (candidates.isEmpty()) {
            JEB_SHUFFLED_PLAYER_ENTRIES_CACHE.clear();
            return;
        }

        Set<UUID> activeJebPlayers = new HashSet<>();
        for (var player : localPlayer.level().players()) {
            if (!worldModifiers.isModifier(player, SEModifiers.JEB_)) {
                continue;
            }
            UUID playerId = player.getUUID();
            activeJebPlayers.add(playerId);
            UUID target = candidates.get(localPlayer.getRandom().nextInt(candidates.size()));
            JEB_SHUFFLED_PLAYER_ENTRIES_CACHE.put(playerId, target);
        }

        JEB_SHUFFLED_PLAYER_ENTRIES_CACHE.keySet().removeIf(id -> !activeJebPlayers.contains(id));
    }

    /**
     * 1: 食物
     * 2: 水
     * 3: 洗澡
     * 4: 床
     * 5: 跑步机
     * 6: 讲台
     * 7: 门
     * 8: 马桶
     * 9: 椅子（包括马桶）
     * 10: 音符盒
     */
    public static HashMap<BlockPos, Integer> taskBlocks = new HashMap<>();
    public static int scanTaskPointsCountDown = -1;
    public static String myRoomNumber = null;

    // ==================== 客户端状态 ====================
    // 当前选中的目标玩家（用于需要选择目标的技能）
    public static Player targetPlayer;
    public static PlayerBodyEntity targetBody;

    @Override
    public void onInitializeClient() {
        NoellesrolesClientAmbientSounds.register();
        // Dream（梦魇）：颤抖视角漂移 + 虚拟血量条（准星指向受伤玩家时显示）
        org.agmas.noellesroles.game.roles.killer.dream.client.DreamClientHandler.register();
        // 区域地图物品：客户端扫描 playArea 生成地图纹理 + 手持时 HUD 小地图
        org.agmas.noellesroles.client.map.AreaMapManager.register();
        org.agmas.noellesroles.client.map.AreaMapHud.register();
        // 阿蒙终幕「阿蒙时刻」：全屏稍偏灰滤镜。
        org.agmas.noellesroles.client.event.CommonHudRenderCallback.EVENT.register((g, dt) -> {
            if (org.agmas.noellesroles.client.ClientAmonState.finaleActive) {
                g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x33707078);
            }
        });
        // 注册游戏结束事件，清除建筑师客户端墙
        {
            NewspaperItem.runner = (stack, hand) -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (!hand.equals(InteractionHand.MAIN_HAND))
                    return false;
                minecraft.setScreen(new NewspaperScreen(stack, hand));
                return true;
            };
        }
        // 信使信封 GUI 回调
        {
            CourierMailItem.openSendScreen = (player, hand) -> {
                Minecraft minecraft = Minecraft.getInstance();
                // 只有信使可以打开送信页面
                if (SREClient.gameComponent != null && minecraft.level != null
                        && !SREClient.gameComponent.isRole(player, ModRoles.COURIER)) {
                    return;
                }
                minecraft.setScreen(new CourierScreen(hand));
            };
            CourierMailItem.openReceiveScreen = (player, hand) -> {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.setScreen(new CourierMailReceiveScreen(hand));
            };
        }
        io.wifi.starrailexpress.event.client.OnGameFinishedClient.EVENT.register(() -> {
            ClientWallManager.clearAll();
            // 关闭推理师罗盘界面
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof ReasonerCompassScreen) {
                minecraft.screen.onClose();
            }
        });
        // 注册HUD渲染
        LimitedInventoryScreen.NotAllowItemTakePredicates.add(stack -> stack.is(ModItems.BOMB));

        BlockEntityRenderers.register(
                ModSceneBlocks.BREAKING_BRIDGE_ENTITY,
                BreakingBridgeBlockEntityRenderer::new);
        BlockEntityRenderers.register(
                ModBlocks.VENDING_MACHINES_BLOCK_ENTITY,
                VendingMachinesBlockEntityRenderer::new);
        BlockEntityRenderers.register(
                ModBlocks.HUNTER_CAGE_BLOCK_ENTITY,
                HunterCageBlockEntityRenderer::new);

        BlockEntityRenderers.register(SREFumoBlocks.PLUSH_BLOCK_ENTITY, SREPlushBlockEntityRenderer::new);

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.VENDING_MACHINES_BLOCK, RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.LOTTERY_MACHINE_BLOCK, RenderType.translucent());
        // 场景方块：有毒区域 / 迷雾区域 半透明
        BlockRenderLayerMap.INSTANCE.putBlock(
                org.agmas.noellesroles.init.ModSceneBlocks.POISON_ZONE, RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(
                org.agmas.noellesroles.init.ModSceneBlocks.FOG_ZONE, RenderType.translucent());
        // 灌木（树叶贴图）使用 cutout 渲染层
        BlockRenderLayerMap.INSTANCE.putBlock(
                org.agmas.noellesroles.init.ModSceneBlocks.BUSH, RenderType.cutoutMipped());
        // 水阀（镶板贴图，透明背景）使用 cutout 渲染层
        BlockRenderLayerMap.INSTANCE.putBlock(
                org.agmas.noellesroles.init.ModSceneBlocks.WATER_VALVE, RenderType.cutout());

        // 注册C4背部渲染
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) -> {
                    if (entityRenderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                        registrationHelper.register(new C4BackFeatureRenderer(pr));
                    }
                });

        MercenaryContractItem.openGuiRunner = () -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null)
                return;
            client.execute(() -> {
                client.setScreen(new MercenaryContractScreen());
            });
        };
        ProblemSetItem.openScreenCallback = () -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null)
                return;
            client.execute(() -> {
                client.setScreen(new MathSolverScreen());
            });
        };
        PanItem.openScreenCallback = () -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null)
                return;
            client.setScreen(new ChefStartGameScreen());
        };
        // 场景方块客户端屏幕回调（避免服务端加载 Screen 类导致崩溃）
        org.agmas.noellesroles.content.block.scene.ReactorBlock.openReactorScreenCallback = (pos) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null)
                return;
            mc.setScreen(new io.wifi.starrailexpress.client.gui.screen.SimpleQuestMinigameScreen(pos,
                    () -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new org.agmas.noellesroles.packet.ReactorMinigameCompleteC2SPacket(pos)),
                    io.wifi.starrailexpress.client.gui.screen.SimpleQuestMinigameScreen.Mode.REACTOR_TEMPERATURE));
        };
        org.agmas.noellesroles.content.block.scene.WaterValveBlock.openWaterValveScreenCallback = (pos) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null)
                return;
            mc.setScreen(new io.wifi.starrailexpress.client.gui.screen.SimpleQuestMinigameScreen(pos,
                    () -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new org.agmas.noellesroles.packet.WaterValveMinigameCompleteC2SPacket(pos)),
                    io.wifi.starrailexpress.client.gui.screen.SimpleQuestMinigameScreen.Mode.WATER_VALVE));
        };
        org.agmas.noellesroles.content.block.scene.DebrisPileBlock.openDebrisPileScreenCallback = (pos) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null)
                return;
            mc.setScreen(new io.wifi.starrailexpress.client.gui.screen.PhysicalQuestMinigameScreen(pos,
                    () -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new org.agmas.noellesroles.packet.DebrisPileMinigameCompleteC2SPacket(pos)),
                    io.wifi.starrailexpress.client.gui.screen.PhysicalQuestMinigameScreen.Kind.EXTINGUISH));
        };
        org.agmas.noellesroles.content.block.scene.MovingPlatformBlock.openMovingPlatformConfigCallback = (pos) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null)
                return;
            if (mc.level.getBlockEntity(
                    pos) instanceof org.agmas.noellesroles.content.block_entity.scene.MovingPlatformBlockEntity mbe) {
                mc.setScreen(new org.agmas.noellesroles.client.screen.MovingPlatformConfigScreen(
                        pos, mbe.getDistance(), mbe.getSpeed(), mbe.getCollisionSize()));
            }
        };
        org.agmas.noellesroles.content.block.scene.HurricaneDeviceBlock.openHurricaneDeviceConfigCallback = (pos) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null)
                return;
            if (mc.level.getBlockEntity(
                    pos) instanceof org.agmas.noellesroles.content.block_entity.scene.HurricaneDeviceBlockEntity hbe) {
                mc.setScreen(new org.agmas.noellesroles.client.screen.HurricaneDeviceConfigScreen(pos, hbe.getRadius(),
                        hbe.getHeight(), hbe.isPersistent(), hbe.getSpawnIntervalSeconds(), hbe.getDurationSeconds()));
            }
        };
        org.agmas.noellesroles.content.block.scene.TrashCanBlock.openTrashCanConfigCallback = (pos) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null)
                return;
            if (mc.level.getBlockEntity(
                    pos) instanceof org.agmas.noellesroles.content.block_entity.scene.TrashCanBlockEntity trashCan) {
                mc.setScreen(new org.agmas.noellesroles.client.screen.TrashCanConfigScreen(pos,
                        trashCan.isWhitelistEnabled(),
                        trashCan.getWhitelist(), trashCan.isBlacklistEnabled(), trashCan.getBlacklist()));
            }
        };
        EntityRendererRegistry.register(ModEntities.WHEELCHAIR, WheelchairEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.WHEELCHAIR_FIELD_ITEM, WheelchairFieldItemRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROLLING_STONE,
                org.agmas.noellesroles.client.render.RollingStoneRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROLLING_LOG,
                org.agmas.noellesroles.client.render.RollingLogRenderer::new);
        EntityRendererRegistry.register(ModEntities.PIGEON,
                org.agmas.noellesroles.client.render.PigeonRenderer::new);
        EntityRendererRegistry.register(ModEntities.MOVING_PLATFORM,
                org.agmas.noellesroles.client.render.MovingPlatformRenderer::new);
        EntityRendererRegistry.register(ModEntities.HURRICANE,
                org.agmas.noellesroles.client.render.EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.MUMMY,
                net.minecraft.client.renderer.entity.HuskRenderer::new);
        EntityRendererRegistry.register(ModEntities.UNDEAD,
                org.agmas.noellesroles.client.renderer.UndeadEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.MORPHLING_KNIFE_DUMMY,
                org.agmas.noellesroles.client.renderer.MorphlingKnifeDummyRenderer::new);
        EntityRendererRegistry.register(ModEntities.DOOMED_SINNER_BODY,
                context -> new io.wifi.starrailexpress.client.render.entity.PlayerBodyEntityRenderer<>(context, false));
        EntityRendererRegistry.register(ModEntities.SALTED_FISH_BODY,
                context -> new io.wifi.starrailexpress.client.render.entity.PlayerBodyEntityRenderer<>(context, false));
        EntityRendererRegistry.register(ModEntities.RAINBOW_HORSE,
                org.agmas.noellesroles.client.renderer.RainbowHorseRenderer::new);
        EntityRendererRegistry.register(ModEntities.CANYUESA_HORSE,
                org.agmas.noellesroles.client.renderer.CanyuesaHorseRenderer::new);
        EntityRendererRegistry.register(ModEntities.SUPER_PIG_HORSE,
                org.agmas.noellesroles.client.renderer.SuperPigHorseRenderer::new);

        EntityModelLayerRegistry.registerModelLayer(WheelchairEntityModel.LAYER_LOCATION,
                WheelchairEntityModel::createBodyLayer);
        AllowNameRender.EVENT.register((target) -> {
            SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                    .get(target.level());
            if (gameWorldComponent.isRole(target,
                    ModRoles.INSANE_KILLER)) {
                var insaneComponent = InsaneKillerPlayerComponent.KEY.get(target);
                if (insaneComponent != null) {
                    if (insaneComponent.isActive || insaneComponent.inNearDeath()) {
                        return false;
                    }
                }

            }
            return true;
        });
        ClientEmbalmerState.register();
        ClientSkincrawlerState.register();
        SaltedFishClientHandle.register();
        TwoDimensionalCameraClientHandle.register();
        PointerClientHandle.register();
        HakoniwaVisionClientHandle.register();
        org.agmas.noellesroles.client.ClientAmonState.register();
        CommonClientHudRenderer.registerRenderersEvent();
        WorldRenderEvents.AFTER_TRANSLUCENT.register((renderContext) -> {
            TaskBlockOverlayRenderer.render(renderContext);
            TwoDimensionalTaskArrowRenderer.render(renderContext);
            PointerGuidanceRenderer.render(renderContext);
        });
        InstinctRenderer.registerInstinctEvents();

        ClientPlayNetworking.registerGlobalReceiver(ReasonerOpenScreenS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> context.client().setScreen(new ReasonerCompassScreen(payload)));
        });

        ClientPlayNetworking.registerGlobalReceiver(DoomedSinnerFateRevealS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> context.client()
                    .setScreen(new DoomedSinnerFateScreen(payload)));
        });

        ClientPlayNetworking.registerGlobalReceiver(ShowCustomNewspaperPacket.ID, (payload, context) -> {
            context.client().setScreen(new NewspaperScreen(payload.pages(),
                    (payload.title().orElse(Component.literal(""))), (payload.author().orElse(Component.literal("")))));
        });
        ClientPlayNetworking.registerGlobalReceiver(CreateClientSmokeAreaPacket.ID, (payload, context) -> {
            ClientSmokeAreaManager.createSmokeArea(context.client().level, payload.position(), payload.radius(),
                    payload.durationTicks());
        });

        // 建筑师墙数据S2C包
        ClientPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.BuilderWallS2CPacket.ID,
                (payload, context) -> {
                    ClientWallManager.createWall(payload.wallId(), payload.brickPositions(), payload.cobwebPositions(),
                            payload.durationTicks());
                });
        ClientPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.BuilderRemoveWallS2CPacket.ID,
                (payload, context) -> {
                    ClientWallManager.removeWall(payload.wallId());
                });
        ClientPlayNetworking.registerGlobalReceiver(CreateCreeperBombAreaPacket.ID, (payload, context) -> {
            final var p = context.player();
            final var level = context.client().level;
            Vec3 pos = payload.position();
            double dist = p.distanceToSqr(pos);
            if (dist > 4096)
                return; // 64格距离限制

            for (int i = 0; i < 300; i++) {
                // 随机偏移位置
                double offsetX = (level.random.nextDouble() - 0.5) * 2;
                double offsetY = level.random.nextDouble() * 2;
                double offsetZ = (level.random.nextDouble() - 0.5) * 2;
                double x = pos.x + offsetX;
                double y = pos.y + offsetY;
                double z = pos.z + offsetZ;

                // 随机速度
                double speed = 0.3;
                double vx = (level.random.nextDouble() - 0.5) * speed;
                double vy = level.random.nextDouble() * speed;
                double vz = (level.random.nextDouble() - 0.5) * speed;

                float startHue = level.random.nextFloat(); // 0-1
                float endHue = (startHue + 0.3f) % 1.0f;
                java.awt.Color startRgb = new java.awt.Color(java.awt.Color.HSBtoRGB(startHue, 1.0f, 1.0f));
                java.awt.Color endRgb = new java.awt.Color(java.awt.Color.HSBtoRGB(endHue, 1.0f, 1.0f));

                Vector3f startColor = new Vector3f(
                        (startRgb.getRed() * 1f) / 255.0f,
                        (startRgb.getGreen() * 1f) / 255.0f,
                        (startRgb.getBlue() * 1f) / 255.0f);
                Vector3f endColor = new Vector3f(
                        (endRgb.getRed() * 1f) / 255.0f,
                        (endRgb.getGreen() * 1f) / 255.0f,
                        (endRgb.getBlue() * 1f) / 255.0f);
                // 创建并添加粒子
                level.addParticle(
                        new DustColorTransitionOptions(startColor, endColor, 1.0f),
                        true, x, y, z, vx, vy, vz);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(
                org.agmas.noellesroles.game.roles.innocence.fool.FoolOpenTarotVoteS2CPacket.ID,
                (payload, context) -> {
                    final var client = context.client();
                    client.execute(() -> {
                        if (client.player == null) {
                            return;
                        }
                        client.setScreen(new org.agmas.noellesroles.client.screen.FoolTarotVoteScreen(
                                payload.candidates(), payload.durationSeconds()));
                    });
                });
        ClientTickEvents.END_WORLD_TICK.register((level) -> {
            if (level == null)
                return;

            if (SREClient.gameComponent == null)
                return;
            ClientSmokeAreaManager.tick();
            ClientWallManager.tick();
        });
        ClientPlayNetworking.registerGlobalReceiver(ProblemScreenOpenC2SPacket.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                // 强制关闭当前打开的页面（如监控页面），再打开做题页面
                if (client.screen != null && !(client.screen instanceof MathSolverScreen)) {
                    client.screen.onClose();
                }
                client.setScreen(new MathSolverScreen(payload.forced(), payload.maxTrial()));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(ScanAllTaskPointsPayload.ID, (payload, context) -> {
            Noellesroles.LOGGER.info("Recieved Tasks Points!");
            NoellesrolesClient.taskBlocks.clear();
            var tempArr = payload.taskBlocks();
            TaskBlockOverlayRenderer.RoomDoorPositions.clear();
            for (var set : tempArr.entrySet()) {
                if (set.getValue() == 7) {
                    TaskBlockOverlayRenderer.RoomDoorPositions.add(set.getKey());
                } else {
                    NoellesrolesClient.taskBlocks.put(set.getKey(), set.getValue());
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(EnableTaskHighlightPacket.ID, (payload, context) -> {
            final var client = context.client();
            client.execute(() -> {
                if (payload.enable()) {
                    // 启用任务透视功能
                    if (!NoellesrolesClient.isTaskInstinctEnabled) {
                        NoellesrolesClient.isTaskInstinctEnabled = true;
                        if (client.player != null) {
                            client.player.displayClientMessage(
                                    net.minecraft.network.chat.Component
                                            .translatable("message.tip.taskpoint_instinct_enable")
                                            .withStyle(net.minecraft.ChatFormatting.GREEN),
                                    true);
                        }
                    }
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(BroadcastMessageS2CPacket.ID, (payload, context) -> {
            final var client = context.client();
            client.execute(() -> {
                if (client.player != null) {
                    // if (!isPlayerInAdventureMode(client.player))
                    // return;
                    ShowBroadcastMessage(payload.content());
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(VendingBuyMessageCallBackS2CPacket.ID, (payload, context) -> {
            final var client = context.client();
            client.execute(() -> {
                if (client.player != null) {
                    if (client.screen instanceof VendingMachinesGui vendingMachinesGui) {
                        vendingMachinesGui.addPurchaseMessage(payload.componentKey());
                    }
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenIntroPayload.ID, (payload, context) -> {
            final var client = context.client();
            client.execute(() -> {
                if (client.player != null) {
                    boolean isIntroItem = client.player.getMainHandItem().getItem() == ModItems.LETTER_ITEM
                            || client.player.getMainHandItem()
                                    .has(io.wifi.starrailexpress.index.SREDataComponentTypes.SPONSOR_INTRO);
                    if (isIntroItem && SREClient.gameComponent != null) {
                        SRERole role = SREClient.gameComponent.getRole(client.player);
                        if (role != null) {
                            client.setScreen(new RoleIntroduceScreen(client.player, role));
                        } else {
                            client.setScreen(new RoleIntroduceScreen(client.player));
                        }
                    } else {
                        client.setScreen(new RoleIntroduceScreen(client.player));
                    }
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.network.MapIntroSyncPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    if (context
                            .client().screen instanceof io.wifi.starrailexpress.client.gui.screen.MapIntroduceScreen screen) {
                        screen.updateFromPacket(payload);
                    } else if (context
                            .client().screen instanceof io.wifi.starrailexpress.client.gui.screen.maprotation.MapRotationScreen rotationScreen) {
                        // 地图轮换界面复用同一份地图介绍数据（Fabric 每个包 ID 只允许一个全局接收器）
                        rotationScreen.updateFromPacket(payload);
                    } else if (context
                            .client().screen instanceof io.wifi.starrailexpress.client.gui.screen.MapVoteScreen voteScreen) {
                        voteScreen.updateIntroFromPacket(payload);
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.network.MapRotationSyncPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    if (context
                            .client().screen instanceof io.wifi.starrailexpress.client.gui.screen.maprotation.MapRotationScreen rotationScreen) {
                        rotationScreen.applyRotationSync(payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(BreakArmorPayload.ID, (payload, context) -> {
            final var client = context.client();
            client.execute(() -> {
                if (client.player != null && client.level != null) {
                    // 屏幕效果
                    StaminaRenderer.triggerScreenEdgeEffect(Color.ORANGE.getRGB());

                    // 播放护盾破碎声音
                    client.player.displayClientMessage(
                            Component.translatable("message.bartender.armor_broke").withStyle(ChatFormatting.RED),
                            true);
                    client.level.playLocalSound(
                            payload.x(),
                            payload.y(),
                            payload.z(),
                            TMMSounds.ITEM_PSYCHO_ARMOUR,
                            SoundSource.MASTER,
                            1.0F,
                            1.0F,
                            false);
                    // // 处理准星效果
                    // CrosshairAddons.getStateManager().handleBreakPacket(payload.x(), payload.y(),
                    // payload.z());
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(PlayerResetS2CPacket.ID, (payload, context) -> {
            final var client = context.client();
            client.player.getActiveEffects().clear();
            client.execute(() -> {
                if (client.player != null) {
                    // client.player.sendSystemMessage(Component.translatable("screen.noellesroles.guess_role.reset")
                    // .withColor(Color.ORANGE.getRGB()));
                    GuessRoleScreen.clearData();
                    client.player.containerMenu.setCarried(ItemStack.EMPTY);
                    // 清除窃皮者皮肤映射，确保游戏结束时皮肤能正确还原
                    ClientSkincrawlerState.clearAll();
                    // 清除阿蒙伪装与终幕表现状态
                    org.agmas.noellesroles.client.ClientAmonState.clearAll();
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(BloodConfigS2CPacket.ID, (payload, context) -> {
            bloodMain.enabled = payload.enabled();
            LoggerFactory.getLogger(this.getClass())
                    .info("Blood Particle status: " + (bloodMain.enabled ? "Enabled" : "Disabled"));
        });
        ClientPlayNetworking.registerGlobalReceiver(ClearBloodParticlesS2CPacket.ID, (payload, context) -> {
            context.client().execute(
                    () -> BloodParticle.clearParticlesInRange(payload.x(), payload.y(), payload.z(), payload.range()));
        });
        ClientPlayNetworking.registerGlobalReceiver(NameTagSyncPayload.ID, (payload, context) -> {
            RoleNameRenderer.displayTags.putAll(payload.nametags());
        });
        ClientPlayNetworking.registerGlobalReceiver(RepairCoinRewardS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> RepairEscapeHud.pushCoinToast(payload.amount(), payload.sourceKey()));
        });
        ClientPlayNetworking.registerGlobalReceiver(RepairCombatFeedbackS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> RepairEscapeHud.pushCombatCue(payload.kind(), payload.entityId(),
                    payload.x(), payload.y(), payload.z(), payload.weaponId()));
        });
        ClientPlayNetworking.registerGlobalReceiver(MapStatusBarSyncS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> org.agmas.noellesroles.client.hud.MapStatusBarClientState
                    .set(payload.barType(), payload.value(), payload.maxValue()));
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenLockGuiS2CPacket.ID, (payload, context) -> {
            final var client = context.client();
            client.execute(() -> {
                if (client.player != null) {
                    if (!isPlayerInAdventureMode(client.player))
                        return;
                    BlockPos pos = payload.pos();
                    int x = pos.getX();
                    int lockLength = payload.lockLength();
                    int y = pos.getY();
                    int z = pos.getZ();
                    UUID entityId = payload.lockId();
                    AABB areas = new AABB(
                            x - 5, y - 5, z - 5,
                            x + 5, y + 5, z + 5);
                    var entities = Minecraft.getInstance().level.getEntities(client.player, areas, (entity) -> {
                        if (entity instanceof LockEntity) {
                            return true;
                        }
                        return false;
                    });
                    Entity lockEntity = null;
                    for (var entity : entities) {
                        if (entity.getUUID().equals(entityId)) {
                            lockEntity = entity;
                        }
                    }
                    if (lockEntity != null && lockEntity instanceof LockEntity lock) {
                        lock.setLength(lockLength);
                        Minecraft.getInstance()
                                .setScreen(new LockGameScreen(pos, lock));
                    }

                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenKeyForgeGuiS2CPacket.ID, (payload, context) -> {
            final var client = context.client();
            client.execute(() -> {
                if (client.player != null) {
                    if (!isPlayerInAdventureMode(client.player))
                        return;
                    client.setScreen(new KeyForgeGameScreen(payload.inspirationPoints()));
                }
            });
        });
        // 抽奖/抽卡功能已禁用 —— 以下所有 Loot/Lottery 网络包处理器均为空操作
        ClientPlayNetworking.registerGlobalReceiver(LootResultS2CPacket.ID, (payload, context) -> {
        });
        ClientPlayNetworking.registerGlobalReceiver(LootMultiResultS2CPacket.ID, (payload, context) -> {
        });
        ClientPlayNetworking.registerGlobalReceiver(LootPoolsInfoCheckS2CPacket.ID, (payload, context) -> {
        });
        ClientPlayNetworking.registerGlobalReceiver(LootPoolsInfoS2CPacket.ID, (payload, context) -> {
        });
        ClientPlayNetworking.registerGlobalReceiver(LootDataRefreshS2CPacket.ID, (payload, context) -> {
        });

        OnRoundStartWelcomeTimmer.EVENT.register((player, timer) -> {
            if (timer == 1) {
                if (SREClientConfig.HANDLER.instance().welcome_voice) {
                    player.level().playLocalSound(player, NRSounds.HARPY_WELCOME, SoundSource.AMBIENT, 1f, 1f);
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenRepairRoleSelectionS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context
                        .client().screen instanceof net.exmo.sre.repair.client.screen.RepairRoleSelectionScreen) {
                    return;
                }
                context.client().setScreen(new net.exmo.sre.repair.client.screen.RepairRoleSelectionScreen(
                        payload.faction(), payload.endTick(), payload.playerNames()));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenRepairRoleShopS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context
                        .client().screen instanceof net.exmo.sre.repair.client.screen.RepairRoleShopScreen screen) {
                    screen.updateData(payload.skinCoins(), payload.ownedRoles());
                    screen.init(context.client(), context.client().getWindow().getGuiScaledWidth(),
                            context.client().getWindow().getGuiScaledHeight());
                } else {
                    context.client().setScreen(new net.exmo.sre.repair.client.screen.RepairRoleShopScreen(
                            payload.skinCoins(), payload.ownedRoles()));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenRepairStationScreenS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(
                        new net.exmo.sre.repair.client.screen.RepairStationScreen(payload.blockPos()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenVendingMachinesScreenS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                BlockEntity blockEntity = context.client().level.getBlockEntity(payload.blockPos());
                if (blockEntity instanceof VendingMachinesBlockEntity vendingMachinesBlockEntity) {
                    context.client().setScreen(new VendingMachinesGui(vendingMachinesBlockEntity.getShops())
                            .setBlockPos(payload.blockPos()));
                }
            });

        });

        ClientPlayNetworking.registerGlobalReceiver(OpenSupplyCrateScreenS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                BlockEntity blockEntity = context.client().level.getBlockEntity(payload.blockPos());
                if (blockEntity instanceof SupplyCrateBlockEntity crate) {
                    context.client().setScreen(new SupplyCrateGui(payload.blockPos(), crate));
                }
            });
        });

        // 抽奖机：打开界面
        ClientPlayNetworking.registerGlobalReceiver(OpenLotteryMachineScreenS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                BlockEntity blockEntity = context.client().level.getBlockEntity(payload.blockPos());
                if (blockEntity instanceof LotteryMachineBlockEntity lottery) {
                    context.client().setScreen(new LotteryMachineGui(
                            payload.blockPos(), lottery.getShops(), lottery.getDrawCost(), lottery.getDrawCurrency()));
                }
            });
        });
        // 抽奖机：接收抽奖结果
        ClientPlayNetworking.registerGlobalReceiver(LotteryMachineResultS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof LotteryMachineGui gui) {
                    gui.handleResult(payload);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ToggleInsaneSkillC2SPacket.ID, (payload, context) -> {
            if (payload.toggle()) {
                Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
            } else {
                var abstractClientPlayer = Minecraft.getInstance().player;
                var clientLevel = Minecraft.getInstance().level;
                Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
                if (isPlayerBodyEntity.getOrDefault(abstractClientPlayer.getUUID(), false)) {
                    // if (abstractClientPlayer == Minecraft.getInstance().player) {
                    // Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
                    // }
                    isPlayerBodyEntity.put(abstractClientPlayer.getUUID(), false);
                    if (playerBodyEntities.containsKey(abstractClientPlayer.getUUID())) {
                        clientLevel.removeEntity(playerBodyEntities.get(abstractClientPlayer.getUUID()).getId(),
                                Entity.RemovalReason.DISCARDED);
                        playerBodyEntities.remove(abstractClientPlayer.getUUID());

                    }
                }
            }

        });
        ClientPlayNetworking.registerGlobalReceiver(CanMoveInTimeStopS2CPacket.ID, (payload, context) -> {
            clientPositions.clear();
            LocalPlayer player = context.player();
            Level level = player.level();
            TimeStopEffect.freezeStatedTime = SREGameTimeComponent.KEY.get(level).time;
            TimeStopEffect.freezeMaxTime = payload.times();
            lastTimeStopRenderPlayer.clear();
            ClientLevel clientLevel = Minecraft.getInstance().level;
            if (clientLevel != null) {
                clientLevel.players().forEach(p -> {
                    RemotePlayer value = new RemotePlayer(clientLevel, p.getGameProfile());
                    value.setPos(p.position());
                    value.setYRot(p.getYRot());
                    value.setXRot(p.getXRot());
                    value.setYBodyRot(p.yBodyRot);
                    value.setYHeadRot(p.getYHeadRot());

                    value.setItemInHand(InteractionHand.MAIN_HAND, p.getItemInHand(InteractionHand.MAIN_HAND));
                    value.setPose(p.getPose());

                    lastTimeStopRenderPlayer.put(p.getUUID(), value);
                    clientPositions.put(p.getUUID(), p.position());
                });
            }
            player.stopUsingItem();
            TimeStopEffect.effectStatedTime = payload.times();

            TimeStopEffect.canMovePlayers.clear();
            TimeStopEffect.canMovePlayers.addAll(payload.uuids());
        });

        // 注册打开物品展示 ui网络包处理
        ClientPlayNetworking.registerGlobalReceiver(DisplayItemS2CPacket.ID, (payload, context) -> {
            final var client = context.client();
            client.execute(() -> {
                if (client.player != null && !payload.itemStack().isEmpty()) {
                    client.setScreen(new DisplayItemScreen(payload.itemStack()));
                }
            });
        });

        // 注册赌徒 1% 奇迹特效包：服务端发包，客户端本地渲染音效和粒子
        ClientPlayNetworking.registerGlobalReceiver(
                org.agmas.noellesroles.packet.GamblerMiracleS2CPacket.ID, (payload, context) -> {
                    final var client = context.client();
                    client.execute(() -> {
                        net.minecraft.client.multiplayer.ClientLevel level = client.level;
                        net.minecraft.client.player.LocalPlayer player = client.player;
                        if (level == null || player == null)
                            return;

                        net.minecraft.world.phys.Vec3 pos = payload.victimPos();
                        net.minecraft.util.RandomSource rng = level.getRandom();

                        // 1. 音效（客户端本地播放，不再由服务端逐个发包）
                        level.playLocalSound(pos.x(), pos.y(), pos.z(),
                                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                                SoundSource.PLAYERS, 2.0F, 1.4F, false);
                        level.playLocalSound(pos.x(), pos.y(), pos.z(),
                                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_DEATH,
                                SoundSource.PLAYERS, 1.5F, 0.8F, false);
                        level.playLocalSound(pos.x(), pos.y(), pos.z(),
                                net.minecraft.sounds.SoundEvents.WITHER_DEATH,
                                SoundSource.PLAYERS, 1.5F, 0.9F, false);
                        level.playLocalSound(pos.x(), pos.y(), pos.z(),
                                net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                                SoundSource.PLAYERS, 1.5F, 0.7F, false);
                        level.playLocalSound(pos.x(), pos.y(), pos.z(),
                                org.agmas.noellesroles.init.NRSounds.GAMBER_DEATH,
                                SoundSource.PLAYERS, 1.0F, 0.5F, false);

                        // 2. 大规模粒子爆发 - 多种粒子混合
                        for (int i = 0; i < 100; i++) {
                            double ox = (rng.nextDouble() - 0.5) * 20;
                            double oy = rng.nextDouble() * 15;
                            double oz = (rng.nextDouble() - 0.5) * 20;
                            level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,
                                    pos.x() + ox, pos.y() + oy, pos.z() + oz, 0.1, 0.1, 0.05);
                            level.addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                                    pos.x() + ox, pos.y() + oy, pos.z() + oz, 0.1, 0.1, 0.05);
                            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                                    pos.x() + ox, pos.y() + oy, pos.z() + oz, 0.05, 0.05, 0.03);
                            if (i % 3 == 0) {
                                level.addParticle(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH,
                                        pos.x() + ox, pos.y() + oy, pos.z() + oz, 0.05, 0.05, 0.02);
                            }
                            if (i % 2 == 0) {
                                level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                                        pos.x() + ox, pos.y() + oy, pos.z() + oz, 0.1, 0.1, 0.08);
                            }
                            if (i % 4 == 0) {
                                level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA,
                                        pos.x() + ox, pos.y() + oy, pos.z() + oz, 0.05, 0.05, 0.03);
                            }
                            if (i % 5 == 0) {
                                level.addParticle(net.minecraft.core.particles.ParticleTypes.SOUL,
                                        pos.x() + ox, pos.y() + oy, pos.z() + oz, 0.05, 0.05, 0.02);
                            }
                            if (i % 6 == 0) {
                                level.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL,
                                        pos.x() + ox, pos.y() + oy, pos.z() + oz, 0.05, 0.05, 0.01);
                            }
                        }

                        // 3. 冲击波环状扩散效果（多层）
                        for (int ring = 0; ring < 5; ring++) {
                            double radius = 3.0 + ring * 3;
                            int count = 40 + ring * 15;
                            for (int i = 0; i < count; i++) {
                                double angle = (2 * Math.PI * i) / count;
                                double px = pos.x() + Math.cos(angle) * radius;
                                double pz = pos.z() + Math.sin(angle) * radius;
                                level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,
                                        px, pos.y() + 0.5, pz, 0, 0.05, 0);
                                level.addParticle(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                                        px, pos.y() + 0.3, pz, 0, 0, 0);
                                level.addParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                                        px, pos.y() + 0.1, pz, 0, 0, 0);
                            }
                        }

                        // 4. 彩色光尘螺旋上升效果
                        for (int i = 0; i < 100; i++) {
                            double angle = (i / 100.0) * Math.PI * 8;
                            double height = (i / 100.0) * 15;
                            double r = 0.5 + (i / 100.0) * 5;
                            double px = pos.x() + Math.cos(angle) * r;
                            double pz = pos.z() + Math.sin(angle) * r;
                            level.addParticle(net.minecraft.core.particles.ParticleTypes.GLOW_SQUID_INK,
                                    px, pos.y() + height, pz, 0.05, 0.05, 0.01);
                            level.addParticle(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                                    px, pos.y() + height, pz, 0.05, 0.05, 0.01);
                            level.addParticle(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                                    px, pos.y() + height, pz, 0.03, 0.03, 0.005);
                        }

                        // 5. 地面震动效果（方块粒子）
                        for (int dx = -3; dx <= 3; dx++) {
                            for (int dz = -3; dz <= 3; dz++) {
                                if (Math.abs(dx) + Math.abs(dz) <= 4) {
                                    net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(
                                            pos.x() + dx, pos.y() - 1, pos.z() + dz);
                                    net.minecraft.world.level.block.state.BlockState bs = level.getBlockState(bp);
                                    level.addParticle(
                                            new net.minecraft.core.particles.BlockParticleOption(
                                                    net.minecraft.core.particles.ParticleTypes.BLOCK, bs),
                                            pos.x() + dx + 0.5, pos.y() - 0.5, pos.z() + dz + 0.5,
                                            0.1, 0.05, 0.1);
                                }
                            }
                        }

                        // 6. 向上喷射流
                        for (int i = 0; i < 50; i++) {
                            double angle = (i / 50.0) * Math.PI * 2;
                            double r = 1.0 + (i / 50.0) * 2;
                            double px = pos.x() + Math.cos(angle) * r;
                            double pz = pos.z() + Math.sin(angle) * r;
                            for (int h = 0; h < 10; h++) {
                                level.addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                        px, pos.y() + h * 0.8, pz, 0.05, 0.05, 0.01);
                            }
                        }
                    });
                });

        AgentListenStepHandler.registerEvents();
        InvisbleHandItem.register();
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> {
            // 加入游戏清空信息
            currentBroadcastMessage.clear();
            ClientVoteCache.clear();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((a, client) -> {
            // 加入游戏清空信息
            currentBroadcastMessage.clear();
            ClientVoteCache.clear();
            RoleManageConfigUI.RoleEnableStatus.clear();
            RoleManageConfigUI.ModifierEnableStatus.clear();
            ClientRoleRosterCache.clear();
            VolunteerCache.clear();
            RoleRotationCache.clear();
            ClientEmbalmerState.clear();
            ClientAmonState.clearAll();
            ClientSkincrawlerState.clearAll();
            // 在断开连接时，强制清理所有玩家的渲染缓存
            
        });
        // 监听客户端断开连接：清空卡池配置信息
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LotteryManager.getInstance().clearPools();
        });
        //
        ClientTickEvents.END_WORLD_TICK.register((client) -> {
            ClientVoteCache.clientTick();

            if (SREClient.gameComponent == null)
                return;
            if (!hasInitStatusBar) {
                hasInitStatusBar = true;
                StatusInit.statusBars.put("AmonFinale", new StatusInit.StatusBar("AmonFinale",
                        Component.translatable("hud.noellesroles.amon.finale_bar").getString(),
                        () -> org.agmas.noellesroles.client.ClientAmonState.finaleProgress()));
                StatusInit.statusBars.put("Time_Stop", new StatusInit.StatusBar("Time_Stop",
                        Component.translatable("mob_effect.noellesroles.time_stop").getString(), () -> {
                            LocalPlayer player = Minecraft.getInstance().player;
                            if (player != null) {
                                if (player.getEffect(ModEffects.TIME_STOP) != null) {
                                    return 1f
                                            - (player.getEffect(ModEffects.TIME_STOP).getDuration()
                                                    / (float) TimeStopEffect.freezeMaxTime);

                                }
                            }
                            return 1f;
                        }));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || SREClient.gameComponent == null)
                return;
            if (client.level != null) {
                client.level.players().forEach(
                        player -> {
                            if (client.player.hasEffect((ModEffects.TIME_STOP))) {
                                if (clientPositions.containsKey(player.getUUID())
                                        && !TimeStopEffect.canMovePlayers.contains(player.getUUID())) {
                                    player.setPos(clientPositions.get(player.getUUID()));
                                }
                            }
                        });
            }
        });
        // 操纵师附身：相机绑定到目标 + 远程驱动目标移动
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null || SREClient.gameComponent == null)
                return;
            org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent manipulatorComp = org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent.KEY
                    .get(client.player);
            if (manipulatorComp.isControlling && manipulatorComp.target != null) {
                net.minecraft.world.entity.player.Player target = client.level.getPlayerByUUID(manipulatorComp.target);
                if (target != null) {
                    if (client.getCameraEntity() != target) {
                        client.setCameraEntity(target);
                        manipulatorCameraBound = true;
                    }
                    // 读取移动键（已经过 ModEffects 按键拦截）→ 发送驱动包
                    int bits = 0;
                    if (client.options.keyUp.isDown())
                        bits |= org.agmas.noellesroles.packet.ManipulatorControlInputC2SPacket.BIT_FORWARD;
                    if (client.options.keyDown.isDown())
                        bits |= org.agmas.noellesroles.packet.ManipulatorControlInputC2SPacket.BIT_BACK;
                    if (client.options.keyLeft.isDown())
                        bits |= org.agmas.noellesroles.packet.ManipulatorControlInputC2SPacket.BIT_LEFT;
                    if (client.options.keyRight.isDown())
                        bits |= org.agmas.noellesroles.packet.ManipulatorControlInputC2SPacket.BIT_RIGHT;
                    if (client.options.keyJump.isDown())
                        bits |= org.agmas.noellesroles.packet.ManipulatorControlInputC2SPacket.BIT_JUMP;
                    if (client.options.keySprint.isDown())
                        bits |= org.agmas.noellesroles.packet.ManipulatorControlInputC2SPacket.BIT_SPRINT;
                    if (client.options.keyUse.isDown())
                        bits |= org.agmas.noellesroles.packet.ManipulatorControlInputC2SPacket.BIT_USE;
                    ClientPlayNetworking.send(new org.agmas.noellesroles.packet.ManipulatorControlInputC2SPacket(
                            bits, client.player.getYRot(), client.player.getXRot(), false));
                }
            } else if (manipulatorCameraBound) {
                // 操控结束：解除相机绑定
                if (client.getCameraEntity() != client.player) {
                    client.setCameraEntity(client.player);
                }
                manipulatorCameraBound = false;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (taskInstinctBind.consumeClick()) {
                isTaskInstinctEnabled = !isTaskInstinctEnabled;
                if (isTaskInstinctEnabled) {
                    client.player.displayClientMessage(Component.translatable("message.tip.taskpoint_instinct_enable")
                            .withStyle(ChatFormatting.GREEN), true);
                } else {
                    client.player.displayClientMessage(Component.translatable("message.tip.taskpoint_instinct_disable")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            if (showHelpDisplay.consumeClick()) {
                showHelpDisplayClicked(client);
            }
            if (client == null || client.player == null)
                return;

            // jeb_ modifier: refresh only jeb_ players' skin targets every 5 seconds.
            jebShuffleTime++;
            if (jebShuffleTime >= JEB_SHUFFLE_INTERVAL_TICKS || JEB_SHUFFLED_PLAYER_ENTRIES_CACHE.isEmpty()) {
                jebShuffleTime = 0;
                refreshJebShuffledCache(client.player);
            }

            if (roleGuessNoteClientBind.consumeClick()) {
                client.execute(() -> {
                    client.setScreen(new GuessRoleScreen());
                });
            }
            if (roleIntroClientBind.consumeClick()) {
                client.execute(() -> {
                    client.setScreen(new RoleIntroduceScreen(client.player));
                });
            }
            if (mapIntroClientBind.consumeClick()) {
                client.execute(() -> {
                    io.wifi.starrailexpress.client.gui.screen.MapIntroduceScreen screen = new io.wifi.starrailexpress.client.gui.screen.MapIntroduceScreen(
                            (Screen) null);
                    client.setScreen(screen);
                    ClientPlayNetworking.send(new io.wifi.starrailexpress.network.MapIntroRequestPayload());
                });
            }
            boolean abilityPressed = abilityBind.consumeClick();
            if (nextAbilityBind.consumeClick()) {
                ClientAbilityHandler.selectNextSkill(client);
            }
            ClientAbilityHandler.tickContinuousInput(client);
            var repairInputComponent = org.agmas.noellesroles.component.ModComponents.REPAIR_ROLES.get(client.player);
            boolean repairGameRunning = SREClient.gameComponent != null
                    && SREClient.gameComponent.isRunning()
                    && SREClient.gameComponent
                            .getGameMode() == io.wifi.starrailexpress.api.SREGameModes.REPAIR_ESCAPE_MODE;
            if (client.screen == null && repairGameRunning && repairInputComponent.carriedBy != null) {
                if (client.options.keyAttack.consumeClick()) {
                    ClientPlayNetworking.send(new net.exmo.sre.repair.network.RepairCarryStruggleC2SPacket("left"));
                }
                if (client.options.keyUse.consumeClick()) {
                    ClientPlayNetworking.send(new net.exmo.sre.repair.network.RepairCarryStruggleC2SPacket("right"));
                }
            }
            if (client.screen == null && repairGameRunning && repairInputComponent.downed
                    && repairInputComponent.carriedBy == null
                    && client.options.keyShift.consumeClick()) {
                ClientPlayNetworking.send(new net.exmo.sre.repair.network.RepairCarryStruggleC2SPacket("downed"));
            }
            handleRepairSearchInput(client);
            if (client.player.isCreative()) {
                if (foolPrayerBind.consumeClick()) {
                    ClientPlayNetworking
                            .send(new org.agmas.noellesroles.game.roles.innocence.fool.FoolPrayerC2SPacket());
                }
                if (abilityPressed) {
                    if (SREClient.gameComponent.isRole(client.player, ModRoles.ATTENDANT)) {
                        ClientPlayNetworking.send(new AbilityC2SPacket());
                    }
                }
                return;
            }

            boolean inTarotAssembly = client.player.hasEffect(ModEffects.TAROT_ASSEMBLY);
            // if (client.screen instanceof
            // org.agmas.noellesroles.client.screen.FoolTarotVoteScreen
            // && (!foolComponent.inMeeting || !foolComponent.voteInProgress)) {
            // client.setScreen(null);
            // }

            if (foolPrayerBind.consumeClick()) {
                ClientPlayNetworking.send(new org.agmas.noellesroles.game.roles.innocence.fool.FoolPrayerC2SPacket());
            }

            if (abilityPressed) {
                ClientAbilityHandler.handler(client);
            }

            if (inTarotAssembly) {
                if (client.options.keyUse.consumeClick()) {
                    ClientPlayNetworking
                            .send(new org.agmas.noellesroles.game.roles.innocence.fool.FoolLeaveMeetingC2SPacket());
                }

                boolean pauseOpen = client.screen instanceof net.minecraft.client.gui.screens.PauseScreen;
                if (pauseOpen && !foolMeetingPauseHandled) {
                    if (SREClient.gameComponent.isRole(client.player, ModRoles.THE_FOOL)) {
                        foolMeetingPauseHandled = true;
                    } else {
                        ClientPlayNetworking
                                .send(new org.agmas.noellesroles.game.roles.innocence.fool.FoolLeaveMeetingC2SPacket());
                        client.setScreen(null);
                    }
                }
                if (!pauseOpen) {
                    foolMeetingPauseHandled = false;
                }
            } else {
                foolMeetingPauseHandled = false;
            }

            if (!isPlayerInAdventureMode(client.player))
                return;
            insanityTime++;
            if (insanityTime >= 20 * 6) {
                insanityTime = 0;
                List<UUID> keys = new ArrayList<UUID>(ClientSkinCache.PLAYER_ENTRIES_CACHE.keySet());
                List<UUID> originalkeys = new ArrayList<UUID>(ClientSkinCache.PLAYER_ENTRIES_CACHE.keySet());
                Collections.shuffle(keys);
                int i = 0;
                for (UUID o : originalkeys) {
                    SHUFFLED_PLAYER_ENTRIES_CACHE.put(o, keys.get(i));
                    i++;
                }
            }

            handleStalkerContinuousInput(client);

        });

        // 注册里世界场景管理器tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null)
                return;
            if (SREClient.gameComponent == null)
                return;

            boolean otherworldActive = OtherworldShader.isAnyOtherworldActive();

            if (otherworldActive && !OtherworldSceneManager.INSTANCE.isActive()) {
                OtherworldSceneManager.INSTANCE.activate();
            } else if (!otherworldActive && OtherworldSceneManager.INSTANCE.isActive()) {
                OtherworldSceneManager.INSTANCE.deactivate();
            }

            OtherworldSceneManager.INSTANCE.tick();

            // 鬼缚效果红色粒子渲染
            for (var p : client.level.players()) {
                if (p.hasEffect(ModEffects.GHOST_CURSE)) {
                    double px = p.getX() + (Math.random() - 0.5) * 0.8;
                    double py = p.getY() + Math.random() * 1.8;
                    double pz = p.getZ() + (Math.random() - 0.5) * 0.8;
                    client.level.addParticle(
                            net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE,
                            px, py, pz, 0, 0.02, 0);
                    if (Math.random() < 0.4) {
                        double px2 = p.getX() + (Math.random() - 0.5) * 1.0;
                        double py2 = p.getY() + 0.5 + Math.random() * 1.0;
                        double pz2 = p.getZ() + (Math.random() - 0.5) * 1.0;
                        client.level.addParticle(
                                net.minecraft.core.particles.ParticleTypes.DUST_PLUME,
                                px2, py2, pz2, 0, -0.01, 0);
                    }
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null || SREClient.gameComponent == null) {
                MonokumaSceneManager.INSTANCE.forceRestore();
                return;
            }

            boolean monokumaActive = client.player.hasEffect(ModEffects.MONOKUMA_FRENZY);
            if (monokumaActive && !MonokumaSceneManager.INSTANCE.isActive()) {
                MonokumaSceneManager.INSTANCE.activate();
            } else if (!monokumaActive && MonokumaSceneManager.INSTANCE.isActive()) {
                MonokumaSceneManager.INSTANCE.deactivate();
            }

            MonokumaSceneManager.INSTANCE.tick();
        });

        ItemTooltipCallback.EVENT.register(((itemStack, tooltipContext, tooltipType, list) -> {
            tooltipHelper(TMMItems.DEFENSE_VIAL, itemStack, list);
            tooltipHelper(ModItems.DELUSION_VIAL, itemStack, list);
            tooltipHelper(ModItems.ONCE_REVOLVER, itemStack, list);
            tooltipHelper(FunnyItems.PROBLEM_SET, itemStack, list);
            tooltipHelper(FunnyItems.SHISIYE, itemStack, list);
            tooltipHelper(FunnyItems.BOWEN_BADGE, itemStack, list);
            tooltipHelper(ModItems.SIGNATURE_PAPER, itemStack, list);
            tooltipHelper(ModItems.REINFORCEMENT, itemStack, list);
            tooltipHelper(ModItems.SCREWDRIVER, itemStack, list);
            tooltipHelper(ModItems.LIFE_AND_DEATH_SHAPE, itemStack, list);
            tooltipHelper(ModItems.SIGNED_PAPER, itemStack, list);
            tooltipHelper(ModItems.MERCENARY_CONTRACT, itemStack, list);
            tooltipHelper(ModItems.THROWING_KNIFE, itemStack, list);
            tooltipHelper(ModItems.THROWING_AXE, itemStack, list);
        }));
        // registerKeyBindings();

        // 2. 注册客户端事件
        registerClientEvents();

        // 3. 注册物品提示（如果有自定义物品）
        // registerItemTooltips();

        // 4. 设置物品回调
        setupItemCallbacks();

        // 注册炸弹可见性属性
        net.minecraft.client.renderer.item.ItemProperties.register(ModItems.BOMB, Noellesroles.id("visible"),
                (stack, world, entity, seed) -> {
                    // 如果持有者是炸弹客，始终可见
                    if (entity instanceof Player player) {
                        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
                        if (gameWorldComponent.isRole(player, ModRoles.BOMBER)) {
                            return 1.0F;
                        }
                    }

                    @SuppressWarnings("unused")
                    net.minecraft.world.item.component.CustomData customData = stack.getOrDefault(
                            net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.EMPTY);
                    // 非炸弹客始终不可见
                    return 0.0F;
                });
        net.minecraft.client.renderer.item.ItemProperties.register(ModItems.YINYANG_SWORD, Noellesroles.id("charging"),
                (stack, world, entity, seed) -> {
                    if (!(entity instanceof Player player)) {
                        return 0.0F;
                    }
                    var component = org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaPlayerComponent.KEY
                            .maybeGet(player)
                            .orElse(null);
                    if (component == null) {
                        return 0.0F;
                    }
                    return component.aoeChargeTimer > 0 ? 1.0F : 0.0F;
                });
        net.minecraft.client.renderer.item.ItemProperties.register(ModItems.YINYANG_SWORD, Noellesroles.id("charge"),
                (stack, world, entity, seed) -> {
                    if (!(entity instanceof Player player)) {
                        return 0.0F;
                    }
                    var component = org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaPlayerComponent.KEY
                            .maybeGet(player)
                            .orElse(null);
                    if (component == null || component.aoeChargeTimer <= 0) {
                        return 0.0F;
                    }
                    return 1.0F - ((float) component.aoeChargeTimer
                            / org.agmas.noellesroles.game.roles.neutral.monokuma.YinYangSwordItem.CHARGE_TIME);
                });
        net.minecraft.client.renderer.item.ItemProperties.register(ModItems.YINYANG_SWORD, Noellesroles.id("dash"),
                (stack, world, entity, seed) -> {
                    if (!(entity instanceof Player player)) {
                        return 0.0F;
                    }
                    var component = org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaPlayerComponent.KEY
                            .maybeGet(player)
                            .orElse(null);
                    if (component == null || component.dashAnimTimer <= 0) {
                        return 0.0F;
                    }
                    return 1.0F;
                });
        // 当前游戏模式
        OnMessageBelowMoneyRenderer.EVENT.register((minecraft, guiGraphics, deltaTracker) -> {
            if (SREClient.gameComponent != null && minecraft != null && minecraft.player != null) {
                if (SREClient.gameComponent.isRunning() && SREClient.gameComponent.gameMode != null) {

                    return new MutableComponentResult(
                            Component
                                    .translatable("message.tip.game_mode", SREClient.gameComponent.gameMode.getName())
                                    .withStyle(ChatFormatting.WHITE));
                }
            }
            return null;
        });
        // 当前死亡惩罚
        OnMessageBelowMoneyRenderer.EVENT.register((minecraft, guiGraphics, deltaTracker) -> {
            if (SREClient.gameComponent != null && minecraft != null && minecraft.player != null) {
                if (SREClient.gameComponent.isRunning()) {
                    if (minecraft.player.isSpectator()) {
                        DeathPenaltyComponent dpcca = DeathPenaltyComponent.KEY.get(minecraft.player);
                        if (dpcca.hasPenalty()) {
                            if (dpcca.penaltyExpiry > 0) {
                                return new MutableComponentResult(
                                        Component
                                                .translatable("message.tip.death_penalty_with_timeout",
                                                        (dpcca.penaltyExpiry - minecraft.level.getGameTime()) / 20)
                                                .withStyle(ChatFormatting.YELLOW));
                            } else {
                                return new MutableComponentResult(
                                        Component
                                                .translatable("message.tip.death_penalty_inf")
                                                .withStyle(ChatFormatting.YELLOW));
                            }

                        }
                    }

                }
            }
            return null;
        });
        OnMessageBelowMoneyRenderer.EVENT.register((minecraft, guiGraphics, deltaTracker) -> {
            if (SREClient.gameComponent != null && minecraft != null && minecraft.player != null) {
                if (SREClient.gameComponent.isRunning()) {
                    if (GameUtils.isPlayerAliveAndSurvival(minecraft.player)) {
                        if (!SREClient.gameComponent.isSkillAvailable) {
                            //
                            return new MutableComponentResult(
                                    Component
                                            .translatable("message.tip.skill_disabled")
                                            .withStyle(ChatFormatting.RED));
                        }
                    }

                }
            }
            return null;
        });
        OnMessageBelowMoneyRenderer.EVENT.register((minecraft, guiGraphics, deltaTracker) -> {
            if (SREClient.gameComponent != null && !taskBlocks.isEmpty()) {
                if (SREClient.gameComponent.isRunning()) {
                    boolean canDisplay = false;
                    if (SREClient.isPlayerAliveAndInSurvival()) {
                        var playerMood = SREPlayerMoodComponent.KEY.get(Minecraft.getInstance().player);
                        if (playerMood != null) {
                            canDisplay = !playerMood.getTasks().isEmpty();
                        }
                    } else {
                        canDisplay = true;
                    }
                    if (canDisplay) {
                        return new MutableComponentResult(
                                Component
                                        .translatable("message.tip.for_taskpoint",
                                                Component.keybind("key.noellesroles.taskinstinct"))
                                        .withStyle(ChatFormatting.WHITE));
                    }
                    // is_taskpoint_able
                }
            }
            return null;
        });
        OnMessageBelowMoneyRenderer.EVENT.register((minecraft, guiGraphics, deltaTracker) -> {
            if (SREClient.gameComponent != null) {
                if (SREClient.gameComponent.isRunning()) {
                    var role = SREClient.gameComponent.getRole(minecraft.player);
                    if (role != null) {
                        if (role.canUseKiller()) {
                            return new MutableComponentResult(
                                    Component
                                            .translatable("message.tip.for_killer",
                                                    Component.keybind("key." + SRE.MOD_ID + ".instinct"))
                                            .withStyle(ChatFormatting.WHITE));
                        } else if (GameUtils.isPlayerEliminated(minecraft.player)) {
                            return new MutableComponentResult(
                                    Component
                                            .translatable("message.tip.for_killer",
                                                    Component.keybind("key." + SRE.MOD_ID + ".instinct"))
                                            .withStyle(ChatFormatting.WHITE));
                        }
                    }
                }
            }
            return null;
        });
        OnMessageBelowMoneyRenderer.EVENT.register((minecraft, guiGraphics, deltaTracker) -> {
            if (SREClient.gameComponent != null) {
                if (SREClient.gameComponent.isRunning()) {

                    return new MutableComponentResult(
                            Component
                                    .translatable("message.tip.voice_setting",
                                            Component.keybind("key.voice_chat"))
                                    .withStyle(ChatFormatting.WHITE));
                }
            }

            return null;
        });
        OnMessageBelowMoneyRenderer.EVENT.register((minecraft, guiGraphics, deltaTracker) -> {
            if (SREClient.gameComponent != null) {
                if (SREClient.gameComponent.isRunning()) {
                    if (minecraft.player != null && (minecraft.player.hasEffect(ModEffects.SKILL_BANED)
                            || minecraft.player.hasEffect(ModEffects.SKILL_FREEZED))) {

                        return new MutableComponentResult(
                                Component
                                        .translatable("message.tip.cant_use_skill")
                                        .withStyle(ChatFormatting.RED));
                    }
                }
            }

            return null;
        });

        OnMessageBelowMoneyRenderer.EVENT.register((minecraft, guiGraphics, deltaTracker) -> {
            if (SREClient.gameComponent != null && !taskBlocks.isEmpty()) {
                if (SREClient.gameComponent.isRunning()) {
                    boolean canDisplay = false;
                    canDisplay = NoellesrolesClient.isTaskInstinctEnabled;
                    if (canDisplay) {
                        return new MutableComponentResult(
                                Component
                                        .translatable("message.tip.is_taskpoint_able")
                                        .withStyle(ChatFormatting.AQUA));
                    }
                    //
                }
            }
            return null;
        });
        OnMessageBelowMoneyRenderer.EVENT.register((minecraft, guiGraphics, deltaTracker) -> {
            if (SREClient.gameComponent != null) {
                if (SREClient.gameComponent.isRunning()) {
                    if (!SREClient.isPlayerAliveAndInSurvival()) {
                        return new MutableComponentResult(
                                Component
                                        .translatable("message.tip.for_death_vt",
                                                Component.literal("/vt_mode").withStyle(ChatFormatting.GREEN))
                                        .withStyle(ChatFormatting.WHITE));
                    }
                    // is_taskpoint_able
                }
            }
            return null;
        });

        // 5. 注册实体渲染器
        registerEntityRenderers();

        // 6. 注册Screen
        registerScreens();

        // 7. 注册血粒子
        bloodMain.init();

        // 注册客户端命令
        registerCommands();
    }

    public static void showHelpDisplayClicked(Minecraft client) {
        isShowHelpDisplay = !isShowHelpDisplay;
        if (isShowHelpDisplay) {
            client.player.displayClientMessage(Component.translatable("message.tip.show_help_display_enable")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            client.player.displayClientMessage(Component.translatable("message.tip.show_help_display_disable")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private void registerCommands() {
        SREClientCommand.register();
    }

    private void ShowBroadcastMessage(Component message) {
        var client = Minecraft.getInstance();
        if (client == null)
            return;
        long timer = client.level.getGameTime();
        currentBroadcastMessage
                .add(new BroadcastMessageInfo(message, timer + GameConstants.getInTicks(0,
                        SREClientConfig.HANDLER.instance().broadcasterMessageDuration)));
    }

    public void tooltipHelper(Item item, ItemStack itemStack, List<Component> list) {
        if (itemStack.is(item)) {
            list.addAll(
                    TextUtils.getTooltipForItem(item, Style.EMPTY.withColor(TMMItemTooltips.REGULAR_TOOLTIP_COLOR)));
        }
    }

    public static boolean isPlayerInAdventureMode(AbstractClientPlayer targetPlayer) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            PlayerInfo entry = client.player.connection.getPlayerInfo(targetPlayer.getUUID());
            return entry != null && entry.getGameMode() == GameType.ADVENTURE;
        }
        return false;
    }

    private static void handleRepairSearchInput(Minecraft client) {
        if (client.player == null || client.level == null || client.screen != null) {
            return;
        }
        boolean repairGameRunning = SREClient.gameComponent != null
                && SREClient.gameComponent.isRunning()
                && SREClient.gameComponent.getGameMode() == io.wifi.starrailexpress.api.SREGameModes.REPAIR_ESCAPE_MODE;
        if (!repairGameRunning) {
            repairHeldSearchTarget = null;
            return;
        }
        if (!(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit)
                || !client.level.getBlockState(blockHit.getBlockPos()).is(ModBlocks.HOTBAR_STORAGE)
                || !client.options.keyUse.isDown()) {
            if (repairHeldSearchTarget != null) {
                ClientPlayNetworking.send(new RepairSearchCancelC2SPacket());
                repairHeldSearchTarget = null;
            }
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (!pos.equals(repairHeldSearchTarget)) {
            repairHeldSearchTarget = pos;
            ClientPlayNetworking.send(new RepairSearchBeginC2SPacket(pos));
        }
    }
}
