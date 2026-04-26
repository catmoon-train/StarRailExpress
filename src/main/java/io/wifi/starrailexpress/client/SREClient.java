package io.wifi.starrailexpress.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.doctor4t.ratatouille.client.util.OptionLocker;
import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import io.wifi.ConfigCompact.ClientConfigEvents;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomCameraDirector;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomClientState;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomTableHud;
import io.wifi.starrailexpress.client.gui.*;
import io.wifi.starrailexpress.client.gui.screen.*;
import io.wifi.starrailexpress.client.model.GeneralModelLoadingPlugin;
import io.wifi.starrailexpress.client.model.TMMModelLayers;
import io.wifi.starrailexpress.client.render.block_entity.FourthRoomTableBlockEntityRenderer;
import io.wifi.starrailexpress.client.render.block_entity.PlateBlockEntityRenderer;
import io.wifi.starrailexpress.client.render.block_entity.SmallDoorBlockEntityRenderer;
import io.wifi.starrailexpress.client.render.block_entity.WheelBlockEntityRenderer;
import io.wifi.starrailexpress.client.render.entity.FirecrackerEntityRenderer;
import io.wifi.starrailexpress.client.render.entity.HornBlockEntityRenderer;
import io.wifi.starrailexpress.client.render.entity.NoteEntityRenderer;
import io.wifi.starrailexpress.client.util.ClientScheduler;
import io.wifi.starrailexpress.client.util.MyBackgroundAmbience;
import io.wifi.starrailexpress.client.util.TMMItemTooltips;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.content.block.SecurityMonitorBlock;
import io.wifi.starrailexpress.content.entity.FirecrackerEntity;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.item.GrenadeItem;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.content.vote.client.VoteClientReceiver;
import io.wifi.starrailexpress.event.AllowItemShowInHand;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import io.wifi.starrailexpress.event.ClientHeldItemSwitchEvent;
import io.wifi.starrailexpress.event.OnGetInstinctHighlight;
import io.wifi.starrailexpress.event.client.OnGameFinishedClient;
import io.wifi.starrailexpress.event.client.OnGameStartedClient;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.index.*;
import io.wifi.starrailexpress.network.*;
import io.wifi.starrailexpress.network.original.*;
import io.wifi.starrailexpress.network.packet.SyncRoomToPlayerPayload;
import io.wifi.starrailexpress.network.packet.SyncSpecificWaypointVisibilityPacket;
import io.wifi.starrailexpress.network.packet.SyncWaypointVisibilityPacket;
import io.wifi.starrailexpress.network.packet.SyncWaypointsPacket;
import io.wifi.starrailexpress.util.HPManager;
import io.wifi.starrailexpress.util.MatrixParticleManager;
import io.wifi.starrailexpress.util.PoisonComponentUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.exmo.sre.EXSREClient;
import net.exmo.sre.loading.FrameAnimationRenderer;
import net.exmo.sre.mod_whitelist.client.ModWhitelistClient;
import net.exmo.sre.mod_whitelist.client.network.ModWhitelistClientNetworkHandler;
import net.exmo.sre.mod_whitelist.common.network.ModWhitelistConfigPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.game.modes.fourthroom.network.FourthRoomStatePayload;
import org.agmas.noellesroles.game.modes.fourthroom.network.FourthRoomTableEffectsPayload;
import org.agmas.noellesroles.game.modes.fourthroom.network.OpenFourthRoomPeekDeckPayload;
import org.agmas.noellesroles.game.roles.neutral.monokuma.YinYangSwordItem;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;
import org.spongepowered.include.com.google.gson.JsonSyntaxException;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class SREClient implements ClientModInitializer {
    private static float soundLevel = 0f;
    public static boolean hasCustomSkinLoaderAndNeedToWarn = false;
    public static HPManager handParticleManager;
    public static Map<Player, Vec3> particleMap;
    public static Map<UUID, Integer> cachedHighLightMap = new HashMap<>();
    private static boolean prevGameRunning;
    public static SREGameWorldComponent gameComponent;
    public static WorldModifierComponent modifierComponent;
    public static AreasWorldComponent areaComponent;
    public static SRETrainWorldComponent trainComponent;
    public static SREPlayerMoodComponent moodComponent;
    public static int intervalTime = 0;
    public static boolean isInLobby = false;
    public static Player cached_player = null;
    // HUD/API 缓存：在 END_CLIENT_TICK 统一更新，在渲染 mixin 中仅做读取，避免渲染流程重复判断。
    private static boolean cachedPlayerAliveAndInSurvival;
    private static boolean cachedPlayerSpectatingOrCreative;
    private static boolean cachedPlayerCreative;
    private static boolean cachedPlayerSpectator;
    private static boolean cachedKiller;
    private static boolean cachedUseTrainHud;
    private static boolean cachedCanRenderChatHud = true;
    private static boolean cachedShowDebugHud;
    private static boolean cachedRenderVanillaHud;
    private static boolean cachedLooseEndPenalty = false;
    private static SRERole cachedPlayerRole;
    public static boolean hideLocalMainHandItemInLayer = false;
    public static boolean hideLocalOffHandItemInLayer = false;
    public static final Map<UUID, PlayerInfo> PLAYER_ENTRIES_CACHE = new HashMap<>();
    public static final Map<UUID, Boolean> PLAYER_PSYCHO_CACHE = new ConcurrentHashMap<>();
    public static boolean localPlayerPsychoActive = false;
    private static ItemStack prevMainHandSnapshot = ItemStack.EMPTY;
    private static ItemStack prevOffHandSnapshot = ItemStack.EMPTY;
    private static int prevSelectedHotbarSlot = -1;

    public static KeyMapping instinctKeybind;
    public static KeyMapping statsKeybind; // 新增统计面板热键
    public static KeyMapping skinsKeybind; // 新增皮肤管理热键
    public static boolean isInstinctToggleEnabled = false; // 新增变量用于跟踪切换状态
    public static boolean prevInstinctKeyDown = false; // 用于检测按键按下事件
    public static float prevInstinctLightLevel = -.04f;
    public static float instinctLightLevel = -.04f;

    public static boolean shouldDisableHudAndDebug() {
        Minecraft client = Minecraft.getInstance();
        return (client == null
                || (client.player != null && !client.player.isCreative() && !client.player.isSpectator()));
    }

    public static boolean checkCustomSkinLoader() {
        final String customSkinLoaderClassName = "customskinloader.CustomSkinLoader";
        boolean result = isClassPresent(customSkinLoaderClassName);
        hasCustomSkinLoaderAndNeedToWarn = result;
        return result;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, SRE.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isPlayerCreative() {
        return cachedPlayerCreative;
    }

    @Override
    public void onInitializeClient() {
        ClientScheduler.init();

        ClientConfigEvents.register();
        new EXSREClient().onInitializeClient();
        // Load config
        ModWhitelistClient.onInitializeClient();
        // ModVersionPacket
        checkCustomSkinLoader();
        // Initialize ScreenParticle
        handParticleManager = new HPManager();
        particleMap = new HashMap<>();
        // Custom Baked Models
        ModelLoadingPlugin.register(new GeneralModelLoadingPlugin());
        // Register particle factories
        TMMParticles.registerFactories();

        // Entity renderer registration
        EntityRendererRegistry.register(TMMEntities.SEAT, NoopRenderer::new);
        EntityRendererRegistry.register(TMMEntities.FIRECRACKER, FirecrackerEntityRenderer::new);
        EntityRendererRegistry.register(TMMEntities.GRENADE, ThrownItemRenderer::new);
        EntityRendererRegistry.register(TMMEntities.NOTE, NoteEntityRenderer::new);

        // Register entity model layers
        TMMModelLayers.initialize();

        // Block render layers
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(),
                TMMBlocks.STAINLESS_STEEL_VENT_HATCH,
                TMMBlocks.DARK_STEEL_VENT_HATCH,
                TMMBlocks.TARNISHED_GOLD_VENT_HATCH,
                TMMBlocks.METAL_SHEET_WALKWAY,
                TMMBlocks.STAINLESS_STEEL_LADDER,
                TMMBlocks.COCKPIT_DOOR,
                TMMBlocks.METAL_SHEET_DOOR,
                TMMBlocks.GOLDEN_GLASS_PANEL,
                TMMBlocks.CULLING_GLASS,
                TMMBlocks.STAINLESS_STEEL_WALKWAY,
                TMMBlocks.DARK_STEEL_WALKWAY,
                TMMBlocks.PANEL_STRIPES,
                TMMBlocks.RAIL_BEAM,
                TMMBlocks.TRIMMED_RAILING_POST,
                TMMBlocks.DIAGONAL_TRIMMED_RAILING,
                TMMBlocks.TRIMMED_RAILING,
                TMMBlocks.TRIMMED_EBONY_STAIRS,
                TMMBlocks.WHITE_LOUNGE_COUCH,
                TMMBlocks.WHITE_OTTOMAN,
                TMMBlocks.WHITE_TRIMMED_BED,
                TMMBlocks.BLUE_LOUNGE_COUCH,
                TMMBlocks.GREEN_LOUNGE_COUCH,
                TMMBlocks.BAR_STOOL,
                TMMBlocks.WALL_LAMP,
                TMMBlocks.SMALL_BUTTON,
                TMMBlocks.ELEVATOR_BUTTON,
                TMMBlocks.STAINLESS_STEEL_SPRINKLER,
                TMMBlocks.GOLD_SPRINKLER,
                TMMBlocks.GOLD_ORNAMENT,
                TMMBlocks.WHEEL,
                TMMBlocks.RUSTED_WHEEL,
                TMMBlocks.BARRIER_PANEL,
                TMMBlocks.FOOD_PLATTER,
                TMMBlocks.DRINK_TRAY,
                TMMBlocks.LIGHT_BARRIER,
                TMMBlocks.HORN);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(),
                TMMBlocks.RHOMBUS_GLASS,
                TMMBlocks.PRIVACY_GLASS_PANEL,
                TMMBlocks.CULLING_BLACK_HULL,
                TMMBlocks.CULLING_WHITE_HULL,
                TMMBlocks.HULL_GLASS,
                TMMBlocks.RHOMBUS_HULL_GLASS);

        // Custom block models
        CustomModelProvider customModelProvider = new CustomModelProvider();
        ModelLoadingPlugin.register(customModelProvider);

        // Block Entity Renderers
        BlockEntityRenderers.register(
                TMMBlockEntities.SMALL_GLASS_DOOR,
                ctx -> new SmallDoorBlockEntityRenderer(SRE.watheId("textures/entity/small_glass_door.png"), ctx));
        BlockEntityRenderers.register(
                TMMBlockEntities.SMALL_WOOD_DOOR,
                ctx -> new SmallDoorBlockEntityRenderer(SRE.watheId("textures/entity/small_wood_door.png"), ctx));
        BlockEntityRenderers.register(
                TMMBlockEntities.ANTHRACITE_STEEL_DOOR,
                ctx -> new SmallDoorBlockEntityRenderer(SRE.watheId("textures/entity/anthracite_steel_door.png"), ctx));
        BlockEntityRenderers.register(
                TMMBlockEntities.KHAKI_STEEL_DOOR,
                ctx -> new SmallDoorBlockEntityRenderer(SRE.watheId("textures/entity/khaki_steel_door.png"), ctx));
        BlockEntityRenderers.register(
                TMMBlockEntities.MAROON_STEEL_DOOR,
                ctx -> new SmallDoorBlockEntityRenderer(SRE.watheId("textures/entity/maroon_steel_door.png"), ctx));
        BlockEntityRenderers.register(
                TMMBlockEntities.MUNTZ_STEEL_DOOR,
                ctx -> new SmallDoorBlockEntityRenderer(SRE.watheId("textures/entity/muntz_steel_door.png"), ctx));
        BlockEntityRenderers.register(
                TMMBlockEntities.NAVY_STEEL_DOOR,
                ctx -> new SmallDoorBlockEntityRenderer(SRE.watheId("textures/entity/navy_steel_door.png"), ctx));
        BlockEntityRenderers.register(
                TMMBlockEntities.WHEEL,
                ctx -> new WheelBlockEntityRenderer(SRE.watheId("textures/entity/wheel.png"), ctx));
        BlockEntityRenderers.register(
                TMMBlockEntities.RUSTED_WHEEL,
                ctx -> new WheelBlockEntityRenderer(SRE.watheId("textures/entity/rusted_wheel.png"), ctx));
        BlockEntityRenderers.register(
                TMMBlockEntities.BEVERAGE_PLATE,
                PlateBlockEntityRenderer::new);
        BlockEntityRenderers.register(TMMBlockEntities.HORN, HornBlockEntityRenderer::new);
        BlockEntityRenderers.register(TMMBlockEntities.FOURTH_ROOM_TABLE, FourthRoomTableBlockEntityRenderer::new);

        AmbienceUtil.registerBackgroundAmbience(
                new BackgroundAmbience(TMMSounds.AMBIENT_PSYCHO_DRONE, player -> gameComponent.isPsychoActive(), 20));

        AmbienceUtil.registerBackgroundAmbience(new MyBackgroundAmbience(TMMSounds.AMBIENT_TRAIN_INSIDE,
                SoundSource.AMBIENT,
                (player) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                        && gameComponent.isOutsideSoundsAvailable() && isTrainMoving()
                        && !SRE.isSkyVisible(player),
                0.25f, 20, 10));
        AmbienceUtil.registerBackgroundAmbience(new MyBackgroundAmbience(TMMSounds.AMBIENT_TRAIN_OUTSIDE,
                SoundSource.AMBIENT,
                (player) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                        && gameComponent.isOutsideSoundsAvailable() && isTrainMoving()
                        && SRE.isSkyVisible(player),
                0.6f, 20, 10));

        // Caching components
        ClientTickEvents.START_WORLD_TICK.register(clientWorld -> {
            gameComponent = SREGameWorldComponent.KEY.get(clientWorld);
            modifierComponent = WorldModifierComponent.KEY.get(clientWorld);
            areaComponent = AreasWorldComponent.KEY.get(clientWorld);
            trainComponent = SRETrainWorldComponent.KEY.get(clientWorld);
            moodComponent = SREPlayerMoodComponent.KEY.get(Minecraft.getInstance().player);
        });

        // Lock options
        OptionLocker.overrideOption("gamma", 0d);
        if (getLockedRenderDistance(SREConfig.isUltraPerfMode()) != null) {
            OptionLocker.overrideOption("renderDistance",
                    getLockedRenderDistance(SREConfig.isUltraPerfMode()));
        }
        OptionLocker.overrideOption("showSubtitles", false);
        OptionLocker.overrideOption("autoJump", false);
        OptionLocker.overrideOption("renderClouds", CloudStatus.OFF);
        OptionLocker.overrideSoundCategoryVolume("music", 0.0);
        OptionLocker.overrideSoundCategoryVolume("record", 0.1);
        OptionLocker.overrideSoundCategoryVolume("weather", 1.0);
        OptionLocker.overrideSoundCategoryVolume("block", 1.0);
        OptionLocker.overrideSoundCategoryVolume("hostile", 1.0);
        OptionLocker.overrideSoundCategoryVolume("neutral", 1.0);
        OptionLocker.overrideSoundCategoryVolume("player", 1.0);
        OptionLocker.overrideSoundCategoryVolume("ambient", 1.0);
        OptionLocker.overrideSoundCategoryVolume("voice", 1.0);

        // 客户端接收器 (在客户端初始化中调用)
        VoteClientReceiver.register();
        ClientPlayNetworking.registerGlobalReceiver(SecurityCameraModePayload.ID,
                new SecurityCameraModePayload.ClientReceiver());
        ClientPlayNetworking.registerGlobalReceiver(IsLobbyConfigPayload.ID, (payload, context) -> {
            SREClient.isInLobby = payload.isLobby();
            SRE.isLobby = payload.isLobby();
            LoggerFactory.getLogger(this.getClass())
                    .info("Is Lobby status: " + (SREClient.isInLobby ? "Yes" : "No"));
        });

        // Item tooltips
        TMMItemTooltips.addTooltips();
        AllowOtherCameraType.EVENT.register((original, localPlayer) -> {
            if (SecurityMonitorBlock.isInSecurityMode()) {
                return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
            }
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        });
        ClientHeldItemSwitchEvent.EVENT.register((player, mainHand, offHand) -> {
            hideLocalMainHandItemInLayer = isHandHiddenByEvent(player, mainHand, true);
            hideLocalOffHandItemInLayer = isHandHiddenByEvent(player, offHand, false);
        });
        ClientTickEvents.START_WORLD_TICK.register(clientWorld -> {
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
                return;
            }
            final LocalPlayer player = Minecraft.getInstance().player;
            {
                boolean keycode = Minecraft.getInstance().options.keyShift.consumeClick();
                if (keycode) {
                    if (SecurityMonitorBlock.isInSecurityMode()) {
                        SecurityMonitorBlock.setSecurityMode(false);
                        Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
                    }
                }
            }

            prevInstinctLightLevel = instinctLightLevel;
            // 检测按键按下事件，只在按键状态从释放变为按下时切换
            boolean isKeyDown = instinctKeybind.isDown();
            if (isKeyDown && !prevInstinctKeyDown) {
                isInstinctToggleEnabled = !isInstinctToggleEnabled; // 切换状态
                updateInstinctCache(Minecraft.getInstance());
            }
            prevInstinctKeyDown = isKeyDown;

            // instinct night vision - 现在基于切换状态而不是按键按下来判断
            if (SREClient.isInstinctEnabled()) {
                instinctLightLevel += .2f;
            } else {
                instinctLightLevel -= .2f;
            }
            float maxLightLevel = 0.75f;
            if (player.isCreative())
                maxLightLevel = 1f;
            instinctLightLevel = Mth.clamp(instinctLightLevel, -.04f, maxLightLevel);
            if (gameComponent == null)
                return;
            if (!prevGameRunning && gameComponent.isRunning()) {
                Minecraft.getInstance().player.getInventory().selected = 8;
            }
            prevGameRunning = gameComponent.isRunning();

            // Fade sound with game start / stop fade
            SREGameWorldComponent component = SREGameWorldComponent.KEY.get(clientWorld);
            if (component.getFade() > 0) {
                Minecraft.getInstance().getSoundManager().updateSourceVolume(SoundSource.MASTER,
                        Mth.map(component.getFade(), 0, GameConstants.FADE_TIME, soundLevel, 0));
            } else {
                Minecraft.getInstance().getSoundManager().updateSourceVolume(SoundSource.MASTER, soundLevel);
                soundLevel = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
            }

            if (player != null) {
                StoreRenderer.tick();
                HudStoreRenderer.tick();
                TimeRenderer.tick();
                StaminaRenderer.tick();
            }

        });
        intervalTime = new Random().nextInt(0, 200);
        ClientTickEvents.END_CLIENT_TICK.register((client) -> {
            FrameAnimationRenderer.setInWorld(client != null && client.level != null);
            LocalPlayer player = client.player;
            cached_player = player;
            if (player == null) {
                localPlayerPsychoActive = false;
                PLAYER_PSYCHO_CACHE.clear();
                prevMainHandSnapshot = ItemStack.EMPTY;
                prevOffHandSnapshot = ItemStack.EMPTY;
                prevSelectedHotbarSlot = -1;
                hideLocalMainHandItemInLayer = false;
                hideLocalOffHandItemInLayer = false;
            } else {
                int INTERVAL = 5;
                if (SREConfig.isUltraPerfMode())
                    INTERVAL = 20;
                if (client.level.getGameTime() % INTERVAL == 0) {
                    updateInstinctCache(client);
                }
                updateHudApiCache(client);
                localPlayerPsychoActive = SREPlayerPsychoComponent.KEY.get(player).getPsychoTicks() > 0;
                PLAYER_PSYCHO_CACHE.put(player.getUUID(), localPlayerPsychoActive);
                if (client.level != null) {
                    for (Player levelPlayer : client.level.players()) {
                        PLAYER_PSYCHO_CACHE.put(levelPlayer.getUUID(),
                                SREPlayerPsychoComponent.KEY.get(levelPlayer).getPsychoTicks() > 0);
                    }
                }
                ItemStack mainHand = player.getMainHandItem();
                ItemStack offHand = player.getOffhandItem();
                int selectedHotbarSlot = player.getInventory().selected;
                boolean mainHandChanged = selectedHotbarSlot != prevSelectedHotbarSlot
                        || !ItemStack.isSameItemSameComponents(mainHand, prevMainHandSnapshot);
                boolean offHandChanged = !ItemStack.isSameItemSameComponents(offHand, prevOffHandSnapshot);
                if (mainHandChanged || offHandChanged) {
                    prevMainHandSnapshot = mainHand.copy();
                    prevOffHandSnapshot = offHand.copy();
                    prevSelectedHotbarSlot = selectedHotbarSlot;
                    ClientHeldItemSwitchEvent.EVENT.invoker().onSwitch(player, mainHand, offHand);
                }
            }

            if (gameComponent != null) {
                if (gameComponent.isRunning()) {
                    if (client != null && client.player != null) {
                        if (GameUtils.isPlayerSpectator(client.player)) {
                            intervalTime++;
                            if (intervalTime >= 30 * 10) { // 30s
                                if (TrainVoicePlugin.CLIENT_API != null) {
                                    if (!TrainVoicePlugin.CLIENT_API.isDisconnected()) {
                                        if (TrainVoicePlugin.CLIENT_API.getGroup() == null) {
                                            ClientPlayNetworking.send(new JoinSpecGroupPayload(true));
                                        }
                                    }
                                }
                                intervalTime = 0;
                            }
                        }
                    }

                }
            }
            SREClient.handParticleManager.tick();
            RoundTextRenderer.tick();
        });

        SyncMapConfigPayload.registerReceiver();
        FourthRoomStatePayload.registerReceiver();
        FourthRoomTableEffectsPayload.registerReceiver();
        ClientPlayNetworking.registerGlobalReceiver(OpenFourthRoomPeekDeckPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    var client = context.client();
                    if (client.level == null || client.player == null) {
                        return;
                    }
                    if (!FourthRoomClientState.snapshot().active()
                            || FourthRoomClientState.snapshot().viewer().peekCards().isEmpty()) {
                        return;
                    }
                    if (client.screen instanceof io.wifi.starrailexpress.client.gui.screen.ingame.FourthRoomPeekDeckScreen) {
                        return;
                    }
                    client.setScreen(new io.wifi.starrailexpress.client.gui.screen.ingame.FourthRoomPeekDeckScreen(
                            client.screen));
                }));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> FourthRoomClientState.clear());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> FourthRoomCameraDirector.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            FourthRoomClientState.clear();
            FourthRoomCameraDirector.clear();
        });
        TriggerScreenEdgeEffectPayload.registerReceiver();
        RemoveStatusBarPayload.registerReceiver();
        TriggerStatusBarPayload.registerReceiver();
        ClientPlayNetworking.registerGlobalReceiver(ShootMuzzleS2CPayload.ID, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> {
                if (client.level == null || client.player == null)
                    return;
                Entity entity = client.level.getEntity(payload.shooterId());
                if (!(entity instanceof Player shooter))
                    return;

                if (shooter.getId() == client.player.getId()
                        && client.options.getCameraType() == CameraType.FIRST_PERSON)
                    return;
                Vec3 muzzlePos = MatrixParticleManager.muzzlePosForPlayer$get(shooter);
                if (muzzlePos != null)
                    client.level.addParticle(TMMParticles.GUNSHOT, muzzlePos.x, muzzlePos.y, muzzlePos.z, 0, 0, 0);
            });

        });
        ClientPlayNetworking.registerGlobalReceiver(SniperScopeStateS2CPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                // 如果倍镜被卸下，退出开镜状态
                if (!payload.scopeAttached()) {
                    ScopeOverlayRenderer.setInScopeView(false);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(PoisonComponentUtils.PoisonOverlayPayload.ID,
                new PoisonComponentUtils.PoisonOverlayPayload.Receiver());
        ClientPlayNetworking.registerGlobalReceiver(GunDropPayload.ID, new GunDropPayload.Receiver());
        ClientPlayNetworking.registerGlobalReceiver(AnnounceWelcomePayload.ID, (payload, context) -> {
            if (payload.role() == null)
                return;
            var res = ResourceLocation.tryParse(payload.role());

            var announcementText = RoleAnnouncementTexts.getFromName(res.getPath());
            if (announcementText == null) {
                LoggerFactory.getLogger(this.getClass())
                        .error("Unable to get announcement Text for '" + res.getPath() + "' (" + res
                                + "). Available: ");
                return;
            }
            RoundTextRenderer.startWelcome(announcementText, payload.killers(), payload.targets());
        });
        ClientPlayNetworking.registerGlobalReceiver(AnnounceEndingPayload.ID, (payload, context) -> {
            RoundTextRenderer.startEnd();
            final var gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                RoundTextRenderer.lastRole.putAll(gameComponent.getRoles());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(TaskCompletePayload.ID, new TaskCompletePayload.Receiver());
        ClientPlayNetworking.registerGlobalReceiver(ShowStatsPayload.ID, (payload, context) -> {
            UUID targetPlayerUuid = payload.targetPlayerUuid();
            context.client().execute(() -> {
                if (SREClient.gameComponent.fade <= 0) {
                    context.client().execute(() -> {
                        context.client().setScreen(new PlayerStatsScreen(targetPlayerUuid));
                    });
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(OnGameFinishedPayload.TYPE, (payload, context) -> {
            OnGameFinishedClient.EVENT.invoker().gameFinished();
        });
        ClientPlayNetworking.registerGlobalReceiver(OnGameStartedPayload.TYPE, (payload, context) -> {
            OnGameStartedClient.EVENT.invoker().gameStarted();
        });
        ClientPlayNetworking.registerGlobalReceiver(SyncRoomToPlayerPayload.ID, (payload, context) -> {
            Map<UUID, Integer> data = payload.data();
            if (Minecraft.getInstance().isSingleplayer()) {
                SRE.LOGGER.info("Singleplayer. No need to sync info.");
                return;
            } else {
                SRE.LOGGER.info("Sync RoomToPlayer info from server.");
            }
            GameUtils.roomToPlayer.clear();
            GameUtils.roomToPlayer.putAll(data);
        });
        ClientPlayNetworking.registerGlobalReceiver(ModWhitelistConfigPayload.ID, (payload, context) -> {
            ModWhitelistClientNetworkHandler.sendModWhitelistPayload();
        });

        ClientPlayNetworking.registerGlobalReceiver(ShowSelectedMapUIPayload.ID, (payload, context) -> {
            var str = payload.serverConfig();

            // @SuppressWarnings("unchecked")
            try {
                var a = MapConfig.gson.fromJson(str, MapConfig.class);
                MapConfig.getInstance().maps.clear();
                MapConfig.getInstance().maps.addAll(a.maps);
            } catch (JsonSyntaxException e) {
                LoggerFactory.getLogger("TMMClient").error(e.getMessage());
                e.printStackTrace();
            }
            context.client().execute(() -> {
                context.client().setScreen(new MapSelectorScreen());
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(MapVotingResultsPayload.TYPE, (payload, context) -> {
            MapDetailsRenderer.triggerMapDetails(
                    payload.result);
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenSkinScreenPaylod.ID, (payload, context) -> {

            context.client().execute(() -> {
                context.client().setScreen(new SkinManagementScreen());
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenProgressionScreenPayload.ID, (payload, context) -> {
            context.client().execute(() -> context.client().setScreen(new ProgressionPassScreen()));
        });
        ClientPlayNetworking.registerGlobalReceiver(
                io.wifi.starrailexpress.content.mail.OpenMailboxScreenPayload.ID, (payload, context) -> {
                    context.client().execute(() -> context.client().setScreen(
                            new io.wifi.starrailexpress.content.mail.MailboxScreen()));
                });
        // ClientPlayNetworking.registerGlobalReceiver(
        // io.wifi.starrailexpress.network.OpenRoleUnlockScreenPayload.ID, (payload,
        // context) -> {
        // context.client().execute(() -> {
        // io.wifi.starrailexpress.unlock.RoleUnlockManager.getInstance()
        // .updateClientData(payload.globalGamesPlayed(), payload.forceUnlockedRoles());
        // context.client().setScreen(
        // new io.wifi.starrailexpress.client.gui.screen.RoleUnlockProgressScreen());
        // });
        // });
        // ClientPlayNetworking.registerGlobalReceiver(
        // io.wifi.starrailexpress.network.RoleUnlockedHudPayload.ID, (payload, context)
        // -> {
        // context.client().execute(() ->
        // io.wifi.starrailexpress.client.gui.RoleUnlockHudRenderer
        // .enqueue(payload.globalGamesPlayed(), payload.unlockedRoleIds()));
        // });
        ClientPlayNetworking.registerGlobalReceiver(CloseUiPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(null);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(PlayerDeathPayload.ID, (payload, context) -> {
            NoellesrolesClient.isTaskInstinctEnabled = false;
            // isInstinctToggleEnabled = false;
        });
        // Chat Dialogue
        ClientPlayNetworking.registerGlobalReceiver(
                net.exmo.sre.client.chat.OpenChatDialoguePayload.ID, (payload, context) -> {
                    context.client().execute(() -> {
                        net.exmo.sre.client.chat.ChatDialogueData data = net.exmo.sre.client.chat.ChatDialogueData.GSON
                                .fromJson(
                                        payload.dialogueJson(),
                                        net.exmo.sre.client.chat.ChatDialogueData.class);
                        context.client().setScreen(
                                new net.exmo.sre.client.chat.ChatDialogueScreen(
                                        data, payload.targetEntityId()));
                    });
                });

        // Instinct keybind
        instinctKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + SRE.MOD_ID + ".instinct",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                "category." + SRE.MOD_ID + ".keybinds"));

        // Register stats keybind
        statsKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + SRE.MOD_ID + ".stats",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O, // 默认热键 'O'
                "category." + SRE.MOD_ID + ".keybinds"));

        // Register skins keybind
        skinsKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + SRE.MOD_ID + ".skins",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N, // 默认热键 'N'
                "category." + SRE.MOD_ID + ".keybinds"));
        // Initialize Command UI system
        // TMMCommandUI.init();
        // KeyPressHandler.register();
        InputHandler.initialize();

        // Register HUD rendering for security camera
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((guiGraphics, deltaTick) -> {
            SecurityCameraHUD.render(guiGraphics, Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    Minecraft.getInstance().getWindow().getGuiScaledHeight());
            SecurityCameraHUD.renderCameraFeed(guiGraphics, Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    Minecraft.getInstance().getWindow().getGuiScaledHeight());
            ScopeOverlayRenderer.renderScopeOverlay(guiGraphics, deltaTick);
            WaypointHUD.renderHUD(guiGraphics, deltaTick.getRealtimeDeltaTicks());
            AFKRenderer.renderAFKEffects(guiGraphics, deltaTick.getRealtimeDeltaTicks());
            FourthRoomCameraDirector.renderOverlay(guiGraphics);
            FourthRoomTableHud.render(guiGraphics);
            // RoleUnlockHudRenderer.render(guiGraphics);

            // // 添加地图详情渲染
            // Font font = Minecraft.getInstance().font;
            // LocalPlayer player = Minecraft.getInstance().player;
            // if (font != null && player != null) {
            // MapDetailsRenderer.renderHud(font, player, guiGraphics,
            // deltaTick.getRealtimeDeltaTicks());
            // }
        });
        ClientPlayNetworking.registerGlobalReceiver(SyncWaypointsPacket.ID, SyncWaypointsPacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(SyncWaypointVisibilityPacket.ID,
                SyncWaypointVisibilityPacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(SyncSpecificWaypointVisibilityPacket.ID,
                SyncSpecificWaypointVisibilityPacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(BreakArmorPayload.ID, (payload, context) -> {
            LocalPlayer player = context.player();
            if (player != null && player.level() != null) {
                player.level().playLocalSound(payload.x(), payload.y(), payload.z(),
                        TMMSounds.ITEM_PSYCHO_ARMOUR, SoundSource.MASTER, 5.0F, 1.0F, false);
            }
        });

        // Register client tick event for stats keybind
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            FourthRoomCameraDirector.tick(client);
            if (SREClient.gameComponent == null)
                return;

            if (statsKeybind.consumeClick()) {

                if (SREClient.gameComponent.fade <= 0) {
                    if (client.screen instanceof PlayerStatsScreen) {
                        client.setScreen(null);
                    } else {
                        client.setScreen(new PlayerStatsScreen(client.player.getUUID()));
                    }
                }

            }

            if (skinsKeybind.consumeClick()) {
                if (client.screen instanceof SkinManagementScreen) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new SkinManagementScreen());
                }
            }
        });
    }

    private static void updateInstinctCache(Minecraft client) {
        HashSet<UUID> toRemove = new HashSet<>();
        for (var entry : cachedHighLightMap.entrySet()) {
            Entity entity = client.level.getEntities().get(entry.getKey());
            if (entity == null) {
                toRemove.add(entry.getKey());
                continue;
            }
            cachedHighLightMap.put(entity.getUUID(), getInstinctHighlight(entity));
        }
        for (var it : toRemove) {
            cachedHighLightMap.remove(it);
        }
    }

    public static SRETrainWorldComponent getTrainComponent() {
        return trainComponent;
    }

    public static float getTrainSpeed() {
        return trainComponent.getSpeed();
    }

    public static boolean isTrainMoving() {
        return gameComponent != null && gameComponent.isRunning() && trainComponent != null
                && trainComponent.getSpeed() > 0;
    }

    public static boolean isSceneOffsetActive() {
        return areaComponent != null && areaComponent.sceneOffsetEnabled;
    }

    public static boolean needsChunkOffset() {
        return isTrainMoving() || isSceneOffsetActive();
    }

    public static class CustomModelProvider implements ModelLoadingPlugin {

        private final Map<ResourceLocation, UnbakedModel> modelIdToBlock = new Object2ObjectOpenHashMap<>();
        private final Set<ResourceLocation> withInventoryVariant = new HashSet<>();

        public void register(Block block, UnbakedModel model) {
            this.register(BuiltInRegistries.BLOCK.getKey(block), model);
        }

        public void register(ResourceLocation id, UnbakedModel model) {
            this.modelIdToBlock.put(id, model);
        }

        public void markInventoryVariant(Block block) {
            this.markInventoryVariant(BuiltInRegistries.BLOCK.getKey(block));
        }

        public void markInventoryVariant(ResourceLocation id) {
            this.withInventoryVariant.add(id);
        }

        @Override
        public void onInitializeModelLoader(Context ctx) {
            ctx.modifyModelOnLoad().register((model, context) -> {
                ModelResourceLocation topLevelId = context.topLevelId();
                if (topLevelId == null) {
                    return model;
                }
                ResourceLocation id = topLevelId.id();
                if (topLevelId.getVariant().equals("inventory") && !this.withInventoryVariant.contains(id)) {
                    return model;
                }
                if (this.modelIdToBlock.containsKey(id)) {
                    return this.modelIdToBlock.get(id);
                }
                return model;
            });
        }
    }

    public static boolean isPlayerAliveAndInSurvivalIgnoreShitSplit() {
        return cachedPlayerAliveAndInSurvivalIgnoreShitSplit;
    }

    public static boolean isPlayerAliveAndInSurvival() {
        return cachedPlayerAliveAndInSurvival;
    }

    public static boolean isPlayerSpectatingOrCreative() {
        return cachedPlayerSpectatingOrCreative;
    }

    public static boolean isKiller() {
        return cachedKiller;
    }

    public static boolean isRole(SRERole role) {
        return cachedPlayerRole != null
                && role != null
                && cachedPlayerRole.identifier().equals(role.identifier());
    }

    public static SRERole getCachedPlayerRole() {
        return cachedPlayerRole;
    }

    public static boolean isPlayerSpectator() {
        return cachedPlayerSpectator;
    }

    public static boolean shouldUseTrainHud() {
        return cachedUseTrainHud;
    }

    public static boolean canRenderChatHud() {
        return cachedCanRenderChatHud;
    }

    public static boolean shouldShowDebugHud() {
        return cachedShowDebugHud;
    }

    public static boolean shouldRenderVanillaHud() {
        return cachedRenderVanillaHud;
    }

    public static boolean getLooseEndPenalty() {
        return cachedLooseEndPenalty;
    }

    public static int getCachedInstinctHighlight(Entity target) {
        if (!(target instanceof ItemEntity || target instanceof Player || target instanceof NoteEntity
                || target instanceof PuppeteerBodyEntity
                || target instanceof FirecrackerEntity || target instanceof PlayerBodyEntity)) {
            return -1;
        }
        if (!cachedHighLightMap.containsKey(target.getUUID())) {
            int color = getInstinctHighlight(target);
            cachedHighLightMap.put(target.getUUID(), color);
            return color;
        }
        return cachedHighLightMap.getOrDefault(target.getUUID(), -1);
    }

    public static int getInstinctHighlight(Entity target) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || gameComponent == null) {
            return -1;
        }
        int invokerColor = OnGetInstinctHighlight.EVENT.invoker().GetInstinctHighlight(target, isInstinctEnabled());
        if (invokerColor != -1) {
            if (invokerColor == -2)
                return -1;
            return invokerColor;
        }
        if (!isInstinctEnabled()) {
            return -1;
        }
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(Minecraft.getInstance().player.level());
        // if (target instanceof PlayerBodyEntity) return 0x606060;
        if (target instanceof ItemEntity || target instanceof NoteEntity || target instanceof FirecrackerEntity)
            return 0xDB9D00;
        // 渲染傀儡高亮
        if (target instanceof PuppeteerBodyEntity) {
            if (GameUtils.isPlayerSpectatingOrCreativeIgnoreShitSplit(Minecraft.getInstance().player)) {
                return new java.awt.Color(181, 255, 231).getRGB();
            }
        }
        if (target instanceof Player targetPlayer) {
            if (!(targetPlayer).isSpectator()) {
                if (GameUtils.isPlayerSpectatingOrCreativeIgnoreShitSplit(Minecraft.getInstance().player)) {
                    SRERole role = gameWorldComponent.getRole(targetPlayer);
                    if (role == null) {
                        return (TMMRoles.CIVILIAN.color());
                    } else {
                        return (role.color());
                    }
                } else {
                    return (TMMRoles.CIVILIAN.color());
                }

            }
        }
        return -1;
    }

    static Predicate<Player> isHoldSpecialItem = (player) -> {
        if (player.getMainHandItem().getItem() instanceof KnifeItem)
            return true;
        if (player.getMainHandItem().getItem() instanceof GrenadeItem)
            return true;
        if (player.getMainHandItem().getItem() instanceof YinYangSwordItem)
            return true;
        return false;
    };
    private static boolean cachedPlayerAliveAndInSurvivalIgnoreShitSplit = false;
    public static boolean cachedCanSeeTime = false;

    public static boolean isInstinctEnabled() {
        boolean canUseInstinct = isKiller();
        final var player = Minecraft.getInstance().player;
        if (SREClient.gameComponent != null) {
            var role = SREClient.gameComponent.getRole(player);
            if (role != null) {
                canUseInstinct = role.canUseInstinct();
            }
        }
        return (isInstinctToggleEnabled
                && ((canUseInstinct && isPlayerAliveAndInSurvival()) || isPlayerSpectatingOrCreative()))
                || (canUseInstinct && isHoldSpecialItem.test(player));
    }

    public static Object getLockedRenderDistance(boolean ultraPerfMode) {
        return null;
    }

    private static boolean isHandHiddenByEvent(LocalPlayer player, ItemStack stack, boolean isMainHand) {
        ItemStack eventRes = AllowItemShowInHand.EVENT.invoker().allowShowInHand(player, stack, isMainHand);
        return eventRes != null && eventRes.isEmpty();
    }

    private static void updateHudApiCache(Minecraft client) {
        if (gameComponent == null)
            return;
        if (client.level == null)
            return;
        cachedLooseEndPenalty = gameComponent.isRunning() && RefugeeComponent.KEY.get(client.level).isAnyRevivals
                && DeathPenaltyComponent.KEY.get(client.player).hasPenalty();
        LocalPlayer player = client.player;
        cachedPlayerAliveAndInSurvival = GameUtils.isPlayerAliveAndSurvival(player);
        cachedCanSeeTime = SREClient.gameComponent.getGameMode().canAllPeopleSeeTime();
        cachedPlayerAliveAndInSurvivalIgnoreShitSplit = GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player);
        cachedPlayerSpectatingOrCreative = GameUtils.isPlayerSpectatingOrCreative(player);
        cachedPlayerCreative = player != null && player.isCreative();
        cachedPlayerSpectator = player != null && player.isSpectator();
        cachedPlayerRole = gameComponent != null && player != null ? gameComponent.getRole(player) : null;
        cachedUseTrainHud = !isInLobby && trainComponent != null && trainComponent.hasHud();
        cachedKiller = gameComponent != null && player != null && gameComponent.canUseKillerFeatures(player);
        cachedShowDebugHud = isInLobby || (cachedPlayerCreative);
        cachedRenderVanillaHud = false;

        boolean canRender = true;
        if (isInLobby)
            canRender = true;
        if (gameComponent.isRunning()) {
            canRender = false;
        }
        if (player != null && !isInLobby && gameComponent.isRunning()) {
            if (SRE.cantUseChatHud.stream().anyMatch(pre -> pre.test(player))) {
                canRender = false;
                cachedRenderVanillaHud = false;
            } else if (!cachedPlayerAliveAndInSurvival) {
                canRender = true;
                cachedRenderVanillaHud = true;
            } else {
                canRender = SRE.canUseChatHudPlayer.stream().anyMatch(predicate -> predicate.test(player))
                        || (cachedPlayerRole != null
                                && SRE.canUseChatHud.stream().anyMatch(predicate -> predicate.test(cachedPlayerRole)));
            }
        }
        cachedCanRenderChatHud = canRender;
        cachedRenderVanillaHud = cachedRenderVanillaHud || isInLobby || player.isCreative();
    }

    public static void stalkerKnifeInventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean b) {
        Integer i1 = itemStack.get(SREDataComponentTypes.WEAPON_USED_TIME);
        if (!((LocalPlayer) entity).isCrouching() || (i1 == null || i1 != 3))
            return;
        Entity crosshairPickEntity = Minecraft.getInstance().crosshairPickEntity;
        // distance <=4
        if (crosshairPickEntity != null && entity.distanceToSqr(crosshairPickEntity) > 16) {
            return;
        }
        if (crosshairPickEntity instanceof Player && ((LocalPlayer) entity).getTicksUsingItem() > 3) {
            ((LocalPlayer) entity).releaseUsingItem();
        }
    }
}
