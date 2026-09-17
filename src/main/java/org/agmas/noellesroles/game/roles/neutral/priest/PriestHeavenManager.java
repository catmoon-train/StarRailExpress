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

import java.util.ArrayList;
import java.util.List;
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

    public static final int CHANT_TICKS = 120 * 20;
    public static final int ACCEL_TICKS = 30 * 20;
    public static final int LAST_DRAMATIC_TICKS = 15 * 20;
    public static final int FINALE_TICKS = 8 * 20;
    public static final int SPEED_RAMP_TICKS = 8 * 20;
    public static final double MAX_SPEED_MULTIPLIER = 4.0D;
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

    public static boolean isPriest(ServerPlayer player) {
        return session != null && player != null && player.getUUID().equals(session.priestId);
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
        List<ServerPlayer> alive = new ArrayList<>();
        ServerPlayer clockmaker = null;
        ServerPlayer civilian = null;
        for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
            alive.add(player);
            SRERole role = game.getRole(player);
            if (role != null && role.identifier().equals(ModRoles.CLOCKMAKER_ID)
                    && modifiers.isModifier(player, NRModifiers.GODS_MISSION)) {
                clockmaker = player;
            } else if (role != null && role.canIncreaseSurvivingInnocents()) {
                civilian = player;
            }
        }
        if (alive.size() != 2 || clockmaker == null || civilian == null || clockmaker == civilian) {
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
        syncToAll(level, PriestHeavenStateS2CPacket.SOUND_NONE);
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
        session.visualTime = time.getTime();
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
        syncToAll(level, PriestHeavenStateS2CPacket.SOUND_NONE);
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
        syncToAll(level, PriestHeavenStateS2CPacket.SOUND_NONE);
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
                } else if (last15 || session.accelElapsed % 5 == 0) {
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
        float progress = session.accelElapsed / (float) ACCEL_TICKS;
        float eased = progress * progress;
        long dayExtra = 20L + (long) (eased * 800L);
        level.setDayTime(level.getDayTime() + dayExtra);

        SREGameTimeComponent time = SREGameTimeComponent.KEY.get(level);
        int extra = 1 + (int) (eased * 20);
        time.time = Math.max(0, time.time - extra);
        session.visualTime = time.time;
        boolean last15 = session.accelElapsed >= ACCEL_TICKS - LAST_DRAMATIC_TICKS;
        if (last15 || session.accelElapsed % 10 == 0) {
            time.sync();
        }
    }

    public static void tickMobility(ServerPlayer player) {
        applyMobility(player);
        PriestRoleData data = RoleData.getNullable(PriestRoleData.class, player);
        if (data == null) {
            return;
        }
        if (player.isSprinting()) {
            data.sprintTicks = Math.min(SPEED_RAMP_TICKS, data.sprintTicks + 1);
        } else {
            data.sprintTicks = Math.max(0, data.sprintTicks - 2);
        }
        double multiplier = MAX_SPEED_MULTIPLIER * (data.sprintTicks / (double) SPEED_RAMP_TICKS);
        updateSpeed(player, multiplier);
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
