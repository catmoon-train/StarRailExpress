package org.agmas.noellesroles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.TMMItemUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.component.*;
import org.agmas.noellesroles.entity.LockEntityManager;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModDescItems;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.*;
import org.agmas.noellesroles.packet.Loot.LootPoolsInfoRequestC2SPacket;
import org.agmas.noellesroles.packet.Loot.LootPoolsInfoS2CPacket;
import org.agmas.noellesroles.packet.Loot.LootRequestC2SPacket;
import org.agmas.noellesroles.packet.Loot.LootResultS2CPacket;
import org.agmas.noellesroles.packet.Loot.LootMultiRequestC2SPacket;
import org.agmas.noellesroles.packet.Loot.LootMultiResultS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.screen.DetectiveInspectScreenHandler;
import org.agmas.noellesroles.screen.ModScreenHandlers;
import org.agmas.noellesroles.screen.PostmanScreenHandler;
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
    public static final CustomPacketPayload.Type<StarAbilityC2SPacket> STAR_ABILITY_PACKET = StarAbilityC2SPacket.ID;
    public static final CustomPacketPayload.Type<SingerAbilityC2SPacket> SINGER_ABILITY_PACKET = SingerAbilityC2SPacket.ID;
    public static final CustomPacketPayload.Type<PsychologistC2SPacket> PSYCHOLOGIST_PACKET = PsychologistC2SPacket.ID;
    public static final CustomPacketPayload.Type<PuppeteerC2SPacket> PUPPETEER_PACKET = PuppeteerC2SPacket.ID;

    public static final CustomPacketPayload.Type<LockGameC2Packet> LOCK_GAME_PACKET = LockGameC2Packet.ID;
    public static final CustomPacketPayload.Type<LootRequestC2SPacket> LOOT_REQUIRE_PACKET = LootRequestC2SPacket.ID;
    public static final CustomPacketPayload.Type<LootMultiRequestC2SPacket> LOOT_MULTI_REQUIRE_PACKET = LootMultiRequestC2SPacket.ID;
    public static final CustomPacketPayload.Type<LootPoolsInfoRequestC2SPacket> LOOT_POOLS_INFO_REQUEST_PACKET = LootPoolsInfoRequestC2SPacket.ID;

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
        ModItems.init();

        ModEffects.init();

        FunnyItems.init();

        ModDescItems.init();
        // 4. 注册实体
        ModEntities.init();

        // 5. 注册 ScreenHandlers
        ModScreenHandlers.init();

        // 6. 初始化商店

        // 7. 注册网络包（如果有自定义技能需要客户端-服务端通信）
        registerPackets();

        // 8. 注册事件监听
        registerEvents();

        // 9. 加载配置（如果使用 YACL）
        // ModConfig.HANDLER.load();

        // 10. 注册傀儡师尸体收集事件
        registerPuppeteerBodyCollect();
    }

    /**
     * 注册傀儡师尸体收集事件
     * 使用 Fabric API 的 UseEntityCallback 代替 Mixin
     */
    private static void registerPuppeteerBodyCollect() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            // 只在服务端处理
            if (world.isClientSide())
                return net.minecraft.world.InteractionResult.PASS;

            // 检查实体是否是玩家尸体
            if (!(entity instanceof PlayerBodyEntity body))
                return net.minecraft.world.InteractionResult.PASS;

            // 检查玩家是否存活
            if (!GameUtils.isPlayerAliveAndSurvival(player))
                return net.minecraft.world.InteractionResult.PASS;

            // 检查玩家是否是傀儡师
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
            if (gameWorld.isRole(player, ModRoles.DIO)) {
                DIOPlayerComponent dioPlayerComponent = DIOPlayerComponent.KEY.get(player);
                boolean success = dioPlayerComponent.feedOnCorpse(body);
                if (success) {
                    dioPlayerComponent.sync();
                    if (dioPlayerComponent.isFinalCarnivalActive) {
                        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                        dioPlayerComponent.extendTempLife();
                    }
                }
            }
            if (!gameWorld.isRole(player, ModRoles.PUPPETEER))
                return net.minecraft.world.InteractionResult.PASS;

            // 获取傀儡师组件
            PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(player);

            // 检查是否可以回收（阶段一且不在冷却中）
            if (!puppeteerComp.canCollectBody())
                return net.minecraft.world.InteractionResult.PASS;

            // 获取尸体对应的玩家UUID
            java.util.UUID bodyOwnerUuid = body.getPlayerUuid();

            // 获取游戏总人数
            int totalPlayers = 1;
            if (world instanceof net.minecraft.server.level.ServerLevel serverWorld) {
                totalPlayers = serverWorld.players().size();
            }

            // 回收尸体
            puppeteerComp.collectBody(bodyOwnerUuid, totalPlayers);

            // 让尸体消失
            body.discard();

            return net.minecraft.world.InteractionResult.SUCCESS;
        });
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

        // 注册阴谋家猜测包
        PayloadTypeRegistry.playC2S().register(ConspiratorC2SPacket.ID, ConspiratorC2SPacket.CODEC);

        // 注册电报员消息包
        PayloadTypeRegistry.playC2S().register(TelegrapherC2SPacket.ID, TelegrapherC2SPacket.CODEC);

        // 注册邮差传递包
        PayloadTypeRegistry.playC2S().register(PostmanC2SPacket.ID, PostmanC2SPacket.CODEC);

        // 注册私家侦探审查包
        PayloadTypeRegistry.playC2S().register(DetectiveC2SPacket.ID, DetectiveC2SPacket.CODEC);

        // 注册拳击手技能包
        PayloadTypeRegistry.playC2S().register(BoxerAbilityC2SPacket.ID, BoxerAbilityC2SPacket.CODEC);

        // 注册跟踪者窥视包
        PayloadTypeRegistry.playC2S().register(StalkerGazeC2SPacket.ID, StalkerGazeC2SPacket.CODEC);

        // 注册跟踪者突进包
        PayloadTypeRegistry.playC2S().register(StalkerDashC2SPacket.ID, StalkerDashC2SPacket.CODEC);

        // 注册运动员技能包
        PayloadTypeRegistry.playC2S().register(AthleteAbilityC2SPacket.ID, AthleteAbilityC2SPacket.CODEC);

        // 注册慕恋者窥视包
        PayloadTypeRegistry.playC2S().register(AdmirerGazeC2SPacket.ID, AdmirerGazeC2SPacket.CODEC);

        // 注册设陷者技能包
        PayloadTypeRegistry.playC2S().register(TrapperC2SPacket.ID, TrapperC2SPacket.CODEC);

        // 注册明星技能包
        PayloadTypeRegistry.playC2S().register(StarAbilityC2SPacket.ID, StarAbilityC2SPacket.CODEC);

        // 注册歌手技能包
        PayloadTypeRegistry.playC2S().register(SingerAbilityC2SPacket.ID, SingerAbilityC2SPacket.CODEC);

        // 注册心理学家技能包
        PayloadTypeRegistry.playC2S().register(PsychologistC2SPacket.ID, PsychologistC2SPacket.CODEC);

        // 注册傀儡师技能包
        PayloadTypeRegistry.playC2S().register(PuppeteerC2SPacket.ID, PuppeteerC2SPacket.CODEC);

        // 注册撬锁小游戏完成包
        PayloadTypeRegistry.playC2S().register(LockGameC2Packet.ID, LockGameC2Packet.CODEC);

        // 注册卡池信息请求包
        PayloadTypeRegistry.playC2S().register(LootPoolsInfoRequestC2SPacket.ID, LootPoolsInfoRequestC2SPacket.CODEC);
        // 注册抽奖请求包
        PayloadTypeRegistry.playC2S().register(LootRequestC2SPacket.ID, LootRequestC2SPacket.CODEC);
        // 注册五连抽请求包
        PayloadTypeRegistry.playC2S().register(LootMultiRequestC2SPacket.ID, LootMultiRequestC2SPacket.CODEC);

        // 撬锁
        ServerPlayNetworking.registerGlobalReceiver(LOCK_GAME_PACKET, (payload, context) -> {
            ServerPlayer player = context.player();
            ItemStack lockPick = player.getItemInHand(InteractionHand.MAIN_HAND);
            boolean isLockPick = false;
            for (var item : LockEntityManager.getInstance().getCanBeUsedToUnLock()) {
                if (lockPick.is(item)) {
                    isLockPick = true;
                    break;
                }
            }
            if (payload.result()) {
                context.player().displayClientMessage(
                        Component.translatable("message.lock.unlock").withStyle(ChatFormatting.GREEN), true);
                // context.player().playSound(SoundEvents.ANVIL_PLACE, 1f, 2f);
                context.server().execute(() -> {
                    context.player().playNotifySound(SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 2f);
                });
                LockEntityManager.getInstance().removeLockEntity(payload.pos(), payload.entityId());
                // 把锁附近的门解锁（如果还有锁则不会成功解锁）
                Level world = context.player().level();
                BlockEntity blockEntity = world.getBlockEntity(payload.pos().below());
                if (blockEntity instanceof DoorBlockEntity door) {
                    LockEntityManager.lockNearByDoors(door, world, false);
                }

            } else if (isLockPick) {
                context.server().execute(() -> {
                    context.player().playNotifySound(SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 0.5f, 1f);
                });
                context.player().displayClientMessage(
                        Component.translatable("message.lock.failed").withStyle(ChatFormatting.RED), true);
                lockPick.shrink(1);
            }
        });

        // 处理阴谋家猜测包
        ServerPlayNetworking.registerGlobalReceiver(CONSPIRATOR_PACKET, (payload, context) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 验证玩家是阴谋家
            if (!gameWorld.isRole(context.player(), ModRoles.CONSPIRATOR))
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 验证目标玩家
            if (payload.targetPlayer() == null)
                return;
            Player target = context.player().level().getPlayerByUUID(payload.targetPlayer());
            if (target == null)
                return;

            // 验证角色 ID
            if (payload.roleId() == null || payload.roleId().isEmpty())
                return;
            ResourceLocation roleId = ResourceLocation.tryParse(payload.roleId());
            if (roleId == null)
                return;

            // 验证玩家持有书页物品
            ItemStack mainHand = context.player().getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack offHand = context.player().getItemInHand(InteractionHand.OFF_HAND);
            boolean hasPage = mainHand.is(ModItems.CONSPIRACY_PAGE) || offHand.is(ModItems.CONSPIRACY_PAGE);

            if (!hasPage)
                return;

            // 执行猜测
            ConspiratorPlayerComponent component = ModComponents.CONSPIRATOR.get(context.player());
            boolean correct = component.makeGuess(payload.targetPlayer(), roleId);
            if (correct) {
                // 防止警告罢了
            }
            // 消耗书页物品
            if (mainHand.is(ModItems.CONSPIRACY_PAGE)) {
                mainHand.shrink(1);
            } else if (offHand.is(ModItems.CONSPIRACY_PAGE)) {
                offHand.shrink(1);
            }
        });

        // 处理电报员消息包
        ServerPlayNetworking.registerGlobalReceiver(TELEGRAPHER_PACKET, (payload, context) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 验证玩家是电报员
            if (!gameWorld.isRole(context.player(), ModRoles.TELEGRAPHER))
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 验证消息不为空
            if (payload.message() == null || payload.message().trim().isEmpty())
                return;

            // 获取电报员组件并发送消息
            TelegrapherPlayerComponent telegrapherComp = ModComponents.TELEGRAPHER.get(context.player());
            telegrapherComp.sendAnonymousMessage(payload.message());
        });

        // 处理邮差传递包
        ServerPlayNetworking.registerGlobalReceiver(POSTMAN_PACKET, (payload, context) -> {
            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 获取玩家的邮差组件
            PostmanPlayerComponent postmanComp = ModComponents.POSTMAN.get(context.player());

            // 根据不同操作处理（部分操作需要验证是否邮差角色）
            switch (payload.action()) {
                case OPEN_DELIVERY -> {
                    // 只有邮差才能发起传递
                    // if (!gameWorld.isRole(context.player(), ModRoles.POSTMAN)) return;

                    // 验证目标玩家存在且存活
                    Player target = context.player().level().getPlayerByUUID(payload.targetPlayer());
                    if (target == null || !GameUtils.isPlayerAliveAndSurvival(target))
                        return;

                    // 开始传递
                    postmanComp.startDelivery(payload.targetPlayer(), target.getName().getString());

                    // 通知目标玩家
                    PostmanPlayerComponent targetComp = ModComponents.POSTMAN.get(target);
                    targetComp.receiveDelivery(context.player().getUUID(), context.player().getName().getString());

                    // 打开邮差界面 - 使用 ExtendedScreenHandlerFactory 传递 UUID
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        serverPlayer.openMenu(
                                new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<java.util.UUID>() {
                                    @Override
                                    public Component getDisplayName() {
                                        return Component.translatable("screen.noellesroles.postman.title");
                                    }

                                    @Override
                                    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int syncId,
                                            net.minecraft.world.entity.player.Inventory playerInventory,
                                            Player player) {
                                        return new PostmanScreenHandler(syncId, playerInventory,
                                                payload.targetPlayer());
                                    }

                                    @Override
                                    public java.util.UUID getScreenOpeningData(ServerPlayer player) {
                                        return payload.targetPlayer();
                                    }
                                });
                    }

                    // 同时为目标玩家打开界面
                    if (target instanceof ServerPlayer serverTarget) {
                        final java.util.UUID postmanUuid = context.player().getUUID();
                        serverTarget.openMenu(
                                new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<java.util.UUID>() {
                                    @Override
                                    public Component getDisplayName() {
                                        return Component.translatable("screen.noellesroles.postman.title");
                                    }

                                    @Override
                                    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int syncId,
                                            net.minecraft.world.entity.player.Inventory playerInventory,
                                            Player player) {
                                        return new PostmanScreenHandler(syncId, playerInventory, postmanUuid);
                                    }

                                    @Override
                                    public java.util.UUID getScreenOpeningData(ServerPlayer player) {
                                        return postmanUuid;
                                    }
                                });
                    }
                }
                case SET_ITEM -> {
                    // 验证玩家有有效的传递会话
                    if (!postmanComp.isDeliveryActive())
                        return;

                    // 放入物品
                    postmanComp.setItem(payload.item(), !postmanComp.isReceiver);
                }
                case CONFIRM -> {
                    // 验证玩家有有效的传递会话
                    if (!postmanComp.isDeliveryActive())
                        return;

                    // 获取对方组件
                    if (postmanComp.deliveryTarget == null)
                        return;
                    Player target = context.player().level().getPlayerByUUID(postmanComp.deliveryTarget);
                    if (target == null)
                        return;
                    PostmanPlayerComponent targetComp = ModComponents.POSTMAN.get(target);

                    // 确认交换 - 同步更新双方组件
                    boolean isPostman = !postmanComp.isReceiver;

                    // 更新自己的组件
                    if (isPostman) {
                        postmanComp.postmanConfirmed = true;
                        targetComp.postmanConfirmed = true; // 同步到对方
                    } else {
                        postmanComp.targetConfirmed = true;
                        targetComp.targetConfirmed = true; // 同步到对方
                    }
                    postmanComp.sync();
                    targetComp.sync();

                    // 检查是否双方都确认（使用自己组件中的状态）
                    if (postmanComp.postmanConfirmed && postmanComp.targetConfirmed) {
                        // 执行交换
                        ItemStack postmanItem = postmanComp.postmanItem.copy();
                        ItemStack targetItem = postmanComp.targetItem.copy();

                        // 确定谁是邮差谁是接收方
                        Player postmanPlayer = isPostman ? context.player() : target;
                        Player receiverPlayer = isPostman ? target : context.player();

                        // 邮差收到接收方的物品，接收方收到邮差的物品
                        if (!targetItem.isEmpty()) {
                            postmanPlayer.addItem(targetItem);
                        }
                        if (!postmanItem.isEmpty()) {
                            receiverPlayer.addItem(postmanItem);
                        }

                        // 消耗邮差的传递盒
                        consumeDeliveryBox(postmanPlayer);

                        // 重置双方状态（这会触发 isDeliveryActive() 返回 false）
                        postmanComp.init();
                        targetComp.init();

                        // 关闭双方界面
                        if (context.player() instanceof ServerPlayer serverPlayer) {
                            serverPlayer.closeContainer();
                        }
                        if (target instanceof ServerPlayer serverTarget) {
                            serverTarget.closeContainer();
                        }
                    }
                }
                case CANCEL -> {
                    // 验证玩家有有效的传递会话
                    if (!postmanComp.isDeliveryActive())
                        return;

                    // 取消传递 - 邮差和接收方都可以取消
                    if (postmanComp.deliveryTarget != null) {
                        Player target = context.player().level().getPlayerByUUID(postmanComp.deliveryTarget);
                        if (target != null) {
                            PostmanPlayerComponent targetComp = ModComponents.POSTMAN.get(target);
                            targetComp.init();
                        }
                    }
                    postmanComp.init();
                }
            }
        });

        // 处理私家侦探审查包
        ServerPlayNetworking.registerGlobalReceiver(DETECTIVE_PACKET, (payload, context) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 验证玩家是私家侦探
            if (!gameWorld.isRole(context.player(), ModRoles.DETECTIVE))
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 获取私家侦探组件
            DetectivePlayerComponent component = ModComponents.DETECTIVE.get(context.player());

            // 检查技能冷却
            if (!component.canUseAbility()) {
                context.player()
                        .displayClientMessage(Component.translatable("message.noellesroles.detective.on_cooldown",
                                String.format("%.1f", component.getCooldownSeconds())), true);
                return;
            }

            // 获取玩家商店组件，检查金币
            SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(context.player());
            if (shopComponent.balance < DetectivePlayerComponent.INSPECT_COST) {
                context.player().displayClientMessage(
                        Component.translatable("message.noellesroles.detective.insufficient_funds"), true);
                return;
            }

            // 验证目标玩家
            Player target = context.player().level().getPlayerByUUID(payload.targetUuid());
            if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
                context.player().displayClientMessage(
                        Component.translatable("message.noellesroles.detective.invalid_target"), true);
                return;
            }

            // 不能审查自己
            if (target.getUUID().equals(context.player().getUUID())) {
                context.player().displayClientMessage(
                        Component.translatable("message.noellesroles.detective.cannot_inspect_self"), true);
                return;
            }

            // 扣除金币
            shopComponent.addToBalance(-DetectivePlayerComponent.INSPECT_COST);

            // 设置冷却
            component.setCooldown(DetectivePlayerComponent.INSPECT_COOLDOWN);

            // 开始审查
            component.startInspecting((ServerPlayer) target);

            // 打开只读的侦探审查界面
            if (context.player() instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(
                        new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<java.util.UUID>() {
                            @Override
                            public Component getDisplayName() {
                                return Component.translatable("container.noellesroles.detective.inspect",
                                        target.getName());
                            }

                            @Override
                            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int syncId,
                                    net.minecraft.world.entity.player.Inventory playerInventory, Player player) {
                                return new DetectiveInspectScreenHandler(syncId, playerInventory,
                                        (ServerPlayer) target);
                            }

                            @Override
                            public java.util.UUID getScreenOpeningData(ServerPlayer player) {
                                return target.getUUID();
                            }
                        });
            }
        });

        // 处理拳击手技能包
        ServerPlayNetworking.registerGlobalReceiver(BOXER_ABILITY_PACKET, (payload, context) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 验证玩家是拳击手
            if (!gameWorld.isRole(context.player(), ModRoles.BOXER))
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 获取拳击手组件
            BoxerPlayerComponent boxerComponent = ModComponents.BOXER.get(context.player());

            // 在服务端使用技能
            boxerComponent.useAbility();
        });

        // 处理跟踪者窥视包
        ServerPlayNetworking.registerGlobalReceiver(STALKER_GAZE_PACKET, (payload, context) -> {
            // 获取跟踪者组件
            StalkerPlayerComponent stalkerComp = ModComponents.STALKER.get(context.player());

            // 验证是跟踪者
            if (!stalkerComp.isActiveStalker())
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 只有一阶段和二阶段能使用窥视
            if (stalkerComp.phase > 2)
                return;

            if (payload.gazing()) {
                stalkerComp.startGazing();
            } else {
                stalkerComp.stopGazing();
            }
        });

        // 处理跟踪者突进包
        ServerPlayNetworking.registerGlobalReceiver(STALKER_DASH_PACKET, (payload, context) -> {
            // 获取跟踪者组件
            StalkerPlayerComponent stalkerComp = ModComponents.STALKER.get(context.player());

            // 验证是跟踪者
            if (!stalkerComp.isActiveStalker())
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 只有三阶段能使用突进
            if (stalkerComp.phase != 3 || !stalkerComp.dashModeActive)
                return;

            if (payload.charging()) {
                stalkerComp.startCharging();
            } else {
                stalkerComp.releaseCharge();
            }
        });

        // 处理运动员技能包
        ServerPlayNetworking.registerGlobalReceiver(ATHLETE_ABILITY_PACKET, (payload, context) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 验证玩家是运动员
            if (!gameWorld.isRole(context.player(), ModRoles.ATHLETE))
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 获取运动员组件
            AthletePlayerComponent athleteComponent = ModComponents.ATHLETE.get(context.player());

            // 在服务端使用技能
            athleteComponent.useAbility();
        });

        // 处理慕恋者窥视包
        ServerPlayNetworking.registerGlobalReceiver(ADMIRER_GAZE_PACKET, (payload, context) -> {
            // 获取慕恋者组件
            AdmirerPlayerComponent admirerComp = ModComponents.ADMIRER.get(context.player());

            // 验证是慕恋者
            if (!admirerComp.isActiveAdmirer())
                return;
            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            if (payload.gazing()) {
                admirerComp.startGazing();
            } else {
                admirerComp.stopGazing();
            }
        });

        // 处理设陷者技能包
        ServerPlayNetworking.registerGlobalReceiver(TRAPPER_PACKET, (payload, context) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 验证玩家是设陷者
            if (!gameWorld.isRole(context.player(), ModRoles.TRAPPER))
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 获取设陷者组件并尝试放置陷阱
            TrapperPlayerComponent trapperComp = ModComponents.TRAPPER.get(context.player());
            trapperComp.tryPlaceTrap();
        });

        // 处理明星技能包
        ServerPlayNetworking.registerGlobalReceiver(STAR_ABILITY_PACKET, (payload, context) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 验证玩家是明星
            if (!gameWorld.isRole(context.player(), ModRoles.SUPERSTAR))
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 获取明星组件并使用技能
            SuperStarPlayerComponent starComp = ModComponents.STAR.get(context.player());
            starComp.useAbility();
        });

        // 处理歌手技能包
        ServerPlayNetworking.registerGlobalReceiver(SINGER_ABILITY_PACKET, (payload, context) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 验证玩家是歌手
            if (!gameWorld.isRole(context.player(), ModRoles.SINGER))
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 获取歌手组件并使用技能
            SingerPlayerComponent singerComp = ModComponents.SINGER.get(context.player());
            singerComp.useAbility();
        });

        // 处理心理学家治疗包
        ServerPlayNetworking.registerGlobalReceiver(PSYCHOLOGIST_PACKET, (payload, context) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 验证玩家是心理学家
            if (!gameWorld.isRole(context.player(), ModRoles.PSYCHOLOGIST))
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 验证目标玩家
            Player target = context.player().level().getPlayerByUUID(payload.targetUuid());
            if (target == null) {
                context.player().displayClientMessage(
                        Component.translatable("message.noellesroles.psychologist.invalid_target"), true);
                return;
            }

            // 获取心理学家组件并开始治疗
            PsychologistPlayerComponent psychComp = ModComponents.PSYCHOLOGIST.get(context.player());
            psychComp.startHealing(target);
        });

        // 处理傀儡师技能包
        ServerPlayNetworking.registerGlobalReceiver(PUPPETEER_PACKET, (payload, context) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 获取傀儡师组件
            PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(context.player());

            // 验证玩家是傀儡师（通过角色检查或组件检查，与客户端保持一致）
            boolean isPuppeteer = gameWorld.isRole(context.player(), ModRoles.PUPPETEER);
            boolean isActivePuppeteer = puppeteerComp.isActivePuppeteer();

            if (!isPuppeteer && !isActivePuppeteer) {
                return;
            }

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            switch (payload.action()) {
                case USE_PUPPET -> {
                    // 使用假人技能 - 详细验证在 usePuppetAbility() 中处理
                    if (puppeteerComp.phase == 2) {
                        puppeteerComp.usePuppetAbility();
                    }
                }
                case RETURN_TO_BODY -> {
                    // 主动返回本体
                    if (puppeteerComp.isControllingPuppet) {
                        puppeteerComp.returnToBody(false);
                    }
                }
            }
        });

        // 处理卡池信息请求包：返回缺失的卡池
        ServerPlayNetworking.registerGlobalReceiver(LOOT_POOLS_INFO_REQUEST_PACKET, (payload, context) -> {
            List<LotteryManager.LotteryPool> missingPools = new ArrayList<>();
            for (int poolID : payload.poolIds()) {
                LotteryManager.LotteryPool lotteryPool = LotteryManager.getInstance().getLotteryPool(poolID);
                if (lotteryPool != null)
                    missingPools.add(lotteryPool);
            }
            ServerPlayNetworking.send(context.player(), new LootPoolsInfoS2CPacket(missingPools));
        });

        // 处理抽奖请求包
        ServerPlayNetworking.registerGlobalReceiver(LOOT_REQUIRE_PACKET, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player == null)
                return;
            if (LotteryManager.getInstance().getLotteryPool(payload.poolID()) != null
                    && LotteryManager.getInstance().canRoll(player)) {
                Pair<Integer, Integer> rollID = LotteryManager.getInstance().getLotteryPool(payload.poolID())
                        .rollOnce(player);
                if (rollID.first != -1) {
                    ServerPlayNetworking.send(player,
                            new LootResultS2CPacket(payload.poolID(), rollID.first, rollID.second));
                    // 抽一次减一次抽奖机会
                    LotteryManager.getInstance().addOrDegreeLotteryChance(player, -1);
                }
            } else {
                // 抽奖次数 = 0 或 卡池是否存在 限制
                player.sendSystemMessage(Component.translatable("message.noellesroles.loot.limit", payload.poolID()));
            }
        });

        // 处理五连抽请求包
        ServerPlayNetworking.registerGlobalReceiver(LOOT_MULTI_REQUIRE_PACKET, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player == null)
                return;
            int count = Math.min(payload.count(), 5);
            if (count <= 0)
                return;
            LotteryManager.LotteryPool pool = LotteryManager.getInstance().getLotteryPool(payload.poolID());
            if (pool == null) {
                player.sendSystemMessage(Component.translatable("message.noellesroles.loot.limit", payload.poolID()));
                return;
            }
            java.util.List<int[]> results = new java.util.ArrayList<>();
            for (int i = 0; i < count; ++i) {
                if (!LotteryManager.getInstance().canRoll(player))
                    break;
                Pair<Integer, Integer> rollID = pool.rollOnce(player);
                if (rollID.first != -1) {
                    results.add(new int[]{rollID.first, rollID.second});
                    LotteryManager.getInstance().addOrDegreeLotteryChance(player, -1);
                }
            }
            if (!results.isEmpty()) {
                ServerPlayNetworking.send(player, new LootMultiResultS2CPacket(payload.poolID(), results));
            } else {
                player.sendSystemMessage(Component.translatable("message.noellesroles.loot.limit", payload.poolID()));
            }
        });
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

        // ==================== 滑头鬼角色处理 ====================
        if (role.equals(ModRoles.SLIPPERY_GHOST)) {
            // 重置滑头鬼组件
            SlipperyGhostPlayerComponent slipperyGhostComponent = ModComponents.SLIPPERY_GHOST.get(player);
            slipperyGhostComponent.init();
        }

        // ==================== 工程师角色处理 ====================
        if (role.equals(ModRoles.ENGINEER)) {
            // 工程师不需要特殊组件，只需要商店访问权限
            // 商店逻辑在 EngineerShopMixin 中处理
        }

        // ==================== 拳击手角色处理 ====================
        if (role.equals(ModRoles.BOXER)) {
            // 重置拳击手组件 - 设置开局冷却
            BoxerPlayerComponent boxerComponent = ModComponents.BOXER.get(player);
            boxerComponent.init();
        }

        // ==================== 邮差角色处理 ====================
        if (role.equals(ModRoles.POSTMAN)) {
            // 重置邮差组件
            PostmanPlayerComponent postmanComponent = ModComponents.POSTMAN.get(player);
            postmanComponent.init();
        }

        // ==================== 私家侦探角色处理 ====================
        if (role.equals(ModRoles.DETECTIVE)) {
            // 重置私家侦探组件
            DetectivePlayerComponent detectiveComponent = ModComponents.DETECTIVE.get(player);
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
            // 给予一把刀
            player.addItem(new ItemStack(TMMItems.KNIFE));
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
     * 消耗邮差的传递盒
     * 在传递成功完成后调用
     *
     * @param postmanPlayer 邮差玩家
     */
    private static void consumeDeliveryBox(Player postmanPlayer) {
        // 先检查主手
        TMMItemUtils.clearItem(postmanPlayer, ModItems.DELIVERY_BOX, 1);
    }

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