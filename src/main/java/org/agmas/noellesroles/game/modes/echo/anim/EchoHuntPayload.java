package org.agmas.noellesroles.game.modes.echo.anim;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

/**
 * 追捕者动作广播：告诉同维度的客户端「谁在什么时候做了哪一种动作」。
 *
 * <p>只传表现参数（动作种类、序号、比赛 tick、是否扑击、施法母题），不传坐标、生命或任何
 * 可被反推的私有信息；客户端只把它当作一次性动画触发，不参与判定。动作以 {@code serial}
 * 去重，重连后序号重新开始并配合 {@code match} 校验，避免旧包驱动新一局的表现。
 */
public record EchoHuntPayload(UUID match, UUID actor, Kind kind, int serial, long tick,
                              boolean empowered, int style) implements CustomPacketPayload {
    public enum Kind { CHARGE, STRIKE, MISS, INSTINCT, CAST, WIPE }

    /** 没有施法母题时使用；{@link EchoSkillCastAnim.Style} 的序号不可能为负。 */
    public static final int NO_STYLE = -1;

    public EchoHuntPayload {
        Objects.requireNonNull(match);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(kind);
        if (serial < 0) throw new IllegalArgumentException("动作序号不能为负");
        if (tick < 0) throw new IllegalArgumentException("动作 tick 不能为负");
        if (style < NO_STYLE || style >= EchoSkillCastAnim.Style.values().length) style = NO_STYLE;
    }

    /** 施法母题；没有时为 {@code null}。 */
    public EchoSkillCastAnim.Style castStyle() {
        return style == NO_STYLE ? null : EchoSkillCastAnim.Style.values()[style];
    }

    public static final Type<EchoHuntPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/hunt_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoHuntPayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeUUID(p.match()); b.writeUUID(p.actor()); b.writeEnum(p.kind());
        b.writeVarInt(p.serial()); b.writeVarLong(p.tick());
        b.writeBoolean(p.empowered()); b.writeByte(p.style());
    }, b -> new EchoHuntPayload(b.readUUID(), b.readUUID(), b.readEnum(Kind.class),
            b.readVarInt(), b.readVarLong(), b.readBoolean(), b.readByte()));

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}