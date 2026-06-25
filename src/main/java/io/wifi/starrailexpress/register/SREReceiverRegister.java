package io.wifi.starrailexpress.register;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.content.vote.VoteManager;
import io.wifi.starrailexpress.content.vote.network.VoteCastC2SPacket;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.*;
import io.wifi.starrailexpress.network.original.*;
import io.wifi.starrailexpress.network.packet.ModVersionPacket;
import io.wifi.starrailexpress.scenery.network.SceneAssetNetwork;
import org.agmas.noellesroles.game.modes.fourthroom.network.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端网络包接收器注册，从 {@link SRE#onInitialize()} 中按类别剥离归一化而来。
 */
public class SREReceiverRegister {

    public static void registerGlobalReceivers() {
        SceneAssetNetwork.registerServerReceivers();

        UpdateSkinSelectedPayload.registerReceiver();
        UpdateNameTagSelectedPayload.registerReceiver();
        // 服务端处理客户端投票包
        ServerPlayNetworking.registerGlobalReceiver(VoteCastC2SPacket.TYPE, (packet, context) -> {
            VoteManager.handleVoteCast(context.player(), packet.optionIndices());
        });
        ServerPlayNetworking.registerGlobalReceiver(KnifeStabPayload.ID, new KnifeStabPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(ModVersionPacket.ID, new ModVersionPacket.Receiver());
        // 全局战绩 / 回放查询请求
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.record.network.RecordListRequestC2SPayload.ID,
                (payload, context) -> net.exmo.sre.record.MatchRecordService.openListWindow(context.player(),
                        payload.offset(), payload.limit()));
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.record.network.RecordReplayRequestC2SPayload.ID,
                (payload, context) -> net.exmo.sre.record.MatchRecordService.openReplayFor(context.player(),
                        payload.matchId()));
        ServerPlayNetworking.registerGlobalReceiver(GunShootPayload.ID, new GunShootPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(SniperShootPayload.TYPE, new SniperShootPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(StoreBuyPayload.ID, new StoreBuyPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(NoteEditPayload.ID, new NoteEditPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(RequestOpenClueArchivePayload.ID,
                new RequestOpenClueArchivePayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.network.VoteForMapPayload.ID,
                (payload, context) -> {
                    io.wifi.starrailexpress.network.VoteForMapPayload.Handler.handle(payload, context.player());
                });

        // 实体交互方块服务端网络处理
        EntityInteractionBlockServerNetwork.register();
        MinigameQuestServerNetwork.register();
        TicketOfficeServerNetwork.register();
        EffectGeneratorServerNetwork.register();
        // 画板服务端网络处理
        DrawingBoardServerNetwork.register();
        ServerPlayNetworking.registerGlobalReceiver(SecurityCameraExitRequestPayload.ID,
                new SecurityCameraExitRequestPayload.ServerReceiver());
        ServerPlayNetworking.registerGlobalReceiver(JoinSpecGroupPayload.ID, (payload, context) -> {
            joinVoice(payload, context);

        });
        ServerPlayNetworking.registerGlobalReceiver(NunchuckHitPayload.ID, new NunchuckHitPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(CardPlayPayload.ID, new CardPlayPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(BuyFourthRoomItemPayload.ID,
                new BuyFourthRoomItemPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(RevealIdentityPayload.ID, new RevealIdentityPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(CompleteFourthRoomTaskPayload.ID,
                new CompleteFourthRoomTaskPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(EndTurnPayload.ID, new EndTurnPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(UseAssassinationItemPayload.ID,
                new UseAssassinationItemPayload.Receiver());

        // Role Rotation receivers
        RoleRotationSelectC2SPacket.registerServerReceiver();

        // 职业轮换系统：管理员编辑名单
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.network.RoleRosterUpdatePayload.ID,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> {
                        if (!player.hasPermissions(2)) {
                            return;
                        }
                        switch (payload.action()) {
                            case "set" -> io.wifi.starrailexpress.roster.RoleRosterManager.setFromJson(payload.json());
                            case "enable" -> io.wifi.starrailexpress.roster.RoleRosterManager.setEnabled(true);
                            case "disable" -> io.wifi.starrailexpress.roster.RoleRosterManager.setEnabled(false);
                            case "clear" -> io.wifi.starrailexpress.roster.RoleRosterManager.clear();
                            case "randomize" -> {
                                int count = context.server().getPlayerCount();
                                try {
                                    if (payload.json() != null && !payload.json().isBlank()) {
                                        count = Integer.parseInt(payload.json().trim());
                                    }
                                } catch (NumberFormatException ignored) {
                                    // 使用在线人数
                                }
                                io.wifi.starrailexpress.roster.RoleRosterManager.randomize(Math.max(1, count));
                            }
                            default -> {
                            }
                        }
                    });
                });

        // Mailbox receivers
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailClaimC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailClaimC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailDeleteC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailDeleteC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailClaimAllC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailClaimAllC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailDeleteAllReadC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailDeleteAllReadC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailMarkReadC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailMarkReadC2SPayload.Receiver());

        // Chat Dialogue advance handler
        ServerPlayNetworking.registerGlobalReceiver(
                net.exmo.sre.client.chat.ChatDialogueAdvancePayload.ID, (payload, context) -> {
                    var mgr = net.exmo.sre.client.chat.ChatDialogueManager
                            .getInstance(context.player().getServer());
                    var data = mgr.get(payload.dialogueId());
                    if (data == null)
                        return;
                    int idx = payload.lineIndex();
                    if (idx < 0 || idx >= data.lines.size())
                        return;
                    var line = data.lines.get(idx);

                    if (payload.choiceIndex() >= 0) {
                        if (!line.hasChoices())
                            return;
                        int choiceIndex = payload.choiceIndex();
                        if (choiceIndex < 0 || choiceIndex >= line.choices.size())
                            return;

                        var choice = line.choices.get(choiceIndex);
                        executeDialogueCommand(context, choice.command, choice.runsOnServer());

                        if (choice.opensDialogue()) {
                            var nextDialogue = mgr.get(choice.nextDialogue);
                            if (nextDialogue != null) {
                                net.exmo.sre.client.chat.OpenChatDialoguePayload.sendToPlayer(
                                        context.player(), nextDialogue, payload.focusEntityId());
                            } else {
                                SRE.LOGGER.warn("[SRE-Chat] Missing next dialogue '{}' from '{}' line {} choice {}",
                                        choice.nextDialogue, payload.dialogueId(), idx, choiceIndex);
                            }
                        }
                        return;
                    }

                    executeDialogueCommand(context, line.command, line.runsOnServer());
                });
    }

    private static void executeDialogueCommand(ServerPlayNetworking.Context context, String command,
            boolean runOnServer) {
        if (!runOnServer || command == null || command.isBlank())
            return;
        context.player().getServer().getCommands()
                .performPrefixedCommand(
                        context.player().createCommandSourceStack()
                                .withPermission(2)
                                .withSuppressedOutput(),
                        command);
    }

    private static void joinVoice(JoinSpecGroupPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sp = context.player();
        boolean isJoin = payload.isJoin();
        if (isJoin) {
            if (GameUtils.isPlayerSpectator(sp)) {
                TrainVoicePlugin.addPlayer(sp.getUUID());
            }
        } else {
            TrainVoicePlugin.resetPlayer(sp.getUUID());
        }
    }
}
