package net.exmo.sre.sixtyseconds.network;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务端→客户端：60s 模式结算数据，携带玩家状态详情供自定义结算页面展示。
 */
public record SixtySecondsEndGamePayload(GameUtils.WinStatus winStatus, int dayNumber,
        List<PlayerResult> players) implements CustomPacketPayload {

    public static final Type<SixtySecondsEndGamePayload> ID =
            new Type<>(Noellesroles.id("sixty_seconds_end_game"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsEndGamePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(val -> GameUtils.WinStatus.values()[val],
                            status -> status.ordinal()),
                    SixtySecondsEndGamePayload::winStatus,
                    ByteBufCodecs.VAR_INT,
                    SixtySecondsEndGamePayload::dayNumber,
                    PlayerResult.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    SixtySecondsEndGamePayload::players,
                    SixtySecondsEndGamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 单个玩家的结算数据。 */
    public record PlayerResult(UUID uuid, String name, boolean wasDead, boolean isMonster,
            boolean helicopterEvacuated, int teamId, boolean hasWon) {

        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerResult> STREAM_CODEC =
            StreamCodec.ofMember(
                    (pr, buf) -> {
                        buf.writeUtf(pr.uuid().toString());
                        buf.writeUtf(pr.name());
                        buf.writeBoolean(pr.wasDead());
                        buf.writeBoolean(pr.isMonster());
                        buf.writeBoolean(pr.helicopterEvacuated());
                        buf.writeVarInt(pr.teamId());
                        buf.writeBoolean(pr.hasWon());
                    },
                    buf -> new PlayerResult(
                            UUID.fromString(buf.readUtf()),
                            buf.readUtf(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readVarInt(),
                            buf.readBoolean()));

        public StatusCategory category() {
            if (helicopterEvacuated) return StatusCategory.EVACUATED;
            if (isMonster) return StatusCategory.MONSTER;
            if (wasDead) return StatusCategory.DEAD;
            return StatusCategory.SURVIVED;
        }
    }

    public enum StatusCategory {
        SURVIVED, DEAD, MONSTER, EVACUATED
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private GameUtils.WinStatus winStatus;
        private int dayNumber;
        private final List<PlayerResult> players = new ArrayList<>();

        public Builder winStatus(GameUtils.WinStatus ws) {
            this.winStatus = ws;
            return this;
        }

        public Builder dayNumber(int day) {
            this.dayNumber = day;
            return this;
        }

        public Builder addPlayer(PlayerResult pr) {
            this.players.add(pr);
            return this;
        }

        public SixtySecondsEndGamePayload build() {
            return new SixtySecondsEndGamePayload(winStatus, dayNumber, List.copyOf(players));
        }
    }
}
