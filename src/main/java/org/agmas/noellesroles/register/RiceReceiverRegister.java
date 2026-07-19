package org.agmas.noellesroles.register;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.client.screen.DetectiveInspectScreenHandler;
import org.agmas.noellesroles.client.screen.PostmanScreenHandler;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.entity.LockEntityManager;
import org.agmas.noellesroles.game.roles.innocence.athlete.AthletePlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.ayayaya.AyayayaPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.boxer.BoxerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.detective.AgentPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.great_detective.GreatDetectivePlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.locksmith_inspiration.LocksmithInspirationComponent;
import org.agmas.noellesroles.game.roles.innocence.psychologist.PsychologistPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.singer.SingerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.super_star.SuperStarPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.telegrapher.TelegrapherPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.veteran.VeteranKnifeHandler;
import org.agmas.noellesroles.game.roles.killer.conspirator.ConspiratorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.trapper.TrapperPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.admirer.AdmirerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.GreatDetectiveRevealC2SPacket;
import org.agmas.noellesroles.role.BounsRoles;
import org.agmas.noellesroles.role.ModRoles;
import static org.agmas.noellesroles.RicesRoleRhapsody.*;

/**
 * Rice's Role Rhapsody 服务端网络包接收器注册，
 * 从 {@link org.agmas.noellesroles.RicesRoleRhapsody} 的 registerPackets() 中按类别剥离归一化而来。
 *
 * <p>数据包 Type 常量（CONSPIRATOR_PACKET 等）通过对
 * {@link org.agmas.noellesroles.RicesRoleRhapsody} 的静态导入解析。
 */
public class RiceReceiverRegister {

    private static final int VETERAN_DASH_COOLDOWN_TICKS = 60 * 20;
    private static final double VETERAN_DASH_SPEED = 1.25D;

    public static void registerReceivers() {
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
                if (!lockPick.is(ModItems.MASTER_KEY)) {
                    lockPick.shrink(1);
                }
            }
        });

        // 配钥
        ServerPlayNetworking.registerGlobalReceiver(KEY_FORGE_GAME_PACKET, (payload, context) -> {
            ServerPlayer player = context.player();
            int difficulty = payload.difficulty();
            if (difficulty < 1 || difficulty > 6) {
                return;
            }

            int inspirationCost;
            switch (difficulty) {
                case 1:
                    inspirationCost = 2;
                    break;
                case 2:
                    inspirationCost = 3;
                    break;
                case 3:
                    inspirationCost = 4;
                    break;
                case 4:
                    inspirationCost = 7;
                    break;
                case 5:
                    inspirationCost = 8;
                    break;
                case 6:
                    inspirationCost = 9;
                    break;
                default:
                    inspirationCost = 0;
                    break;
            }

            ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
            InteractionHand consumeHand = null;
            if (mainHand.is(ModItems.NOELL_KEY_BLANK)) {
                consumeHand = InteractionHand.MAIN_HAND;
            } else if (offHand.is(ModItems.NOELL_KEY_BLANK)) {
                consumeHand = InteractionHand.OFF_HAND;
            }
            if (consumeHand == null) {
                return;
            }

            LocksmithInspirationComponent inspirationComponent = ModComponents.LOCKSMITH_INSPIRATION.get(player);
            if (!player.isCreative() && !inspirationComponent.consumeInspiration(inspirationCost)) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.locksmith.inspiration_insufficient",
                                inspirationCost, inspirationComponent.getInspirationPoints())
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            ItemStack keyBlank = player.getItemInHand(consumeHand);
            if (!player.isCreative()) {
                keyBlank.shrink(1);
            }

            if (!payload.success()) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.key_forge.failed").withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            ItemStack reward = switch (difficulty) {
                case 1 -> ModItems.MASTER_KEY_P.getDefaultInstance();
                case 2 -> TMMItems.IRON_DOOR_KEY.getDefaultInstance();
                case 3 -> TMMItems.CROWBAR.getDefaultInstance();
                case 4 -> ModItems.MASTER_KEY.getDefaultInstance();
                case 5 -> TMMItems.LOCKPICK.getDefaultInstance();
                case 6 -> ModItems.NOELL_ARTISAN_KEY.getDefaultInstance();
                default -> ItemStack.EMPTY;
            };
            if (reward.isEmpty()) {
                return;
            }

            if (!player.addItem(reward)) {
                player.drop(reward, false);
            }
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.key_forge.success").withStyle(ChatFormatting.GREEN),
                    true);
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

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(context.player()))
                return;

            // 验证消息不为空
            if (payload.message() == null || payload.message().trim().isEmpty())
                return;

            // 模仿者使用电报员能力
            if (gameWorld.isRole(context.player(), ModRoles.IMITATOR)) {
                org.agmas.noellesroles.game.roles.killer.imitator.ImitatorPlayerComponent imitComp =
                        org.agmas.noellesroles.component.ModComponents.IMITATOR.get(context.player());
                imitComp.useMessageAbility(context.player(), payload.message());
                return;
            }

            // 验证玩家是电报员
            if (!gameWorld.isRole(context.player(), BounsRoles.TELEGRAPHER))
                return;

            // 获取电报员组件并发送消息
            TelegrapherPlayerComponent telegrapherComp = ModComponents.TELEGRAPHER.get(context.player());
            telegrapherComp.sendAnonymousMessage(payload.message());
        });

        // 处理射命丸文传递包
        ServerPlayNetworking.registerGlobalReceiver(POSTMAN_PACKET, (payload, context) -> {
            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 获取玩家的射命丸文组件
            AyayayaPlayerComponent postmanComp = ModComponents.AYAYAYA.get(context.player());

            // 根据不同操作处理（部分操作需要验证是否射命丸文角色）
            switch (payload.action()) {
                case OPEN_DELIVERY -> {
                    // 只有射命丸文才能发起传递
                    // if (!gameWorld.isRole(context.player(), ModRoles.POSTMAN)) return;

                    // 验证目标玩家存在且存活
                    Player target = context.player().level().getPlayerByUUID(payload.targetPlayer());
                    if (target == null || !GameUtils.isPlayerAliveAndSurvival(target))
                        return;

                    // 开始传递
                    postmanComp.startDelivery(payload.targetPlayer(), target.getName().getString());

                    // 通知目标玩家
                    AyayayaPlayerComponent targetComp = ModComponents.AYAYAYA.get(target);
                    targetComp.receiveDelivery(context.player().getUUID(), context.player().getName().getString());

                    // 打开射命丸文界面 - 使用 ExtendedScreenHandlerFactory 传递 UUID
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
                    AyayayaPlayerComponent targetComp = ModComponents.AYAYAYA.get(target);

                    // 确认交换 - 同步更新双方组件
                    boolean isPostman = !postmanComp.isReceiver;

                    // 更新自己的组件
                    if (isPostman) {
                        postmanComp.senderConfirmed = true;
                        targetComp.senderConfirmed = true; // 同步到对方
                    } else {
                        postmanComp.targetConfirmed = true;
                        targetComp.targetConfirmed = true; // 同步到对方
                    }
                    postmanComp.sync();
                    targetComp.sync();

                    // 检查是否双方都确认（使用自己组件中的状态）
                    if (postmanComp.senderConfirmed && postmanComp.targetConfirmed) {
                        // 执行交换
                        ItemStack postmanItem = postmanComp.putItem.copy();
                        ItemStack targetItem = postmanComp.targetItem.copy();

                        // 确定谁是射命丸文谁是接收方
                        Player postmanPlayer = isPostman ? context.player() : target;
                        Player receiverPlayer = isPostman ? target : context.player();

                        // 射命丸文收到接收方的物品，接收方收到射命丸文的物品
                        if (!targetItem.isEmpty()) {
                            postmanPlayer.addItem(targetItem);
                        }
                        if (!postmanItem.isEmpty()) {
                            receiverPlayer.addItem(postmanItem);
                        }

                        // 消耗射命丸文的传递盒
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

                    // 取消传递 - 射命丸文和接收方都可以取消
                    if (postmanComp.deliveryTarget != null) {
                        Player target = context.player().level().getPlayerByUUID(postmanComp.deliveryTarget);
                        if (target != null) {
                            AyayayaPlayerComponent targetComp = ModComponents.AYAYAYA.get(target);
                            targetComp.init();
                        }
                    }
                    postmanComp.init();
                }
            }
        });

        // 处理探员审查包
        ServerPlayNetworking.registerGlobalReceiver(DETECTIVE_PACKET, (payload, context) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 验证玩家是探员
            if (!gameWorld.isRole(context.player(), ModRoles.AGENT))
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 获取探员组件
            AgentPlayerComponent component = ModComponents.AGENT.get(context.player());

            // 检查技能冷却
            if (!component.canUseAbility()) {
                context.player()
                        .displayClientMessage(Component.translatable("message.noellesroles.detective.on_cooldown",
                                String.format("%.1f", component.getCooldownSeconds())), true);
                return;
            }

            // 获取玩家商店组件，检查金币
            SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(context.player());
            if (shopComponent.balance < AgentPlayerComponent.INSPECT_COST) {
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
            shopComponent.addToBalance(-AgentPlayerComponent.INSPECT_COST);

            // 设置冷却
            component.setCooldown(AgentPlayerComponent.INSPECT_COOLDOWN);

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

        // 处理大侦探"目标情况"包：记录某凶手与侦探当前的距离快照
        ServerPlayNetworking.registerGlobalReceiver(GreatDetectiveRevealC2SPacket.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());

            // 验证玩家是大侦探且存活
            if (!gameWorld.isRole(player, ModRoles.GREAT_DETECTIVE))
                return;
            if (!GameUtils.isPlayerAliveAndSurvival(player))
                return;
            if (payload.killer() == null)
                return;

            GreatDetectivePlayerComponent comp = GreatDetectivePlayerComponent.KEY.get(player);

            // 至少 3 条线索才能查明目标情况
            if (comp.clueCount(payload.killer()) < 3)
                return;
            // 已揭示则冻结，不再刷新（只显示触发时的距离）
            if (comp.hasRevealedDistance(payload.killer()))
                return;

            int distance = -1;
            Player killer = player.level().getPlayerByUUID(payload.killer());
            if (killer != null && killer.level() == player.level()
                    && GameUtils.isPlayerAliveAndSurvival(killer)) {
                distance = (int) Math.round(player.distanceTo(killer));
            }
            comp.setRevealedDistance(payload.killer(), distance);

            player.displayClientMessage(
                    Component.translatable("message.noellesroles.great_detective.target_locked")
                            .withStyle(ChatFormatting.AQUA),
                    true);
        });

        // 处理斗士技能包
        ServerPlayNetworking.registerGlobalReceiver(BOXER_ABILITY_PACKET, (payload, context) -> {
            if (RoleSkill.blockForSpectator(context.player()))
                return;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 验证玩家是斗士
            if (!gameWorld.isRole(context.player(), ModRoles.FIGHTER))
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 获取斗士组件
            BoxerPlayerComponent boxerComponent = ModComponents.FIGHTER.get(context.player());

            // 在服务端使用技能
            boxerComponent.useAbility();
            ConfigWorldComponent.onPlayerUsedSkill(context.player());
        });

        // 处理跟踪者窥视包
        ServerPlayNetworking.registerGlobalReceiver(STALKER_GAZE_PACKET, (payload, context) -> {
            if (RoleSkill.blockForSpectator(context.player()))
                return;
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
                ConfigWorldComponent.onPlayerUsedSkill(context.player());
            } else {
                stalkerComp.stopGazing();
            }
        });

        // 处理跟踪者突进包
        ServerPlayNetworking.registerGlobalReceiver(STALKER_DASH_PACKET, (payload, context) -> {
            if (RoleSkill.blockForSpectator(context.player()))
                return;
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
                ConfigWorldComponent.onPlayerUsedSkill(context.player());
            } else {
                stalkerComp.releaseCharge();
            }
        });

        // 处理运动员技能包
        ServerPlayNetworking.registerGlobalReceiver(ATHLETE_ABILITY_PACKET, (payload, context) -> {
            if (RoleSkill.blockForSpectator(context.player()))
                return;
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
            ConfigWorldComponent.onPlayerUsedSkill(context.player());
        });

        // 处理慕恋者窥视包
        ServerPlayNetworking.registerGlobalReceiver(ADMIRER_GAZE_PACKET, (payload, context) -> {
            if (RoleSkill.blockForSpectator(context.player()))
                return;
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
                ConfigWorldComponent.onPlayerUsedSkill(context.player());
            } else {
                admirerComp.stopGazing();
            }
        });

        // 处理设陷者技能包
        ServerPlayNetworking.registerGlobalReceiver(TRAPPER_PACKET, (payload, context) -> {
            if (RoleSkill.blockForSpectator(context.player()))
                return;
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
            ConfigWorldComponent.onPlayerUsedSkill(context.player());
        });

        // 处理设陷者切换陷阱类型包
        ServerPlayNetworking.registerGlobalReceiver(TRAPPER_SWITCH_PACKET, (payload, context) -> {
            if (RoleSkill.blockForSpectator(context.player()))
                return;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(context.player().level());

            // 验证玩家是设陷者
            if (!gameWorld.isRole(context.player(), ModRoles.TRAPPER))
                return;

            // 验证玩家存活
            if (!GameUtils.isPlayerAliveAndSurvival(context.player()))
                return;

            // 获取设陷者组件并切换陷阱类型
            TrapperPlayerComponent trapperComp = ModComponents.TRAPPER.get(context.player());
            trapperComp.switchTrapType();
            ConfigWorldComponent.onPlayerUsedSkill(context.player());
        });

        // 处理明星技能包
        ServerPlayNetworking.registerGlobalReceiver(STAR_ABILITY_PACKET, (payload, context) -> {
            if (RoleSkill.blockForSpectator(context.player()))
                return;
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
            ConfigWorldComponent.onPlayerUsedSkill(context.player());
        });

        // 处理歌手技能包
        ServerPlayNetworking.registerGlobalReceiver(SINGER_ABILITY_PACKET, (payload, context) -> {
            if (RoleSkill.blockForSpectator(context.player()))
                return;
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
            ConfigWorldComponent.onPlayerUsedSkill(context.player());
        });

        // 处理退伍军人持刀冲刺包
        ServerPlayNetworking.registerGlobalReceiver(VETERAN_DASH_PACKET, (payload, context) -> {
            handleVeteranDash(context.player());
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
            ConfigWorldComponent.onPlayerUsedSkill(context.player());
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
                        ConfigWorldComponent.onPlayerUsedSkill(context.player());
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

        // 抽奖/抽卡功能已禁用 —— 以下所有 Loot C2S 包处理器均为空操作
        ServerPlayNetworking.registerGlobalReceiver(LOOT_POOLS_INFO_REQUEST_PACKET, (payload, context) -> {});
        ServerPlayNetworking.registerGlobalReceiver(LOOT_POOLS_INFO_CHECK_CLIENT_PACKET, (payload, context) -> {});
        ServerPlayNetworking.registerGlobalReceiver(LOOT_REQUIRE_PACKET, (payload, context) -> {});
        ServerPlayNetworking.registerGlobalReceiver(LOOT_MULTI_REQUIRE_PACKET, (payload, context) -> {});
        ServerPlayNetworking.registerGlobalReceiver(LOOT_DATA_REFRESH_CLIENT_PACKET, (payload, context) -> {});
    }

    private static void handleVeteranDash(ServerPlayer player) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.VETERAN)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        if (!VeteranKnifeHandler.isHeldKnife(player.getMainHandItem())
                && !VeteranKnifeHandler.isHeldKnife(player.getOffhandItem())) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.veteran_dash.no_knife")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        if (ability.cooldown > 0) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.veteran_dash.cooldown",
                            String.format("%.1f", ability.cooldown / 20.0F))
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        Vec3 direction = getVeteranDashDirection(player);
        if (direction.lengthSqr() < 1.0E-4D) {
            return;
        }

        Vec3 current = player.getDeltaMovement();
        Vec3 dashMotion = new Vec3(
                direction.x * VETERAN_DASH_SPEED,
                current.y,
                direction.z * VETERAN_DASH_SPEED);
        player.setDeltaMovement(dashMotion);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), dashMotion));
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.7F, 1.4F);

        ability.setCooldown(VETERAN_DASH_COOLDOWN_TICKS);
        ConfigWorldComponent.onPlayerUsedSkill(player);
    }

    private static Vec3 getVeteranDashDirection(ServerPlayer player) {
        Vec3 movement = player.getDeltaMovement();
        Vec3 horizontal = new Vec3(movement.x, 0.0D, movement.z);
        if (horizontal.lengthSqr() < 0.0025D) {
            Vec3 look = player.getLookAngle();
            horizontal = new Vec3(look.x, 0.0D, look.z);
        }
        if (horizontal.lengthSqr() < 1.0E-4D) {
            return Vec3.ZERO;
        }
        return horizontal.normalize();
    }

    /**
     * 消耗射命丸文的传递盒
     * 在传递成功完成后调用
     *
     * @param postmanPlayer 射命丸文玩家
     */
    private static void consumeDeliveryBox(Player postmanPlayer) {
        // 先检查主手
        SREItemUtils.clearItem(postmanPlayer, ModItems.DELIVERY_BOX, 1);
    }
}
