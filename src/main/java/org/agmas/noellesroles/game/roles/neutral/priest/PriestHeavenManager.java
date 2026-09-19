package org.agmas.noellesroles.game.roles.neutral.priest;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.PriestHeavenStateS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.PriestRoleData;
import org.agmas.noellesroles.utils.OpenScreenManager;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.UUID;

/**
 * 神父「天堂制造」序列：转职、咏诵、昼夜/局内时间加速、伪结束。
 */
public final class PriestHeavenManager {

    public enum Phase {
        IDLE,
        TRANSFORMED,
        CHANTING,
        ACCELERATING,
        FINALE
    }

    public static final int CHANT_TICKS = 25 * 20;
    public static final int ACCEL_TICKS = 30 * 20;
    public static final int LAST_DRAMATIC_TICKS = 15 * 20;
    public static final int FINALE_TICKS = 8 * 20;
    public static final int SPEED_RAMP_TICKS = 4 * 20;
    /** 最大速度倍率（1000%） */
    public static final double MAX_SPEED_MULTIPLIER = 10.0D;
    public static final ResourceLocation SPEED_MODIFIER_ID = Noellesroles.id("priest_heaven_speed");

    private static Session session;

    public static final class Session {
        public UUID priestId;
        public Phase phase = Phase.IDLE;
        public int chantRemain;
        public int accelElapsed;
        public int finaleRemain;
        public boolean pendingOfficialEnd;
        public boolean completedSequence;
        public boolean endingSent;
        public int visualTime;
        public int accelStartTime;
        public boolean frozeGameTime;
    }

    private PriestHeavenManager() {
    }

    public static Session session() {
        return session;
    }

    public static boolean isActive() {
        return session != null && session.phase != Phase.IDLE;
    }

    public static boolean isCinematicFreeze() {
        return session != null && (session.phase == Phase.ACCELERATING || session.phase == Phase.FINALE);
    }

    public static boolean isPriest(ServerPlayer player) {
        return session != null && player != null && player.getUUID().equals(session.priestId);
    }

    public static boolean shouldAutoRun(net.minecraft.world.entity.player.Player player) {
        PriestRoleData data = RoleData.getNullable(PriestRoleData.class, player);
        return data != null && data.autoSprint;
    }

    public static boolean isMovingForSpeedRamp(net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return false;
        }
        if (shouldAutoRun(player) || player.isSprinting()) {
            return true;
        }
        if (Math.abs(player.xxa) > 1.0E-4F || Math.abs(player.zza) > 1.0E-4F) {
            return true;
        }
        return player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-5;
    }

    /** 叠在游戏写死的 getSpeed 上，走动越久越快，最高约 +1000%。 */
    public static float movementSpeedMultiplier(net.minecraft.world.entity.player.Player player) {
        PriestRoleData data = RoleData.getNullable(PriestRoleData.class, player);
        if (data == null) {
            return 1.0F;
        }
        float ramp = Math.min(1.0F, data.sprintTicks / (float) SPEED_RAMP_TICKS);
        if (data.autoSprint) {
            ramp = Math.max(ramp, 0.2F);
        }
        if (ramp <= 0.001F) {
            return 1.0F;
        }
        return 1.0F + (float) MAX_SPEED_MULTIPLIER * ramp;
    }

    public static void reset() {
        if (session != null && session.frozeGameTime) {
            // 游戏已结束时世界可能还在，解冻留给 OnGameEnd 前的 level
        }
        session = null;
    }

    public static void reset(ServerLevel level) {
        if (session != null && session.frozeGameTime && level != null) {
            SREGameTimeComponent time = SREGameTimeComponent.KEY.get(level);
            time.setTimeFrozen(false);
        }
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                clearMobility(player);
            }
            session = null;
            syncToAll(level, PriestHeavenStateS2CPacket.SOUND_NONE);
        } else {
            session = null;
        }
    }

    public static WinStatus allowGameEnd(ServerLevel level, WinStatus proposed) {
        if (session != null && session.pendingOfficialEnd) {
            if (!session.endingSent) {
                session.endingSent = true;
                syncToAll(level, PriestHeavenStateS2CPacket.SOUND_ENDING);
            }
            return WinStatus.PASSENGERS;
        }
        if (tryTransform(level)) {
            return WinStatus.NONE;
        }
        if (isActive() && !session.pendingOfficialEnd) {
            return WinStatus.NONE;
        }
        return WinStatus.NOT_MODIFY;
    }

    public static boolean tryTransform(ServerLevel level) {
        if (session != null || level == null) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        if (!game.isRunning()) {
            return false;
        }
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(level);
        ServerPlayer clockmaker = null;
        int civilians = 0;
        for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
            SRERole role = game.getRole(player);
            if (role == null) {
                continue;
            }
            if (role.identifier().equals(ModRoles.CLOCKMAKER_ID)
                    && modifiers.isModifier(player, NRModifiers.GODS_MISSION)) {
                clockmaker = player;
            } else if (role.canIncreaseSurvivingInnocents()) {
                // 只计算平民，不计算杀手和其他中立
                civilians++;
            }
        }
        if (clockmaker == null || civilians != 1) {
            return false;
        }
        transform(level, clockmaker);
        return true;
    }

    public static void transform(ServerLevel level, ServerPlayer clockmaker) {
        session = new Session();
        session.priestId = clockmaker.getUUID();
        session.phase = Phase.TRANSFORMED;
        session.visualTime = SREGameTimeComponent.KEY.get(level).getTime();

        RoleUtils.changeRole(clockmaker, ModRoles.PRIEST, true, true, false, false, true);
        applyMobility(clockmaker);

        // 转职音效由 SOUND_TRANSFORM 在客户端播放，避免空自定义音效槽和双响
        Component title = Component.translatable("message.noellesroles.priest.transform.title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("message.noellesroles.priest.transform.subtitle")
                .withStyle(ChatFormatting.YELLOW);
        Component broadcast = Component.translatable("message.noellesroles.priest.transform.broadcast")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (ServerPlayer player : level.players()) {
            SRENetworkMessageUtils.sendTitleTime(player, 10, 80, 20);
            SRENetworkMessageUtils.sendTitle(player, title);
            SRENetworkMessageUtils.sendSubtitle(player, subtitle);
            SRENetworkMessageUtils.sendBroadcast(player, broadcast);
        }

        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 10, () -> {
            if (session != null && session.phase == Phase.TRANSFORMED) {
                ServerPlayer priest = level.getServer().getPlayerList().getPlayer(session.priestId);
                if (priest != null) {
                    openChantScreen(priest);
                }
            }
        }));
        syncToAll(level, PriestHeavenStateS2CPacket.SOUND_TRANSFORM);
    }

    public static boolean openChantScreen(ServerPlayer player) {
        if (session == null || session.phase != Phase.TRANSFORMED || !isPriest(player)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.priest.chant.not_ready")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        OpenScreenManager.openScreen(player, OpenScreenManager.PRIEST_CHANT_SCREEN);
        return true;
    }

    public static void submitLyric(ServerPlayer player, String text) {
        if (session == null || session.phase != Phase.TRANSFORMED || !isPriest(player)) {
            return;
        }
        PriestRoleData data = RoleData.getNullable(PriestRoleData.class, player);
        int index = data == null ? 0 : data.lyricIndex;
        if (!PriestLyrics.matches(index, text)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.priest.chant.mismatch")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (data != null) {
            data.lyricIndex++;
            data.sync();
        }
        ServerLevel level = player.serverLevel();
        if (index + 1 >= PriestLyrics.COUNT) {
            startChanting(level);
            syncToAll(level, index);
            return;
        }
        player.displayClientMessage(Component.translatable("message.noellesroles.priest.chant.next",
                        String.format("%d", index + 2), String.format("%d", PriestLyrics.COUNT))
                .withStyle(ChatFormatting.GOLD), true);
        syncToAll(level, index);
    }

    private static void startChanting(ServerLevel level) {
        session.phase = Phase.CHANTING;
        session.chantRemain = CHANT_TICKS;
        // GUI 关闭，结束自动奔跑
        ServerPlayer priest = level.getServer().getPlayerList().getPlayer(session.priestId);
        if (priest != null) {
            PriestRoleData data = RoleData.getNullable(PriestRoleData.class, priest);
            if (data != null) {
                data.autoSprint = false;
                data.sync();
            }
        }
        Component title = Component.translatable("message.noellesroles.priest.chant.started.title")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("message.noellesroles.priest.chant.started.subtitle")
                .withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer player : level.players()) {
            SRENetworkMessageUtils.sendTitleTime(player, 5, 50, 15);
            SRENetworkMessageUtils.sendTitle(player, title);
            SRENetworkMessageUtils.sendSubtitle(player, subtitle);
        }
        syncToAll(level, PriestHeavenStateS2CPacket.SOUND_CHANTING);
    }

    private static void startAcceleration(ServerLevel level) {
        session.phase = Phase.ACCELERATING;
        session.accelElapsed = 0;
        SREGameTimeComponent time = SREGameTimeComponent.KEY.get(level);
        if (!time.isTimeFrozen()) {
            time.setTimeFrozen(true);
            session.frozeGameTime = true;
        }
        session.accelStartTime = Math.max(0, time.getTime());
        session.visualTime = session.accelStartTime;
        Component title = Component.translatable("message.noellesroles.priest.accel.title")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("message.noellesroles.priest.accel.subtitle")
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : level.players()) {
            SRENetworkMessageUtils.sendTitleTime(player, 8, 70, 15);
            SRENetworkMessageUtils.sendTitle(player, title);
            SRENetworkMessageUtils.sendSubtitle(player, subtitle);
            SRENetworkMessageUtils.sendBroadcast(player,
                    Component.translatable("message.noellesroles.priest.accel.broadcast")
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        }
        syncToAll(level, PriestHeavenStateS2CPacket.SOUND_ACCEL);
    }

    private static void startFinale(ServerLevel level) {
        session.phase = Phase.FINALE;
        session.finaleRemain = FINALE_TICKS;
        session.completedSequence = true;
        SREGameTimeComponent time = SREGameTimeComponent.KEY.get(level);
        time.time = 0;
        session.visualTime = 0;
        time.sync();
        Component title = Component.translatable("message.noellesroles.priest.finale.title")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("message.noellesroles.priest.finale.subtitle")
                .withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : level.players()) {
            SRENetworkMessageUtils.sendTitleTime(player, 10, 80, 20);
            SRENetworkMessageUtils.sendTitle(player, title);
            SRENetworkMessageUtils.sendSubtitle(player, subtitle);
        }
        syncToAll(level, PriestHeavenStateS2CPacket.SOUND_FINALE);
    }

    public static void tick(ServerLevel level) {
        if (session == null) {
            return;
        }
        ServerPlayer priest = level.getServer().getPlayerList().getPlayer(session.priestId);
        boolean priestAlive = priest != null && GameUtils.isPlayerAliveAndSurvival(priest);
        if (!priestAlive && (session.phase == Phase.TRANSFORMED || session.phase == Phase.CHANTING)) {
            cancel(level);
            return;
        }
        if (priestAlive) {
            applyMobility(priest);
        }
        if (isCinematicFreeze()) {
            freezeEveryone(level);
        }

        switch (session.phase) {
            case CHANTING -> {
                session.chantRemain--;
                if (session.chantRemain <= 0) {
                    startAcceleration(level);
                } else if (session.chantRemain % 20 == 0) {
                    syncToAll(level, PriestHeavenStateS2CPacket.SOUND_NONE);
                }
            }
            case ACCELERATING -> {
                session.accelElapsed++;
                accelerateTime(level);
                boolean last15 = session.accelElapsed >= ACCEL_TICKS - LAST_DRAMATIC_TICKS;
                if (session.accelElapsed >= ACCEL_TICKS) {
                    startFinale(level);
                } else if (last15 || session.accelElapsed % 2 == 0) {
                    syncToAll(level, PriestHeavenStateS2CPacket.SOUND_NONE);
                }
            }
            case FINALE -> {
                session.finaleRemain--;
                if (session.finaleRemain <= 0) {
                    session.pendingOfficialEnd = true;
                    syncToAll(level, PriestHeavenStateS2CPacket.SOUND_NONE);
                } else if (session.finaleRemain % 10 == 0) {
                    syncToAll(level, PriestHeavenStateS2CPacket.SOUND_NONE);
                }
            }
            default -> {
            }
        }
    }
    private static void accelerateTime(ServerLevel level) {
        float progress = Math.min(1.0F, session.accelElapsed / (float) ACCEL_TICKS);
        // 前慢后快，结束时一定落到 0
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        SREGameTimeComponent time = SREGameTimeComponent.KEY.get(level);
        int start = Math.max(session.accelStartTime, 1);
        int next = Math.max(0, Math.round(start * (1.0F - eased)));
        if (session.accelElapsed >= ACCEL_TICKS) {
            next = 0;
        }
        time.time = next;
        session.visualTime = next;

        long dayExtra = 80L + (long) (eased * 2800L);
        level.setDayTime(level.getDayTime() + dayExtra);

        boolean last15 = session.accelElapsed >= ACCEL_TICKS - LAST_DRAMATIC_TICKS;
        if (last15 || session.accelElapsed % 2 == 0) {
            time.sync();
        }
    }

    public static void tickMobility(ServerPlayer player) {
        if (isCinematicFreeze()) {
            freezePlayer(player);
            return;
        }
        applyMobility(player);
        PriestRoleData data = RoleData.getNullable(PriestRoleData.class, player);
        if (data == null) {
            return;
        }
        // GUI 打开期间自动奔跑；走动/冲刺都会把速度往上堆
        if (data.autoSprint) {
            player.setSprinting(true);
        }
        if (isMovingForSpeedRamp(player)) {
            data.sprintTicks = Math.min(SPEED_RAMP_TICKS, data.sprintTicks + 1);
        } else {
            data.sprintTicks = Math.max(0, data.sprintTicks - 2);
        }
    }

    private static void freezeEveryone(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            freezePlayer(player);
        }
    }

    private static void freezePlayer(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.xxa = 0.0F;
        player.zza = 0.0F;
        player.setSprinting(false);
        player.setJumping(false);
    }

    public static void applyMobility(ServerPlayer player) {
        var effect = player.getEffect(ModEffects.NO_COLLIDE);
        if (effect == null || effect.getDuration() <= 40) {
            player.addEffect(ModEffects.of(ModEffects.NO_COLLIDE, 10 * 20, 0, true, false, false));
        }
    }

    public static void clearMobility(ServerPlayer player) {
        updateSpeed(player, 0);
        player.removeEffect(ModEffects.NO_COLLIDE);
        PriestRoleData data = RoleData.getNullable(PriestRoleData.class, player);
        if (data != null) {
            data.autoSprint = false;
        }
        player.setSprinting(false);
    }

    private static void updateSpeed(ServerPlayer player, double multiplier) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(SPEED_MODIFIER_ID);
        if (multiplier > 0.001D) {
            attribute.addTransientModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID, multiplier, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void cancel(ServerLevel level) {
        if (session != null && session.frozeGameTime) {
            SREGameTimeComponent.KEY.get(level).setTimeFrozen(false);
        }
        if (session != null) {
            ServerPlayer priest = level.getServer().getPlayerList().getPlayer(session.priestId);
            if (priest != null) {
                clearMobility(priest);
            }
        }
        session = null;
        syncToAll(level, PriestHeavenStateS2CPacket.SOUND_NONE);
    }

    public static void syncToAll(ServerLevel level, int soundIndex) {
        PriestHeavenStateS2CPacket packet = currentPacket(level, soundIndex);
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private static PriestHeavenStateS2CPacket currentPacket(ServerLevel level, int soundIndex) {
        if (session == null) {
            return new PriestHeavenStateS2CPacket(Phase.IDLE.ordinal(), 0, 0, 0, 0, soundIndex);
        }
        int lyricIndex = 0;
        ServerPlayer priest = level.getServer().getPlayerList().getPlayer(session.priestId);
        if (priest != null) {
            PriestRoleData data = RoleData.getNullable(PriestRoleData.class, priest);
            if (data != null) {
                lyricIndex = data.lyricIndex;
            }
        }
        return new PriestHeavenStateS2CPacket(
                session.phase.ordinal(),
                lyricIndex,
                session.chantRemain,
                session.accelElapsed,
                session.visualTime,
                soundIndex);
    }
}
