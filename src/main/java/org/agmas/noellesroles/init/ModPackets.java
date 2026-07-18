package org.agmas.noellesroles.init;

import net.exmo.sre.repair.network.*;
import io.wifi.starrailexpress.network.packet.EditNewspaperPacket;
import io.wifi.starrailexpress.network.packet.EnableTaskHighlightPacket;
import io.wifi.starrailexpress.network.packet.ShowCustomNewspaperPacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.content.item.ZeroOneFiveSecondShotPayload;
import org.agmas.noellesroles.content.item.ZeroOneFiveShootPayload;
import org.agmas.noellesroles.packet.*;
import org.agmas.noellesroles.packet.Loot.*;

public class ModPackets {
    // ==================== 网络包ID定义 ====================
    public static final CustomPacketPayload.Type<MorphC2SPacket> MORPH_PACKET = MorphC2SPacket.ID;
    public static final CustomPacketPayload.Type<SwapperC2SPacket> SWAP_PACKET = SwapperC2SPacket.ID;
    public static final CustomPacketPayload.Type<AbilityC2SPacket> ABILITY_PACKET = AbilityC2SPacket.ID;
    public static final CustomPacketPayload.Type<OpenIntroPayload> OPEN_INTRO_PACKET = OpenIntroPayload.ID;
    public static final CustomPacketPayload.Type<VultureEatC2SPacket> VULTURE_PACKET = VultureEatC2SPacket.ID;
    public static final CustomPacketPayload.Type<ThiefStealC2SPacket> THIEF_PACKET = ThiefStealC2SPacket.ID;
    public static final CustomPacketPayload.Type<ManipulatorC2SPacket> MANIPULATOR_PACKET = ManipulatorC2SPacket.ID;
    public static final CustomPacketPayload.Type<AmonSelectTargetC2SPacket> AMON_SELECT_TARGET_PACKET = AmonSelectTargetC2SPacket.ID;

    public static final CustomPacketPayload.Type<ExecutionerSelectTargetC2SPacket> EXECUTIONER_SELECT_TARGET_PACKET = ExecutionerSelectTargetC2SPacket.ID;
    public static final CustomPacketPayload.Type<InsaneKillerAbilityC2SPacket> INSANE_KILLER_ABILITY_PACKET = InsaneKillerAbilityC2SPacket.ID;
    public static final CustomPacketPayload.Type<RecorderC2SPacket> RECORDER_PACKET = RecorderC2SPacket.TYPE;
        public static final CustomPacketPayload.Type<MercenaryContractSignC2SPacket> MERCENARY_CONTRACT_SIGN_PACKET = MercenaryContractSignC2SPacket.TYPE;
    public static final CustomPacketPayload.Type<MonitorMarkC2SPacket> MONITOR_MARK_PACKET = MonitorMarkC2SPacket.ID;
    public static final CustomPacketPayload.Type<WaterGhostUseSkillC2SPacket> WATER_GHOST_SKILL_PACKET = WaterGhostUseSkillC2SPacket.TYPE;

    public static final CustomPacketPayload.Type<MorticianToggleModeC2SPacket> MORTICIAN_TOGGLE_MODE_PACKET = MorticianToggleModeC2SPacket.TYPE;

    public static final CustomPacketPayload.Type<MorticianCreateBodyC2SPacket> MORTICIAN_CREATE_BODY_PACKET = MorticianCreateBodyC2SPacket.TYPE;

    public static final CustomPacketPayload.Type<ImitatorSwitchSlotC2SPacket> IMITATOR_SWITCH_SLOT_PACKET = ImitatorSwitchSlotC2SPacket.TYPE;

    public static void registerPackets() {
        PayloadTypeRegistry.playS2C().register(ProblemScreenOpenC2SPacket.ID,
                ProblemScreenOpenC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ExecutionerSelectTargetC2SPacket.ID,
                ExecutionerSelectTargetC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ProblemSetEventC2SPacket.ID,
                ProblemSetEventC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(BroadcasterC2SPacket.ID, BroadcasterC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(NinjaAbilityC2SPacket.ID, NinjaAbilityC2SPacket.CODEC);

        PayloadTypeRegistry.playC2S().register(AbilityWithTargetC2SPacket.ID, AbilityWithTargetC2SPacket.CODEC);

        PayloadTypeRegistry.playC2S().register(VendingMachinesBuyC2SPacket.TYPE,
                VendingMachinesBuyC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(LotteryMachineDrawC2SPacket.TYPE,
                LotteryMachineDrawC2SPacket.CODEC);

        PayloadTypeRegistry.playS2C().register(VendingBuyMessageCallBackS2CPacket.ID,
                VendingBuyMessageCallBackS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenLotteryMachineScreenS2CPacket.ID,
                OpenLotteryMachineScreenS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(LotteryMachineResultS2CPacket.ID,
                LotteryMachineResultS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(WheelchairMoveC2SPacket.ID, WheelchairMoveC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(BroadcastMessageS2CPacket.ID, BroadcastMessageS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(CanMoveInTimeStopS2CPacket.ID, CanMoveInTimeStopS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ScanAllTaskPointsPayload.ID, ScanAllTaskPointsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScanAllTaskPointsPayload.ID, ScanAllTaskPointsPayload.CODEC);

        
        PayloadTypeRegistry.playS2C().register(ShowCustomNewspaperPacket.ID, ShowCustomNewspaperPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(EditNewspaperPacket.ID, EditNewspaperPacket.STREAM_CODEC);

        PayloadTypeRegistry.playC2S().register(PlayerResetS2CPacket.ID, PlayerResetS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ChefCookC2SPacket.ID, ChefCookC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerResetS2CPacket.ID, PlayerResetS2CPacket.CODEC);

        PayloadTypeRegistry.playC2S().register(GamblerSelectRoleC2SPacket.ID, GamblerSelectRoleC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(GamblerSelectRoleC2SPacket.ID, GamblerSelectRoleC2SPacket.CODEC);

        PayloadTypeRegistry.playC2S().register(MorphC2SPacket.ID, MorphC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SilencerC2SPacket.ID, SilencerC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SilencerHelpC2SPacket.ID, SilencerHelpC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenIntroPayload.ID, OpenIntroPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NameTagSyncPayload.ID, NameTagSyncPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(OpenIntroPayload.ID, OpenIntroPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AbilityC2SPacket.ID, AbilityC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(UnifiedSkillInputC2SPacket.ID, UnifiedSkillInputC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(UnifiedSkillSelectC2SPacket.ID, UnifiedSkillSelectC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(ToggleInsaneSkillC2SPacket.ID, ToggleInsaneSkillC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SwapperC2SPacket.ID, SwapperC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(VultureEatC2SPacket.ID, VultureEatC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(TryThrowItemPacket.ID, TryThrowItemPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(org.agmas.noellesroles.packet.WizardShieldC2SPacket.ID,
                org.agmas.noellesroles.packet.WizardShieldC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(WizardSwitchSpellC2SPacket.ID, WizardSwitchSpellC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ManipulatorC2SPacket.ID, ManipulatorC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AmonSelectTargetC2SPacket.ID, AmonSelectTargetC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ManipulatorControlInputC2SPacket.ID, ManipulatorControlInputC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ManipulatorAbilityC2SPacket.ID, ManipulatorAbilityC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenLockGuiS2CPacket.ID, OpenLockGuiS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenLockGuiS2CPacket.ID, OpenLockGuiS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenKeyForgeGuiS2CPacket.ID, OpenKeyForgeGuiS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenVendingMachinesScreenS2CPacket.ID,
                OpenVendingMachinesScreenS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenSupplyCrateScreenS2CPacket.ID,
                OpenSupplyCrateScreenS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenRepairStationScreenS2CPacket.ID,
                OpenRepairStationScreenS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(RepairCoinRewardS2CPacket.ID,
                RepairCoinRewardS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(RepairCombatFeedbackS2CPacket.ID,
                RepairCombatFeedbackS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(MapStatusBarSyncS2CPacket.ID, MapStatusBarSyncS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RepairStationActionC2SPacket.ID,
                RepairStationActionC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RepairStationActionC2SPacket.ID, RepairStationActionC2SPacket::handle);
        PayloadTypeRegistry.playS2C().register(OpenRepairRoleSelectionS2CPacket.ID,
                OpenRepairRoleSelectionS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenRepairRoleShopS2CPacket.ID,
                OpenRepairRoleShopS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RepairRoleSelectC2SPacket.ID, RepairRoleSelectC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RepairRoleShopPurchaseC2SPacket.ID, RepairRoleShopPurchaseC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RepairPrimarySkillC2SPacket.ID, RepairPrimarySkillC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RepairCarryStruggleC2SPacket.ID, RepairCarryStruggleC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RepairSearchBeginC2SPacket.ID, RepairSearchBeginC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RepairSearchCancelC2SPacket.ID, RepairSearchCancelC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RepairRoleSelectC2SPacket.ID, RepairRoleSelectC2SPacket::handle);
        ServerPlayNetworking.registerGlobalReceiver(RepairRoleShopPurchaseC2SPacket.ID, RepairRoleShopPurchaseC2SPacket::handle);
        ServerPlayNetworking.registerGlobalReceiver(RepairPrimarySkillC2SPacket.ID, RepairPrimarySkillC2SPacket::handle);
        ServerPlayNetworking.registerGlobalReceiver(RepairCarryStruggleC2SPacket.ID, RepairCarryStruggleC2SPacket::handle);
        ServerPlayNetworking.registerGlobalReceiver(RepairSearchBeginC2SPacket.ID, RepairSearchBeginC2SPacket::handle);
        ServerPlayNetworking.registerGlobalReceiver(RepairSearchCancelC2SPacket.ID, RepairSearchCancelC2SPacket::handle);

        // 末日60秒模式：门开屏 + loot 表编辑/保存
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenSixtySecondsDoorS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenSixtySecondsDoorS2CPacket.CODEC);
        // 统一避难所门菜单：S2C 开屏（携带上下文选项）+ C2S 执行选中操作
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenShelterDoorS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenShelterDoorS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.ShelterDoorActionC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.ShelterDoorActionC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.ShelterDoorActionC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.ShelterDoorActionC2SPacket::handle);
        // 物资箱搜刮进度（搜打撤式动画）
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.SupplySearchS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SupplySearchS2CPacket.CODEC);
        // 睡觉时间强制入眠黑屏演出
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.SleepBlackoutS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SleepBlackoutS2CPacket.CODEC);
        // 60s 区域地图：当前扫描区域 + 家点位
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.CODEC);
        // 60s 自动复活：尸体标记增删（地图标注表是纯客户端的，服务端只能推包）
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.sixtyseconds.network.SixtySecondsCorpseMarkS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsCorpseMarkS2CPacket.CODEC);
        // 60s 玩家血量广播：血量变化时推给同维度其他玩家（供战斗HUD显示他人血量）
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.sixtyseconds.network.PlayerHealthS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.PlayerHealthS2CPacket.CODEC);
        // 60s 海图：海岛元数据 + 解锁迷雾
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartS2CPacket.CODEC);
        // 60s 星图：星级区域同步（S2C 推送 + C2S 请求刷新）
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.SixtySecondsStarMapS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsStarMapS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(
                net.exmo.sre.sixtyseconds.network.SixtySecondsStarMapRequestC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsStarMapRequestC2SPacket.CODEC);
        net.exmo.sre.sixtyseconds.network.SixtySecondsStarMapRequestC2SPacket.registerServerReceiver();
        // 60s 海图返回：C2S 请求 + S2C 启动动画 + S2C 登岛落点同步
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartReturnC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartReturnC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartReturnC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartReturnC2SPacket::handle);
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartReturnStartS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartReturnStartS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartArrivalS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartArrivalS2CPacket.CODEC);
        // 60s 海图返回取消
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartReturnCancelS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartReturnCancelS2CPacket.CODEC);
        // 60s 海图扬帆去程动画
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartSailStartS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartSailStartS2CPacket.CODEC);
        // 60s 海图动态点位：C2S 开屏/关屏订阅 + S2C 庇护所与队友坐标（仅对开着海图的玩家每秒推）
        PayloadTypeRegistry.playC2S().register(
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartWatchC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartWatchC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartWatchC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartWatchC2SPacket::handle);
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartPositionsS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartPositionsS2CPacket.CODEC);
        // 电力面板 + 避难所控制面板
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenPowerPanelS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenPowerPanelS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenShelterPanelS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenShelterPanelS2CPacket.CODEC);
        // 房车控制台：S2C 开屏 + C2S 安装/卸下/升级
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenRvConsoleS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenRvConsoleS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.RvConsoleActionC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.RvConsoleActionC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.RvConsoleActionC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.RvConsoleActionC2SPacket::handle);
        // 60s 开场演出广播：C2S=OP 请求全员播放（服务端校验），S2C=客户端开屏
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.SixtySecondsIntroPayload.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsIntroPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.SixtySecondsIntroPayload.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsIntroPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.SixtySecondsIntroPayload.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsIntroPayload::handle);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenLootTableEditS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenLootTableEditS2CPacket.CODEC);
        // 随机物资箱配置 GUI（S2C 开屏 + C2S 保存）
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.sixtyseconds.network.OpenRandomSupplyBoxConfigS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenRandomSupplyBoxConfigS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(
                net.exmo.sre.sixtyseconds.network.RandomSupplyBoxConfigSaveC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.RandomSupplyBoxConfigSaveC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                net.exmo.sre.sixtyseconds.network.RandomSupplyBoxConfigSaveC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.RandomSupplyBoxConfigSaveC2SPacket::handle);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenAirdropEditS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenAirdropEditS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.LootTableSaveC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.LootTableSaveC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.LootTableSaveC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.LootTableSaveC2SPacket::handle);
        // 60s NPC：对话菜单 / 商人购买 / 创造模式货架编辑（S2C 开屏 → C2S 动作 → 服务端重校验 → 重推 S2C）
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenNpcDialogueS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenNpcDialogueS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.NpcDialogueActionC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.NpcDialogueActionC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.NpcDialogueActionC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.NpcDialogueActionC2SPacket::handle);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenNpcShopS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenNpcShopS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.NpcShopBuyC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.NpcShopBuyC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.NpcShopBuyC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.NpcShopBuyC2SPacket::handle);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenNpcShopEditS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenNpcShopEditS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.NpcShopSaveC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.NpcShopSaveC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.NpcShopSaveC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.NpcShopSaveC2SPacket::handle);
        // 60s 枪械开火请求（客户端准星射线 → 服务端结算）
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.SixtySecondsGunShootC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsGunShootC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.SixtySecondsGunShootC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsGunShootC2SPacket::handle);

        // 末日60秒模式：拜访请求流程
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenVisitRequestS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenVisitRequestS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenVisitPromptS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenVisitPromptS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.VisitRequestC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.VisitRequestC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.VisitResponseC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.VisitResponseC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.VisitRequestC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.VisitRequestC2SPacket::handle);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.VisitResponseC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.VisitResponseC2SPacket::handle);
        // 撬棍/开锁器闯入：选队界面 + 执行回传
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenBreakInSelectS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenBreakInSelectS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.BreakInExecuteC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.BreakInExecuteC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.BreakInExecuteC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.BreakInExecuteC2SPacket::handle);
        // 保险库撬锁小游戏
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenVaultLockpickS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenVaultLockpickS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.VaultLockpickCompleteC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.VaultLockpickCompleteC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.VaultLockpickCompleteC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.VaultLockpickCompleteC2SPacket::handle);
        // 拜访双向对话
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenVisitChatS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenVisitChatS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.VisitChatMessageS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.VisitChatMessageS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.VisitChatSendC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.VisitChatSendC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.VisitChatSendC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.VisitChatSendC2SPacket::handle);
        // 拜访交易（主手对主手交换）
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenTradeS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenTradeS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.TradeActionC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.TradeActionC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.TradeActionC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.TradeActionC2SPacket::handle);
        // 实体游戏币兑换（个人余额 → 实体币）
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.TokenExchangeC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.TokenExchangeC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.TokenExchangeC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.TokenExchangeC2SPacket::handle);
        // 创造模式：区域地图点击标记点传送
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.MapTeleportC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.MapTeleportC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.MapTeleportC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.MapTeleportC2SPacket::handle);
        // 赛前组队大厅（/sre:60s team 打开 + 创建/加入/离开操作）
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenTeamLobbyS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenTeamLobbyS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.TeamLobbyActionC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.TeamLobbyActionC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.TeamLobbyActionC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.TeamLobbyActionC2SPacket::handle);
        // 对讲机频道：打开频道界面(S2C) + 接入/退出频道(C2S)
        PayloadTypeRegistry.playS2C().register(org.agmas.noellesroles.packet.OpenRadioChannelS2CPacket.ID,
                org.agmas.noellesroles.packet.OpenRadioChannelS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(org.agmas.noellesroles.packet.RadioChannelC2SPacket.ID,
                org.agmas.noellesroles.packet.RadioChannelC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.RadioChannelC2SPacket.ID,
                org.agmas.noellesroles.packet.RadioChannelC2SPacket::handle);
        // 科技树 + 合成站 + 射击轨迹
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenTechTreeS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenTechTreeS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.TechUnlockC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.TechUnlockC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.TechUnlockC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.TechUnlockC2SPacket::handle);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenStationS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenStationS2CPacket.CODEC);
        // 合成台「家里容器」库存快照（客户端读不到容器内容，GUI 判定靠它）
        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.sixtyseconds.network.SixtySecondsStationStockS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.SixtySecondsStationStockS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.StationCraftC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.StationCraftC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.StationCraftC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.StationCraftC2SPacket::handle);
        // 拆解台：打开界面(S2C) + 拆解请求(C2S)
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.OpenDismantleS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.OpenDismantleS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.DismantleC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.DismantleC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.exmo.sre.sixtyseconds.network.DismantleC2SPacket.ID,
                net.exmo.sre.sixtyseconds.network.DismantleC2SPacket::handle);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.GunTracerS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.GunTracerS2CPacket.CODEC);

        PayloadTypeRegistry.playS2C().register(ReasonerOpenScreenS2CPacket.ID, ReasonerOpenScreenS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ReasonerSubmitC2SPacket.ID, ReasonerSubmitC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReasonerSubmitC2SPacket.ID, ReasonerSubmitC2SPacket::handle);

        PayloadTypeRegistry.playS2C().register(DoomedSinnerFateRevealS2CPacket.ID, DoomedSinnerFateRevealS2CPacket.CODEC);

        PayloadTypeRegistry.playS2C().register(BloodConfigS2CPacket.ID, BloodConfigS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(BloodConfigS2CPacket.ID, BloodConfigS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(CreateClientSmokeAreaPacket.ID, CreateClientSmokeAreaPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.CreateClientMarkingAreaPacket.ID,
                net.exmo.sre.sixtyseconds.network.CreateClientMarkingAreaPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(CreateCreeperBombAreaPacket.ID, CreateCreeperBombAreaPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(BanditRevolverShootPayload.ID,
                BanditRevolverShootPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BanditRevolverShootPayload.ID,
                new BanditRevolverShootPayload.Receiver());

        // 注册消防斧攻击网络包
        PayloadTypeRegistry.playC2S().register(FireAxeStabPayload.ID,
                FireAxeStabPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(InsaneKillerAbilityC2SPacket.ID,
                InsaneKillerAbilityC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RecorderC2SPacket.TYPE, RecorderC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(MercenaryContractSignC2SPacket.TYPE,
                MercenaryContractSignC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(MonitorMarkC2SPacket.ID, MonitorMarkC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(WaterGhostUseSkillC2SPacket.TYPE, WaterGhostUseSkillC2SPacket.CODEC);

        // 葬仪模式切换网络包
        PayloadTypeRegistry.playC2S().register(MorticianToggleModeC2SPacket.TYPE, MorticianToggleModeC2SPacket.CODEC);

        // 葬仪造尸网络包
        PayloadTypeRegistry.playC2S().register(MorticianCreateBodyC2SPacket.TYPE, MorticianCreateBodyC2SPacket.CODEC);

        // 模仿者切换槽位网络包
        PayloadTypeRegistry.playC2S().register(ImitatorSwitchSlotC2SPacket.TYPE, ImitatorSwitchSlotC2SPacket.CODEC);

        // 派对狂网络包
        PayloadTypeRegistry.playC2S().register(PartyKillerC2SPacket.TYPE, PartyKillerC2SPacket.CODEC);

        // 注册短管霰弹枪装备音效网络包
        PayloadTypeRegistry.playC2S().register(ShortShotgunEquipPayload.ID, ShortShotgunEquipPayload.CODEC);

        // 注册零一五枪射击网络包
        PayloadTypeRegistry.playC2S().register(ZeroOneFiveShootPayload.ID, ZeroOneFiveShootPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ZeroOneFiveSecondShotPayload.ID, ZeroOneFiveSecondShotPayload.CODEC);

        // 注册鹈鹕网络包
        PayloadTypeRegistry.playC2S().register(PelicanEatC2SPacket.ID, PelicanEatC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(PelicanReleaseC2SPacket.ID, PelicanReleaseC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(PelicanStateS2CPacket.ID, PelicanStateS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(PelicanProgressS2CPacket.ID, PelicanProgressS2CPacket.CODEC);

        // 注册抽奖网络包
        PayloadTypeRegistry.playS2C().register(LootResultS2CPacket.ID, LootResultS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(LootMultiResultS2CPacket.ID, LootMultiResultS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(LootPoolsInfoCheckS2CPacket.ID, LootPoolsInfoCheckS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(LootPoolsInfoS2CPacket.ID, LootPoolsInfoS2CPacket.CODEC);

        // 自定义职业同步
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.CustomRoleSyncPayload.TYPE,
                io.wifi.starrailexpress.network.CustomRoleSyncPayload.CODEC);

        // 注册抽奖数据刷新网络包
        PayloadTypeRegistry.playS2C().register(LootDataRefreshS2CPacket.ID, LootDataRefreshS2CPacket.CODEC);

        // 注册物品展示 ui网络包
        PayloadTypeRegistry.playS2C().register(DisplayItemS2CPacket.ID, DisplayItemS2CPacket.CODEC);

        // 注册赌徒 1% 奇迹特效包（客户端渲染）
        PayloadTypeRegistry.playS2C().register(GamblerMiracleS2CPacket.ID, GamblerMiracleS2CPacket.CODEC);

        // 注册愚者网络包
        PayloadTypeRegistry.playC2S().register(
                org.agmas.noellesroles.game.roles.innocence.fool.FoolPrayerC2SPacket.ID,
                org.agmas.noellesroles.game.roles.innocence.fool.FoolPrayerC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(
                org.agmas.noellesroles.game.roles.innocence.fool.FoolLeaveMeetingC2SPacket.ID,
                org.agmas.noellesroles.game.roles.innocence.fool.FoolLeaveMeetingC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(
                org.agmas.noellesroles.game.roles.innocence.fool.FoolTarotVoteC2SPacket.ID,
                org.agmas.noellesroles.game.roles.innocence.fool.FoolTarotVoteC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(
                org.agmas.noellesroles.game.roles.innocence.fool.FoolExecutionerGunShootC2SPacket.ID,
                org.agmas.noellesroles.game.roles.innocence.fool.FoolExecutionerGunShootC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(
                org.agmas.noellesroles.game.roles.innocence.fool.FoolOpenTarotVoteS2CPacket.ID,
                org.agmas.noellesroles.game.roles.innocence.fool.FoolOpenTarotVoteS2CPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                org.agmas.noellesroles.game.roles.innocence.fool.FoolExecutionerGunShootC2SPacket.ID,
                new org.agmas.noellesroles.game.roles.innocence.fool.FoolExecutionerGunShootC2SPacket.Receiver());

        // 注册清除血液粒子网络包
        PayloadTypeRegistry.playS2C().register(ClearBloodParticlesS2CPacket.ID, ClearBloodParticlesS2CPacket.CODEC);

        // 注册启用任务透视网络包
        PayloadTypeRegistry.playS2C().register(EnableTaskHighlightPacket.ID, EnableTaskHighlightPacket.CODEC);

        // 注册嬉命人网络包
        PayloadTypeRegistry.playC2S().register(EmbalmerC2SPacket.ID, EmbalmerC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(EmbalmerSkinSwapS2CPacket.ID, EmbalmerSkinSwapS2CPacket.CODEC);

        // 注册窃皮者网络包
        PayloadTypeRegistry.playC2S().register(SkincrawlerC2SPacket.ID, SkincrawlerC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SkincrawlerSkinS2CPacket.ID, SkincrawlerSkinS2CPacket.CODEC);

        // 注册阿蒙夺舍皮肤顶替网络包
        PayloadTypeRegistry.playS2C().register(org.agmas.noellesroles.packet.AmonSkinS2CPacket.ID,
                org.agmas.noellesroles.packet.AmonSkinS2CPacket.CODEC);

        // 注册阿蒙终幕状态网络包
        PayloadTypeRegistry.playS2C().register(org.agmas.noellesroles.packet.AmonFinaleS2CPacket.ID,
                org.agmas.noellesroles.packet.AmonFinaleS2CPacket.CODEC);

        // 注册物资箱网络包
        PayloadTypeRegistry.playC2S().register(SupplyCrateSaveConfigC2SPacket.ID, SupplyCrateSaveConfigC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SupplyCrateSaveConfigC2SPacket.ID, SupplyCrateSaveConfigC2SPacket::handle);

        // 注册移动平台配置网络包
        PayloadTypeRegistry.playC2S().register(MovingPlatformConfigC2SPacket.TYPE, MovingPlatformConfigC2SPacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MovingPlatformConfigC2SPacket.TYPE, MovingPlatformConfigC2SPacket::handle);
        PayloadTypeRegistry.playC2S().register(HurricaneDeviceConfigC2SPacket.TYPE, HurricaneDeviceConfigC2SPacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HurricaneDeviceConfigC2SPacket.TYPE, HurricaneDeviceConfigC2SPacket::handle);
        PayloadTypeRegistry.playC2S().register(TrashCanConfigC2SPacket.TYPE, TrashCanConfigC2SPacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TrashCanConfigC2SPacket.TYPE, TrashCanConfigC2SPacket::handle);

        // 注册反应堆小游戏完成网络包
        PayloadTypeRegistry.playC2S().register(ReactorMinigameCompleteC2SPacket.TYPE, ReactorMinigameCompleteC2SPacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReactorMinigameCompleteC2SPacket.TYPE, ReactorMinigameCompleteC2SPacket::handle);

        // 注册水阀小游戏完成网络包
        PayloadTypeRegistry.playC2S().register(WaterValveMinigameCompleteC2SPacket.TYPE, WaterValveMinigameCompleteC2SPacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(WaterValveMinigameCompleteC2SPacket.TYPE, WaterValveMinigameCompleteC2SPacket::handle);
        PayloadTypeRegistry.playC2S().register(DebrisPileMinigameCompleteC2SPacket.TYPE, DebrisPileMinigameCompleteC2SPacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DebrisPileMinigameCompleteC2SPacket.TYPE, DebrisPileMinigameCompleteC2SPacket::handle);

        // 信使邮件包
        PayloadTypeRegistry.playC2S().register(org.agmas.noellesroles.packet.CourierMailSendC2SPacket.TYPE, org.agmas.noellesroles.packet.CourierMailSendC2SPacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.CourierMailSendC2SPacket.TYPE, org.agmas.noellesroles.packet.CourierMailSendC2SPacket::handle);
        PayloadTypeRegistry.playC2S().register(org.agmas.noellesroles.packet.CourierMailReceiveC2SPacket.TYPE, org.agmas.noellesroles.packet.CourierMailReceiveC2SPacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.CourierMailReceiveC2SPacket.TYPE, org.agmas.noellesroles.packet.CourierMailReceiveC2SPacket::handle);
        PayloadTypeRegistry.playC2S().register(org.agmas.noellesroles.packet.CourierMailReplyC2SPacket.TYPE, org.agmas.noellesroles.packet.CourierMailReplyC2SPacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.CourierMailReplyC2SPacket.TYPE, org.agmas.noellesroles.packet.CourierMailReplyC2SPacket::handle);

        // 60s 载具摄像机切换（汽车上车切第三人称）
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.sixtyseconds.network.VehicleCameraS2CPacket.ID,
                net.exmo.sre.sixtyseconds.network.VehicleCameraS2CPacket.CODEC);
        // 电话拨号网络包
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.sixtyseconds.network.PhoneDialC2SPacket.TYPE,
                net.exmo.sre.sixtyseconds.network.PhoneDialC2SPacket.CODEC);
    }
}
