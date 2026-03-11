package org.agmas.noellesroles.init;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.packet.*;
import org.agmas.noellesroles.packet.Loot.LootPoolsInfoCheckS2CPacket;
import org.agmas.noellesroles.packet.Loot.LootPoolsInfoS2CPacket;
import org.agmas.noellesroles.packet.Loot.LootResultS2CPacket;
import org.agmas.noellesroles.repack.BanditRevolverShootPayload;

public class ModPackets {
  // ==================== 网络包ID定义 ====================
  public static final CustomPacketPayload.Type<MorphC2SPacket> MORPH_PACKET = MorphC2SPacket.ID;
  public static final CustomPacketPayload.Type<SwapperC2SPacket> SWAP_PACKET = SwapperC2SPacket.ID;
  public static final CustomPacketPayload.Type<AbilityC2SPacket> ABILITY_PACKET = AbilityC2SPacket.ID;
  public static final CustomPacketPayload.Type<OpenIntroPayload> OPEN_INTRO_PACKET = OpenIntroPayload.ID;
  public static final CustomPacketPayload.Type<VultureEatC2SPacket> VULTURE_PACKET = VultureEatC2SPacket.ID;
  public static final CustomPacketPayload.Type<ThiefStealC2SPacket> THIEF_PACKET = ThiefStealC2SPacket.ID;
  public static final CustomPacketPayload.Type<ManipulatorC2SPacket> MANIPULATOR_PACKET = ManipulatorC2SPacket.ID;

  public static final CustomPacketPayload.Type<ExecutionerSelectTargetC2SPacket> EXECUTIONER_SELECT_TARGET_PACKET = ExecutionerSelectTargetC2SPacket.ID;
  public static final CustomPacketPayload.Type<InsaneKillerAbilityC2SPacket> INSANE_KILLER_ABILITY_PACKET = InsaneKillerAbilityC2SPacket.ID;
  public static final CustomPacketPayload.Type<RecorderC2SPacket> RECORDER_PACKET = RecorderC2SPacket.TYPE;
  public static final CustomPacketPayload.Type<MonitorMarkC2SPacket> MONITOR_MARK_PACKET = MonitorMarkC2SPacket.ID;

  public static void registerPackets() {
    PayloadTypeRegistry.playS2C().register(ProblemScreenOpenC2SPacket.ID,
        ProblemScreenOpenC2SPacket.CODEC);
    PayloadTypeRegistry.playC2S().register(ExecutionerSelectTargetC2SPacket.ID,
        ExecutionerSelectTargetC2SPacket.CODEC);
    PayloadTypeRegistry.playC2S().register(ProblemSetEventC2SPacket.ID,
        ProblemSetEventC2SPacket.CODEC);
    PayloadTypeRegistry.playC2S().register(BroadcasterC2SPacket.ID, BroadcasterC2SPacket.CODEC);

    PayloadTypeRegistry.playC2S().register(AbilityWithTargetC2SPacket.ID, AbilityWithTargetC2SPacket.CODEC);

    PayloadTypeRegistry.playC2S().register(VendingMachinesBuyC2SPacket.TYPE,
        VendingMachinesBuyC2SPacket.CODEC);

    PayloadTypeRegistry.playS2C().register(VendingBuyMessageCallBackS2CPacket.ID,
        VendingBuyMessageCallBackS2CPacket.CODEC);
    PayloadTypeRegistry.playC2S().register(WheelchairMoveC2SPacket.ID, WheelchairMoveC2SPacket.CODEC);
    PayloadTypeRegistry.playS2C().register(BroadcastMessageS2CPacket.ID, BroadcastMessageS2CPacket.CODEC);
    PayloadTypeRegistry.playS2C().register(CanMoveInTimeStopS2CPacket.ID, CanMoveInTimeStopS2CPacket.CODEC);
    PayloadTypeRegistry.playC2S().register(ScanAllTaskPointsPayload.ID, ScanAllTaskPointsPayload.CODEC);
    PayloadTypeRegistry.playS2C().register(ScanAllTaskPointsPayload.ID, ScanAllTaskPointsPayload.CODEC);

    PayloadTypeRegistry.playC2S().register(PlayerResetS2CPacket.ID, PlayerResetS2CPacket.CODEC);
    PayloadTypeRegistry.playC2S().register(ChefCookC2SPacket.ID, ChefCookC2SPacket.CODEC);
    PayloadTypeRegistry.playS2C().register(PlayerResetS2CPacket.ID, PlayerResetS2CPacket.CODEC);

    PayloadTypeRegistry.playC2S().register(GamblerSelectRoleC2SPacket.ID, GamblerSelectRoleC2SPacket.CODEC);
    PayloadTypeRegistry.playS2C().register(GamblerSelectRoleC2SPacket.ID, GamblerSelectRoleC2SPacket.CODEC);

    PayloadTypeRegistry.playC2S().register(MorphC2SPacket.ID, MorphC2SPacket.CODEC);
    PayloadTypeRegistry.playS2C().register(OpenIntroPayload.ID, OpenIntroPayload.CODEC);

    PayloadTypeRegistry.playC2S().register(OpenIntroPayload.ID, OpenIntroPayload.CODEC);
    PayloadTypeRegistry.playC2S().register(AbilityC2SPacket.ID, AbilityC2SPacket.CODEC);
    PayloadTypeRegistry.playS2C().register(ToggleInsaneSkillC2SPacket.ID, ToggleInsaneSkillC2SPacket.CODEC);
    PayloadTypeRegistry.playC2S().register(SwapperC2SPacket.ID, SwapperC2SPacket.CODEC);
    PayloadTypeRegistry.playC2S().register(VultureEatC2SPacket.ID, VultureEatC2SPacket.CODEC);
    PayloadTypeRegistry.playC2S().register(TryThrowKnifePacket.ID, TryThrowKnifePacket.CODEC);
    PayloadTypeRegistry.playC2S().register(ManipulatorC2SPacket.ID, ManipulatorC2SPacket.CODEC);
    PayloadTypeRegistry.playC2S().register(OpenLockGuiS2CPacket.ID, OpenLockGuiS2CPacket.CODEC);
    PayloadTypeRegistry.playS2C().register(OpenLockGuiS2CPacket.ID, OpenLockGuiS2CPacket.CODEC);
    PayloadTypeRegistry.playS2C().register(OpenVendingMachinesScreenS2CPacket.ID,
        OpenVendingMachinesScreenS2CPacket.CODEC);

    PayloadTypeRegistry.playS2C().register(BloodConfigS2CPacket.ID, BloodConfigS2CPacket.CODEC);
    PayloadTypeRegistry.playC2S().register(BloodConfigS2CPacket.ID, BloodConfigS2CPacket.CODEC);
    PayloadTypeRegistry.playS2C().register(CreateClientSmokeAreaPacket.ID, CreateClientSmokeAreaPacket.CODEC);

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
    PayloadTypeRegistry.playC2S().register(MonitorMarkC2SPacket.ID, MonitorMarkC2SPacket.CODEC);

    // 注册抽奖网络包
    PayloadTypeRegistry.playS2C().register(LootResultS2CPacket.ID, LootResultS2CPacket.CODEC);
    PayloadTypeRegistry.playS2C().register(LootPoolsInfoCheckS2CPacket.ID,
        LootPoolsInfoCheckS2CPacket.CODEC);
    PayloadTypeRegistry.playS2C().register(LootPoolsInfoS2CPacket.ID, LootPoolsInfoS2CPacket.CODEC);

    // 注册物品展示 ui网络包
    PayloadTypeRegistry.playS2C().register(DisplayItemS2CPacket.ID, DisplayItemS2CPacket.CODEC);
  }
}
