package io.wifi.starrailexpress.register;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.ReplayPayload;
import io.wifi.starrailexpress.content.vote.network.VoteCastC2SPacket;
import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
import io.wifi.starrailexpress.network.*;
import io.wifi.starrailexpress.network.original.*;
import io.wifi.starrailexpress.network.packet.CustomNarratorPacket;
import io.wifi.starrailexpress.network.packet.ModVersionPacket;
import io.wifi.starrailexpress.network.packet.SyncRoomToPlayerPayload;
import io.wifi.starrailexpress.scenery.network.SceneAssetNetwork;
import io.wifi.starrailexpress.util.PoisonComponentUtils;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.agmas.noellesroles.game.modes.fourthroom.network.*;

/**
 * 网络数据包类型注册，从 {@link SRE#onInitialize()} 中按类别剥离归一化而来。
 */
public class SREPayloadRegister {

    public static void registerPayloadTypes() {
        SceneAssetNetwork.registerPayloadTypes();
        // 商店价格同步 / Shop price sync
        PayloadTypeRegistry.playS2C().register(
                io.wifi.starrailexpress.shop.network.ShopPriceHandshakeS2CPayload.TYPE,
                io.wifi.starrailexpress.shop.network.ShopPriceHandshakeS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                io.wifi.starrailexpress.shop.network.ShopPriceDataS2CPayload.TYPE,
                io.wifi.starrailexpress.shop.network.ShopPriceDataS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                io.wifi.starrailexpress.shop.network.ShopPriceRequestC2SPayload.TYPE,
                io.wifi.starrailexpress.shop.network.ShopPriceRequestC2SPayload.CODEC);
        // Mod Whitelist Payload
        PayloadTypeRegistry.playS2C().register(VoteSyncS2CPacket.TYPE, VoteSyncS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(VoteCastC2SPacket.TYPE, VoteCastC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(
                net.exmo.sre.mod_whitelist.common.network.ModWhitelistPayload.ID,
                net.exmo.sre.mod_whitelist.common.network.ModWhitelistPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                net.exmo.sre.mod_whitelist.common.network.ResourcePackWhitelistPayload.ID,
                net.exmo.sre.mod_whitelist.common.network.ResourcePackWhitelistPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                net.exmo.sre.mod_whitelist.common.network.ShaderPackWhitelistPayload.ID,
                net.exmo.sre.mod_whitelist.common.network.ShaderPackWhitelistPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.mod_whitelist.common.network.ModWhitelistConfigPayload.ID,
                net.exmo.sre.mod_whitelist.common.network.ModWhitelistConfigPayload.CODEC);

        // JAR 密钥认证（挑战-应答）
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.mod_whitelist.common.network.JarAuthChallengePayload.ID,
                net.exmo.sre.mod_whitelist.common.network.JarAuthChallengePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                net.exmo.sre.mod_whitelist.common.network.JarAuthResponsePayload.ID,
                net.exmo.sre.mod_whitelist.common.network.JarAuthResponsePayload.CODEC);

        PayloadTypeRegistry.playS2C().register(ModVersionPacket.ID, ModVersionPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ModVersionPacket.ID, ModVersionPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(CustomNarratorPacket.ID, CustomNarratorPacket.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncRoomToPlayerPayload.ID, SyncRoomToPlayerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncRoomToPlayerPayload.ID, SyncRoomToPlayerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StreamingSpectatorPayload.ID, StreamingSpectatorPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(IsLobbyConfigPayload.ID, IsLobbyConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(IsLobbyConfigPayload.ID, IsLobbyConfigPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(JoinSpecGroupPayload.ID, JoinSpecGroupPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(JoinSpecGroupPayload.ID, JoinSpecGroupPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(OnGameStartedPayload.TYPE, OnGameStartedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OnGameFinishedPayload.TYPE, OnGameFinishedPayload.CODEC);

        // 高级相机轨道
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.camera.AdvancedCameraPayload.ID,
                net.exmo.sre.camera.AdvancedCameraPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncMapConfigPayload.ID, SyncMapConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TriggerScreenEdgeEffectPayload.ID, TriggerScreenEdgeEffectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateSkinSelectedPayload.ID, UpdateSkinSelectedPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateNameTagSelectedPayload.ID, UpdateNameTagSelectedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RemoveStatusBarPayload.ID, RemoveStatusBarPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TriggerStatusBarPayload.ID, TriggerStatusBarPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BreakArmorPayload.ID, BreakArmorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShootMuzzleS2CPayload.ID, ShootMuzzleS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SniperScopeStateS2CPayload.TYPE,
                SniperScopeStateS2CPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PoisonComponentUtils.PoisonOverlayPayload.ID,
                PoisonComponentUtils.PoisonOverlayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GunDropPayload.ID, GunDropPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TaskCompletePayload.ID, TaskCompletePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AnnounceWelcomePayload.ID, AnnounceWelcomePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AnnounceEndingPayload.ID, AnnounceEndingPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReplayPayload.ID, ReplayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SecurityCameraModePayload.ID, SecurityCameraModePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShowStatsPayload.ID, ShowStatsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerStatsSyncPayload.ID, PlayerStatsSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerDataPartSyncPayload.ID, PlayerDataPartSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.RoleRosterSyncPayload.ID,
                io.wifi.starrailexpress.network.RoleRosterSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.sponsor.SponsorListPayload.ID,
                io.wifi.starrailexpress.sponsor.SponsorListPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.OpenRoleRosterScreenPayload.ID,
                io.wifi.starrailexpress.network.OpenRoleRosterScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.network.MapIntroRequestPayload.ID,
                io.wifi.starrailexpress.network.MapIntroRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.MapIntroSyncPayload.ID,
                io.wifi.starrailexpress.network.MapIntroSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.network.MapRotationTogglePayload.ID,
                io.wifi.starrailexpress.network.MapRotationTogglePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.MapRotationSyncPayload.ID,
                io.wifi.starrailexpress.network.MapRotationSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.network.RoleRosterUpdatePayload.ID,
                io.wifi.starrailexpress.network.RoleRosterUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShowSelectedMapUIPayload.ID, ShowSelectedMapUIPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapVotingResultsPayload.TYPE, MapVotingResultsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CloseUiPayload.ID, CloseUiPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerDeathPayload.ID, PlayerDeathPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FourthRoomStatePayload.ID, FourthRoomStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FourthRoomTableEffectsPayload.ID, FourthRoomTableEffectsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenFourthRoomPeekDeckPayload.ID, OpenFourthRoomPeekDeckPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenSkinScreenPaylod.ID, OpenSkinScreenPaylod.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenProgressionScreenPayload.ID, OpenProgressionScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.OpenBackpackScreenPayload.ID,
                io.wifi.starrailexpress.network.OpenBackpackScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenClueArchivePayload.ID, OpenClueArchivePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.OpenRoleUnlockScreenPayload.ID,
                io.wifi.starrailexpress.network.OpenRoleUnlockScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.RoleUnlockedHudPayload.ID,
                io.wifi.starrailexpress.network.RoleUnlockedHudPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.packet.SyncWaypointsPacket.ID,
                io.wifi.starrailexpress.network.packet.SyncWaypointsPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(
                io.wifi.starrailexpress.network.packet.SyncWaypointVisibilityPacket.ID,
                io.wifi.starrailexpress.network.packet.SyncWaypointVisibilityPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(
                io.wifi.starrailexpress.network.packet.SyncSpecificWaypointVisibilityPacket.ID,
                io.wifi.starrailexpress.network.packet.SyncSpecificWaypointVisibilityPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(
                io.wifi.starrailexpress.network.packet.WaypointDeleteC2SPayload.ID,
                io.wifi.starrailexpress.network.packet.WaypointDeleteC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(KnifeStabPayload.ID, KnifeStabPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GunShootPayload.ID, GunShootPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SniperShootPayload.TYPE, SniperShootPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(StoreBuyPayload.ID, StoreBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NoteEditPayload.ID, NoteEditPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestOpenClueArchivePayload.ID, RequestOpenClueArchivePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.network.VoteForMapPayload.ID,
                io.wifi.starrailexpress.network.VoteForMapPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SecurityCameraExitRequestPayload.ID,
                SecurityCameraExitRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NunchuckHitPayload.ID, NunchuckHitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CardPlayPayload.ID, CardPlayPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BuyFourthRoomItemPayload.ID, BuyFourthRoomItemPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RevealIdentityPayload.ID, RevealIdentityPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CompleteFourthRoomTaskPayload.ID, CompleteFourthRoomTaskPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EndTurnPayload.ID, EndTurnPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UseAssassinationItemPayload.ID, UseAssassinationItemPayload.CODEC);

        // Chat Dialogue
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.client.chat.OpenChatDialoguePayload.ID,
                net.exmo.sre.client.chat.OpenChatDialoguePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.client.chat.ChatDialogueAdvancePayload.ID,
                net.exmo.sre.client.chat.ChatDialogueAdvancePayload.CODEC);

        // Subtitle 字幕报幕
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.subtitle.SubtitleS2CPayload.ID,
                net.exmo.sre.subtitle.SubtitleS2CPayload.CODEC);

        // 紧急会议系统
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.meeting.network.MeetingStateS2CPayload.ID,
                net.exmo.sre.meeting.network.MeetingStateS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.meeting.network.MeetingSpeakC2SPayload.ID,
                net.exmo.sre.meeting.network.MeetingSpeakC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.meeting.network.MeetingVoteResultS2CPayload.ID,
                net.exmo.sre.meeting.network.MeetingVoteResultS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.meeting.network.MeetingReportC2SPayload.ID,
                net.exmo.sre.meeting.network.MeetingReportC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.meeting.network.MeetingCooldownS2CPayload.ID,
                net.exmo.sre.meeting.network.MeetingCooldownS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.meeting.network.MeetingSkipC2SPayload.ID,
                net.exmo.sre.meeting.network.MeetingSkipC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.meeting.network.MeetingSkipStateS2CPayload.ID,
                net.exmo.sre.meeting.network.MeetingSkipStateS2CPayload.CODEC);

        // Mailbox
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.content.mail.OpenMailboxScreenPayload.ID,
                io.wifi.starrailexpress.content.mail.OpenMailboxScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.content.mail.MailClaimC2SPayload.ID,
                io.wifi.starrailexpress.content.mail.MailClaimC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.content.mail.MailDeleteC2SPayload.ID,
                io.wifi.starrailexpress.content.mail.MailDeleteC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.content.mail.MailClaimAllC2SPayload.ID,
                io.wifi.starrailexpress.content.mail.MailClaimAllC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.content.mail.MailDeleteAllReadC2SPayload.ID,
                io.wifi.starrailexpress.content.mail.MailDeleteAllReadC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.content.mail.MailMarkReadC2SPayload.ID,
                io.wifi.starrailexpress.content.mail.MailMarkReadC2SPayload.CODEC);

        // 实体交互方块数据包
        PayloadTypeRegistry.playS2C().register(EntityInteractionBlockPayload.OpenUI.TYPE,
                EntityInteractionBlockPayload.OpenUI.CODEC);
        PayloadTypeRegistry.playS2C().register(EntityInteractionBlockPayload.SyncBlockEntity.TYPE,
                EntityInteractionBlockPayload.SyncBlockEntity.CODEC);
        PayloadTypeRegistry.playC2S().register(EntityInteractionBlockPayload.SaveConfig.TYPE,
                EntityInteractionBlockPayload.SaveConfig.CODEC);

        // 小游戏任务点数据包
        PayloadTypeRegistry.playS2C().register(MinigameQuestPayload.OpenConfig.TYPE,
                MinigameQuestPayload.OpenConfig.CODEC);
        PayloadTypeRegistry.playS2C().register(MinigameQuestPayload.OpenGame.TYPE, MinigameQuestPayload.OpenGame.CODEC);
        PayloadTypeRegistry.playC2S().register(MinigameQuestPayload.SaveConfig.TYPE,
                MinigameQuestPayload.SaveConfig.CODEC);
        PayloadTypeRegistry.playC2S().register(MinigameQuestPayload.CompleteGame.TYPE,
                MinigameQuestPayload.CompleteGame.CODEC);
        PayloadTypeRegistry.playS2C().register(TicketPayload.OpenOfficeConfig.TYPE,
                TicketPayload.OpenOfficeConfig.CODEC);
        PayloadTypeRegistry.playS2C().register(TicketPayload.OpenOfficeShop.TYPE,
                TicketPayload.OpenOfficeShop.CODEC);
        PayloadTypeRegistry.playC2S().register(TicketPayload.SaveOfficeConfig.TYPE,
                TicketPayload.SaveOfficeConfig.CODEC);
        PayloadTypeRegistry.playC2S().register(TicketPayload.BuyTicket.TYPE, TicketPayload.BuyTicket.CODEC);
        PayloadTypeRegistry.playS2C().register(EffectGeneratorPayload.OpenConfig.TYPE,
                EffectGeneratorPayload.OpenConfig.CODEC);
        PayloadTypeRegistry.playC2S().register(EffectGeneratorPayload.SaveConfig.TYPE,
                EffectGeneratorPayload.SaveConfig.CODEC);

        // 职业轮选数据包
        PayloadTypeRegistry.playC2S().register(RoleRotationSelectC2SPacket.TYPE, RoleRotationSelectC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(RoleRotationSyncS2CPacket.TYPE, RoleRotationSyncS2CPacket.CODEC);

        // 全局战绩 / 回放查询数据包
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.record.network.RecordListRequestC2SPayload.ID,
                net.exmo.sre.record.network.RecordListRequestC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.record.network.RecordListS2CPayload.ID,
                net.exmo.sre.record.network.RecordListS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.record.network.RecordReplayRequestC2SPayload.ID,
                net.exmo.sre.record.network.RecordReplayRequestC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.record.network.RecordReplayS2CPayload.ID,
                net.exmo.sre.record.network.RecordReplayS2CPayload.CODEC);

        // 60s 队友标点：C2S 发标点 → 服务端转发给同队 → S2C
        PayloadTypeRegistry.playC2S().register(
                net.exmo.sre.sixtyseconds.network.TeamPingC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.TeamPingC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.sixtyseconds.network.TeamPingS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.TeamPingS2CPacket.CODEC);

        // 60s 直升机撤离状态同步（S2C）
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.sixtyseconds.network.SixtySecondsHelicopterS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsHelicopterS2CPacket.CODEC);

        // 60s 结算数据同步（S2C）
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.sixtyseconds.network.SixtySecondsEndGamePayload.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsEndGamePayload.CODEC);
    }
}
