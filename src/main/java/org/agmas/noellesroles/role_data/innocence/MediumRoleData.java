/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.MeetingStartEvent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity;
import org.agmas.noellesroles.content.entity.SaltedFishBodyEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.MediumSeanceCloseS2CPacket;
import org.agmas.noellesroles.packet.MediumSeanceOpenS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MediumRoleData extends SimpleRoleData {

    public static final int SEANCE_COST = 150;
    public static final int SEANCE_SECONDS = 20;
    public static final int SEANCE_TICKS = SEANCE_SECONDS * 20;
    public static final int ANSWER_COOLDOWN_TICKS = 10;

    /** 死者 UUID -> 通灵师 UUID，用于语音例外与回答包校验。 */
    private static final Map<UUID, UUID> DEAD_TO_MEDIUM = new ConcurrentHashMap<>();

    public enum SeanceAnswer {
        YES,
        NO,
        UNKNOWN,
        YES_OR_NO;

        @Nullable
        public static SeanceAnswer fromId(int id) {
            SeanceAnswer[] values = values();
            if (id < 0 || id >= values.length) {
                return null;
            }
            return values[id];
        }

        public Component translatable() {
            return Component.translatable("message.noellesroles.medium.answer." + name().toLowerCase());
        }
    }

    public long sessionEndTick;
    public int lastAnswerId = -1;

    private UUID spiritUuid;
    private UUID corpseUuid;
    private long lastAnswerGameTick;

    private boolean savedPenalty;
    private long savedPenaltyExpiry;
    private UUID savedLimitCameraUUID;
    private Vec3 savedLimitPos;
    private boolean savedMorePenalty;

    static {
        MeetingStartEvent.EVENT.register((serverLevel, reporter) -> endAllSeances(serverLevel));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer disconnected = handler.getPlayer();
            MediumRoleData selfData = RoleData.getNullable(MediumRoleData.class, disconnected);
            if (selfData != null && selfData.isSessionActive()) {
                selfData.endSeance(false);
            }
            UUID mediumId = DEAD_TO_MEDIUM.remove(disconnected.getUUID());
            if (mediumId != null) {
                ServerPlayer medium = server.getPlayerList().getPlayer(mediumId);
                if (medium != null) {
                    MediumRoleData data = RoleData.getNullable(MediumRoleData.class, medium);
                    if (data != null) {
                        data.endSeance(true);
                    }
                }
            }
        });
    }

    public MediumRoleData(RoleDataContext context) {
        super(context);
    }

    public static boolean isSeanceVoiceAllowed(Player sender, Player receiver) {
        UUID mediumOfReceiver = DEAD_TO_MEDIUM.get(receiver.getUUID());
        return mediumOfReceiver != null && mediumOfReceiver.equals(sender.getUUID());
    }

    public static void endAllSeances(ServerLevel serverLevel) {
        for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
            MediumRoleData data = RoleData.getNullable(MediumRoleData.class, p);
            if (data != null && data.isSessionActive()) {
                data.endSeance(true);
            }
        }
    }

    public boolean isSessionActive() {
        if (spiritUuid == null || sessionEndTick <= 0) {
            return false;
        }
        return GameUtils.getTicksFromGameStart(player.level()) < sessionEndTick;
    }

    public boolean tryStartSeance(PlayerBodyEntity body) {
        if (!(player instanceof ServerPlayer medium)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(medium.level());
        if (!gameWorld.isRunning() || !gameWorld.isSkillAvailable) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(medium) || medium.isSpectator()) {
            return false;
        }
        if (isSessionActive()) {
            medium.displayClientMessage(
                    Component.translatable("message.noellesroles.medium.already_active")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (body == null || DoomedSinnerBodyEntity.isDoomedSinnerBody(body)
                || body instanceof SaltedFishBodyEntity) {
            medium.displayClientMessage(
                    Component.translatable("message.noellesroles.medium.invalid_body")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        UUID deadUuid = body.getPlayerUuid();
        if (deadUuid == null) {
            medium.displayClientMessage(
                    Component.translatable("message.noellesroles.medium.invalid_body")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        ServerPlayer spirit = medium.server.getPlayerList().getPlayer(deadUuid);
        if (spirit == null) {
            medium.displayClientMessage(
                    Component.translatable("message.noellesroles.medium.spirit_offline")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (GameUtils.isPlayerAliveAndSurvival(spirit)) {
            medium.displayClientMessage(
                    Component.translatable("message.noellesroles.medium.not_dead")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (DEAD_TO_MEDIUM.containsKey(deadUuid)) {
            medium.displayClientMessage(
                    Component.translatable("message.noellesroles.medium.already_questioned")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (!MoneyUtils.hasBalance(medium, SEANCE_COST)) {
            MoneyUtils.sendNotEnoughtMoneyMessage(medium, SEANCE_COST);
            return false;
        }

        MoneyUtils.cost(medium, SEANCE_COST);
        ConfigWorldComponent.onPlayerUsedSkill(medium);

        this.spiritUuid = deadUuid;
        this.corpseUuid = body.getUUID();
        this.sessionEndTick = GameUtils.getTicksFromGameStart(medium.level()) + SEANCE_TICKS;
        this.lastAnswerId = -1;
        this.lastAnswerGameTick = 0;
        DEAD_TO_MEDIUM.put(deadUuid, medium.getUUID());

        bindSpiritToCorpse(spirit, body);
        spirit.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, SEANCE_TICKS + 40, 0, true, false, false));

        ServerPlayNetworking.send(spirit, new MediumSeanceOpenS2CPacket(
                medium.getUUID(), medium.getGameProfile().getName(), this.sessionEndTick));

        medium.displayClientMessage(
                Component.translatable("message.noellesroles.medium.seance_started", spirit.getName(), SEANCE_SECONDS)
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                true);
        spirit.displayClientMessage(
                Component.translatable("message.noellesroles.medium.spirit_summoned", medium.getName(), SEANCE_SECONDS)
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                false);

        medium.level().playSound(null, medium.getX(), medium.getY(), medium.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);

        SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.medium.seance",
                        GameReplayUtils.getReplayPlayerDisplayText(medium, true),
                        GameReplayUtils.getReplayPlayerDisplayText(spirit, true)));

        this.sync();
        return true;
    }

    public static void handleAnswerPacket(ServerPlayer spirit, int answerId) {
        UUID mediumId = DEAD_TO_MEDIUM.get(spirit.getUUID());
        if (mediumId == null) {
            return;
        }
        ServerPlayer medium = spirit.server.getPlayerList().getPlayer(mediumId);
        if (medium == null) {
            return;
        }
        MediumRoleData data = RoleData.getNullable(MediumRoleData.class, medium);
        if (data != null) {
            data.onAnswer(spirit, answerId);
        }
    }

    public void onAnswer(ServerPlayer spirit, int answerId) {
        if (!(player instanceof ServerPlayer medium)) {
            return;
        }
        if (!isSessionActive() || spiritUuid == null || !spiritUuid.equals(spirit.getUUID())) {
            return;
        }
        SeanceAnswer answer = SeanceAnswer.fromId(answerId);
        if (answer == null) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(medium.level());
        if (now - lastAnswerGameTick < ANSWER_COOLDOWN_TICKS) {
            return;
        }
        lastAnswerGameTick = now;
        lastAnswerId = answerId;

        Component answerText = answer.translatable();
        medium.displayClientMessage(
                Component.translatable("message.noellesroles.medium.received_answer", spirit.getName(), answerText)
                        .withStyle(ChatFormatting.GOLD),
                false);
        spirit.displayClientMessage(
                Component.translatable("message.noellesroles.medium.you_answered", answerText)
                        .withStyle(ChatFormatting.YELLOW),
                true);
        this.sync();
    }

    public void endSeance(boolean notify) {
        if (spiritUuid == null && sessionEndTick <= 0) {
            return;
        }
        UUID dead = this.spiritUuid;
        if (dead != null) {
            DEAD_TO_MEDIUM.remove(dead, player.getUUID());
            if (player instanceof ServerPlayer medium) {
                ServerPlayer spirit = medium.server.getPlayerList().getPlayer(dead);
                if (spirit != null) {
                    restoreSpiritPenalty(spirit);
                    spirit.removeEffect(ModEffects.VOICE_SILENCE);
                    ServerPlayNetworking.send(spirit, new MediumSeanceCloseS2CPacket());
                    if (notify) {
                        spirit.displayClientMessage(
                                Component.translatable("message.noellesroles.medium.seance_ended")
                                        .withStyle(ChatFormatting.GRAY),
                                true);
                    }
                }
            }
        }
        if (notify && player instanceof ServerPlayer medium) {
            medium.displayClientMessage(
                    Component.translatable("message.noellesroles.medium.seance_ended")
                            .withStyle(ChatFormatting.GRAY),
                    true);
        }
        this.spiritUuid = null;
        this.corpseUuid = null;
        this.sessionEndTick = 0;
        this.lastAnswerId = -1;
        this.lastAnswerGameTick = 0;
        this.savedPenalty = false;
        this.savedLimitCameraUUID = null;
        this.savedLimitPos = null;
        this.sync();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer medium)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(medium.level());
        if (!gameWorld.isRole(medium, ModRoles.MEDIUM)) {
            return;
        }
        if (!isSessionActive()) {
            if (spiritUuid != null || sessionEndTick > 0) {
                endSeance(true);
            }
            return;
        }
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(medium)) {
            endSeance(true);
            return;
        }
        ServerPlayer spirit = medium.server.getPlayerList().getPlayer(spiritUuid);
        if (spirit == null || GameUtils.isPlayerAliveAndSurvival(spirit)) {
            endSeance(true);
            return;
        }
        keepSpiritOnCorpse(spirit);
        if (!spirit.hasEffect(ModEffects.VOICE_SILENCE)) {
            int remaining = (int) Math.max(1, sessionEndTick - GameUtils.getTicksFromGameStart(medium.level()));
            spirit.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, remaining + 20, 0, true, false, false));
        }
    }

    @Override
    public void clear() {
        if (spiritUuid != null || sessionEndTick > 0) {
            endSeance(false);
        }
    }

    private void bindSpiritToCorpse(ServerPlayer spirit, PlayerBodyEntity body) {
        DeathPenaltyComponent dp = DeathPenaltyComponent.KEY.get(spirit);
        this.savedPenalty = true;
        this.savedPenaltyExpiry = dp.penaltyExpiry;
        this.savedLimitCameraUUID = dp.limitCameraUUID;
        this.savedLimitPos = dp.limitPos;
        this.savedMorePenalty = dp.morePenalty;

        if (dp.limitCameraUUID != null) {
            spirit.setCamera(spirit);
            dp.limitCameraUUID = null;
        }
        Vec3 pos = corpsePos(body);
        dp.limitPos = pos;
        if (dp.penaltyExpiry == 0) {
            dp.penaltyExpiry = -1;
        }
        dp.sync();
        spirit.teleportTo(pos.x, pos.y, pos.z);
    }

    private void restoreSpiritPenalty(ServerPlayer spirit) {
        if (!savedPenalty) {
            return;
        }
        DeathPenaltyComponent dp = DeathPenaltyComponent.KEY.get(spirit);
        dp.penaltyExpiry = savedPenaltyExpiry;
        dp.morePenalty = savedMorePenalty;
        dp.limitPos = savedLimitPos;
        dp.limitCameraUUID = savedLimitCameraUUID;
        if (savedLimitCameraUUID != null) {
            Entity camera = spirit.serverLevel().getEntity(savedLimitCameraUUID);
            if (camera != null && camera.isAlive()) {
                spirit.setCamera(camera);
            } else {
                spirit.setCamera(spirit);
                dp.limitCameraUUID = null;
            }
        } else {
            spirit.setCamera(spirit);
        }
        if (savedLimitPos != null) {
            spirit.teleportTo(savedLimitPos.x, savedLimitPos.y, savedLimitPos.z);
        }
        dp.sync();
    }

    private void keepSpiritOnCorpse(ServerPlayer spirit) {
        Vec3 pos = currentCorpsePos(spirit.serverLevel());
        if (pos == null) {
            return;
        }
        DeathPenaltyComponent dp = DeathPenaltyComponent.KEY.get(spirit);
        if (dp.limitCameraUUID != null) {
            spirit.setCamera(spirit);
            dp.limitCameraUUID = null;
            dp.sync();
        }
        if (dp.limitPos == null || dp.limitPos.distanceToSqr(pos) > 0.25) {
            dp.limitPos = pos;
            dp.sync();
        }
        if (spirit.distanceToSqr(pos) > 4.0) {
            spirit.teleportTo(pos.x, pos.y, pos.z);
        }
    }

    @Nullable
    private Vec3 currentCorpsePos(ServerLevel level) {
        if (corpseUuid == null) {
            return null;
        }
        Entity entity = level.getEntity(corpseUuid);
        if (entity instanceof PlayerBodyEntity body) {
            return corpsePos(body);
        }
        return null;
    }

    private static Vec3 corpsePos(PlayerBodyEntity body) {
        return new Vec3(body.getX(), body.getY() + 0.2, body.getZ());
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong("sessionEndTick", this.sessionEndTick);
        tag.putInt("lastAnswerId", this.lastAnswerId);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.sessionEndTick = tag.getLong("sessionEndTick");
        this.lastAnswerId = tag.contains("lastAnswerId") ? tag.getInt("lastAnswerId") : -1;
    }
}
