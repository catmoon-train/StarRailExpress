package org.agmas.noellesroles.client;

import static org.agmas.noellesroles.client.RicesRoleRhapsodyClient.handleStalkerContinuousInput;
import static org.agmas.noellesroles.client.RicesRoleRhapsodyClient.registerClientEvents;
import static org.agmas.noellesroles.client.RicesRoleRhapsodyClient.registerEntityRenderers;
import static org.agmas.noellesroles.client.RicesRoleRhapsodyClient.registerScreens;
import static org.agmas.noellesroles.client.RicesRoleRhapsodyClient.setupItemCallbacks;
import static org.agmas.noellesroles.component.InsaneKillerPlayerComponent.isPlayerBodyEntity;
import static org.agmas.noellesroles.component.InsaneKillerPlayerComponent.playerBodyEntities;
import static org.agmas.noellesroles.effects.TimeStopEffect.clientPositions;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.client.StatusInit;
import net.minecraft.client.CameraType;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.block_entity.VendingMachinesBlockEntity;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.init.*;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.blood.BloodMain;
import org.agmas.noellesroles.client.event.MutableComponentResult;
import org.agmas.noellesroles.client.event.OnMessageBelowMoneyRenderer;
import org.agmas.noellesroles.client.renderer.VendingMachinesBlockEntityRenderer;
import org.agmas.noellesroles.client.screen.*;
import org.agmas.noellesroles.component.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.component.MagicianPlayerComponent;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.entity.LockEntity;
import org.agmas.noellesroles.entity.WheelchairEntityModel;
import org.agmas.noellesroles.entity.WheelchairEntityRenderer;
import org.agmas.noellesroles.item.PanItem;
import org.agmas.noellesroles.item.ProblemSetItem;
import org.agmas.noellesroles.packet.*;
import org.agmas.noellesroles.packet.Loot.LootPoolsInfoCheckS2CPacket;
import org.agmas.noellesroles.packet.Loot.LootPoolsInfoRequestC2SPacket;
import org.agmas.noellesroles.packet.Loot.LootPoolsInfoS2CPacket;
import org.agmas.noellesroles.packet.Loot.LootResultS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.agmas.noellesroles.utils.lottery.LotteryManager;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.InputConstants;

import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import dev.doctor4t.ratatouille.util.TextUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.client.StaminaRenderer;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.util.TMMItemTooltips;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.AllowNameRender;
import io.wifi.starrailexpress.event.OnKillerCohortDisplay;
import io.wifi.starrailexpress.event.OnRoundStartWelcomeTimmer;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.BreakArmorPayload;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import walksy.crosshairaddons.CrosshairAddons;
import net.minecraft.sounds.SoundSource;

public class NoellesrolesClient implements ClientModInitializer {
    public static boolean hasInitStatusBar = false;
    public static int insanityTime = 0;
    public static KeyMapping roleGuessNoteClientBind;
    public static KeyMapping abilityBind;
    public static KeyMapping taskInstinctBind;
    public static KeyMapping roleIntroClientBind;
    public static Player target;
    public static PlayerBodyEntity targetBody;
    public static Player targetFakeBody;
    public static Player hudTarget;
    public static boolean isTaskInstinctEnabled = false;
    public static Map<UUID, UUID> SHUFFLED_PLAYER_ENTRIES_CACHE = Maps.newHashMap();
    public static ArrayList<BroadcastMessageInfo> currentBroadcastMessage = new ArrayList<>();
    public static BloodMain bloodMain = new BloodMain();
    public static Map<UUID, AbstractClientPlayer> lastTimeStopRenderPlayer = new HashMap<>();
    public static long lastClientTickTime = 0;
    public static final long CLIENT_TICK_INTERVAL_MS = 50; // 1000ms / 20 ticks per second = 50ms per tick
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

    @Override
    public void onInitializeClient() {
        // 注册HUD渲染
        LimitedInventoryScreen.NotAllowItemTakePredicates.add(stack -> stack.is(ModItems.BOMB));

        BlockEntityRenderers.register(
                ModBlocks.VENDING_MACHINES_BLOCK_ENTITY,
                VendingMachinesBlockEntityRenderer::new);

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.VENDING_MACHINES_BLOCK, RenderType.translucent());
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
        AmbienceUtil.registerBackgroundAmbience(
                new BackgroundAmbience(NRSounds.JESTER_AMBIENT,
                        player -> {
                            if (SREClient.gameComponent == null)
                                return false;
                            if (SREClient.gameComponent.isPsychoActive()) {
                                var level = Minecraft.getInstance().level;
                                if (level == null)
                                    return false;
                                return (level.players().stream().anyMatch((p) -> {
                                    if (SREClient.gameComponent.isRole(p, ModRoles.JESTER)) {
                                        if (SREPlayerPsychoComponent.KEY.get(p).getPsychoTicks() > 0) {
                                            return true;
                                        }
                                    }
                                    return false;
                                }));
                            }
                            return false;
                        },
                        1));

        EntityRendererRegistry.register(ModEntities.WHEELCHAIR, WheelchairEntityRenderer::new);

        EntityModelLayerRegistry.registerModelLayer(WheelchairEntityModel.LAYER_LOCATION,
                WheelchairEntityModel::createBodyLayer);
        AllowNameRender.EVENT.register((target) -> {
            SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(target.level());
            if (gameWorldComponent.isRole(target,
                    ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES)) {
                var insaneComponent = InsaneKillerPlayerComponent.KEY.get(target);
                if (insaneComponent != null) {
                    if (insaneComponent.isActive || insaneComponent.inNearDeath()) {
                        return false;
                    }
                }

            }
            return true;
        });
        ClientHudRenderer.registerRenderersEvent();

        WorldRenderEvents.AFTER_TRANSLUCENT.register((renderContext) -> {
            TaskBlockOverlayRenderer.render(renderContext);
        });
        InstinctRenderer.registerInstinctEvents();
        roleIntroClientBind = KeyBindingHelper
                .registerKeyBinding(new KeyMapping("key." + Noellesroles.MOD_ID + ".role_intro",
                        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.starrailexpress.keybinds"));
        roleGuessNoteClientBind = KeyBindingHelper
                .registerKeyBinding(new KeyMapping("key." + Noellesroles.MOD_ID + ".guess_role_note",
                        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "category.starrailexpress.keybinds"));
        abilityBind = KeyBindingHelper.registerKeyBinding(new KeyMapping("key." + Noellesroles.MOD_ID + ".ability",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.starrailexpress.keybinds"));
        taskInstinctBind = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.noellesroles.taskinstinct",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.starrailexpress.keybinds"));
        ClientPlayNetworking.registerGlobalReceiver(CreateClientSmokeAreaPacket.ID, (payload, context) -> {
            ClientSmokeAreaManager.createSmokeArea(context.client().level, payload.position(), payload.radius(),
                    payload.durationTicks());
        });
        ClientTickEvents.END_WORLD_TICK.register((level) -> {
            if (level == null)
                return;
            ClientSmokeAreaManager.tick();
        });
        ClientPlayNetworking.registerGlobalReceiver(ProblemScreenOpenC2SPacket.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (client.screen != null && client.screen instanceof MathSolverScreen) {
                    return;
                } else {
                    client.setScreen(new MathSolverScreen(payload.forced(), payload.maxTrial()));
                }
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
                    client.setScreen(new RoleIntroduceScreen(client.player));
                }
            });
        });

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
                    // 处理准星效果
                    CrosshairAddons.getStateManager().handleBreakPacket(payload.x(), payload.y(), payload.z());
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(PlayerResetS2CPacket.ID, (payload, context) -> {
            final var client = context.client();
            client.execute(() -> {
                if (client.player != null) {
                    // client.player.sendSystemMessage(Component.translatable("screen.noellesroles.guess_role.reset")
                    // .withColor(Color.ORANGE.getRGB()));
                    GuessRoleScreen.clearData();
                    client.player.containerMenu.setCarried(ItemStack.EMPTY);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(BloodConfigS2CPacket.ID, (payload, context) -> {
            bloodMain.enabled = payload.enabled();
            LoggerFactory.getLogger(this.getClass())
                    .info("Blood Particle status: " + (bloodMain.enabled ? "Enabled" : "Disabled"));
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
        // 注册抽奖网络包处理：接收服务器抽奖结果后播放抽奖动画
        ClientPlayNetworking.registerGlobalReceiver(LootResultS2CPacket.ID, (payload, context) -> {
            final var client = context.client();
            client.execute(() -> {
                if (client.player != null) {
                    client.setScreen(new LootScreen(payload.poolID(), payload.quality(), payload.ansID()));
                }
            });
        });
        // 检查卡池信息是否缺失，如果不缺失则打开卡池界面，否则请求
        ClientPlayNetworking.registerGlobalReceiver(LootPoolsInfoCheckS2CPacket.ID, (payload, context) -> {
            final var client = context.client();
            client.execute(() -> {
                List<Integer> requestPoolIDs = new ArrayList<>();
                for (Integer poolID : payload.poolIDs()) {
                    if (LotteryManager.getInstance().getLotteryPool(poolID) == null)
                        requestPoolIDs.add(poolID);
                }
                if (requestPoolIDs.isEmpty() && client.player != null)
                    client.setScreen(new LootInfoScreen());
                else {
                    // 缺失卡池信息，向服务器请求缺失的卡池信息
                    ClientPlayNetworking.send(new LootPoolsInfoRequestC2SPacket(requestPoolIDs));
                }
            });
        });

        OnRoundStartWelcomeTimmer.EVENT.register((player, timer) -> {
            if (timer == 1) {
                if (NoellesRolesConfig.HANDLER.instance().welcome_voice) {
                    player.level().playLocalSound(player, NRSounds.HARPY_WELCOME, SoundSource.AMBIENT, 1f, 1f);
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenVendingMachinesScreenS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                BlockEntity blockEntity = context.client().level.getBlockEntity(payload.blockPos());
                if (blockEntity instanceof VendingMachinesBlockEntity vendingMachinesBlockEntity) {
                    Map<ItemStack, Integer> shopItems = new LinkedHashMap<>();
                    vendingMachinesBlockEntity.getShops().forEach(shop -> {
                        shopItems.put(shop.stack(), shop.price());
                    });
                    context.client().setScreen(new VendingMachinesGui(shopItems).setBlockPos(payload.blockPos()));
                }
            });

        });
        // 注册抽奖界面网络包处理：接收并保存服务器卡池信息并显示界面
        ClientPlayNetworking.registerGlobalReceiver(LootPoolsInfoS2CPacket.ID, (payload, context) -> {
            for (LotteryManager.LotteryPool lotteryPool : payload.pools()) {
                if (LotteryManager.getInstance().getLotteryPool(lotteryPool.getPoolID()) == null)
                    LotteryManager.getInstance().addLotteryPool(lotteryPool);
                else
                    LotteryManager.getInstance().setLotteryPoolByID(lotteryPool.getPoolID(), lotteryPool);
            }
            // 将卡池按 id大小排序
            LotteryManager.getInstance().sortPools();
            final var client = context.client();
            client.execute(() -> {
                if (client.player != null)
                    client.setScreen(new LootInfoScreen());
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

        Listen.registerEvents();
        InvisbleHandItem.register();
        OnKillerCohortDisplay.EVENT.register((player) -> {
            if (player == null)
                return null;
            if (SREClient.gameComponent != null) {
                if (SREClient.gameComponent.isRole(player, ModRoles.MAGICIAN)) {
                    var roleR = MagicianPlayerComponent.KEY.get(player).getDisguiseRoleId();

                    // Noellesroles.LOGGER.info("mag player:
                    // "+player.getDisplayName().getString()+(roleR!=null?" "+roleR:" Null role"));
                    return RoleUtils.getRoleName(roleR);
                }
            }
            return null;
        });
        ClientTickEvents.END_WORLD_TICK.register((client) -> {
            if (!hasInitStatusBar) {
                hasInitStatusBar = true;
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
            if (client.player == null)
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
            if (client == null || client.player == null)
                return;
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
            if (client.player.isCreative()) {
                if (abilityBind.consumeClick()) {
                    if (SREClient.gameComponent.isRole(client.player, ModRoles.ATTENDANT)) {
                        ClientPlayNetworking.send(new AbilityC2SPacket());
                    }
                }
                return;
            }
            if (!isPlayerInAdventureMode(client.player))
                return;
            insanityTime++;
            if (insanityTime >= 20 * 6) {
                insanityTime = 0;
                List<UUID> keys = new ArrayList<UUID>(SREClient.PLAYER_ENTRIES_CACHE.keySet());
                List<UUID> originalkeys = new ArrayList<UUID>(SREClient.PLAYER_ENTRIES_CACHE.keySet());
                Collections.shuffle(keys);
                int i = 0;
                for (UUID o : originalkeys) {
                    SHUFFLED_PLAYER_ENTRIES_CACHE.put(o, keys.get(i));
                    i++;
                }
            }

            handleStalkerContinuousInput(client);

            if (abilityBind.consumeClick()) {
                ClientAbilityHandler.handler(client);
            }
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
            tooltipHelper(ModItems.THROWING_KNIFE, itemStack, list);
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
        // message.tip.skill_disabled
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
                            canDisplay = !playerMood.tasks.isEmpty();
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

    }

    private void ShowBroadcastMessage(Component message) {
        var client = Minecraft.getInstance();
        if (client == null)
            return;
        long timer = client.level.getGameTime();
        currentBroadcastMessage
                .add(new BroadcastMessageInfo(message, timer + GameConstants.getInTicks(0,
                        NoellesRolesConfig.HANDLER.instance().broadcasterMessageDuration)));
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
}