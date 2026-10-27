package org.agmas.noellesroles.game.modes.echo.editor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 编辑工具物品发出的单个操作请求。三个字符串槽按操作解释：
 *
 * <pre>
 *   NEW / EDIT     a=mapId
 *   MARK           a=kind  b=id  c=minPlayers
 *   IDENTIFY       a=id（可空，空则自动生成）  b=minPlayers（可空，默认 6）
 *                  机器 kind 与坐标一律由服务端重新射线识别，不信客户端
 *   CORNER_A/B     —（取玩家准星对着的方块）
 *   CORNER_INPUT   a=a|b  b="x y z"（输入框手动设入的角点坐标）
 *   POINTS         a=kind（列出该类型全部点位：id/人数档/坐标）
 *   POINT_META     a=kind  b=oldId  c="newId|minPlayers"（就地改 id/人数档，坐标不变；任一侧可留空=不改）
 *   VOLUME         a=kind  b=id
 *   REMOVE         a=field b=id
 *   SAVE           a=mapId
 *   SAVE_CONFIRM   a=nonce
 *   其余           —
 * </pre>
 *
 * <p>服务端只信任操作者身份与这些枚举，绝不执行客户端给的命令字符串。
 */
public record EchoEditorActionPayload(int sequence, Op op, String a, String b, String c) implements CustomPacketPayload {
    public static final int MAX_ARG = 64;

    public enum Op {
        NEW, EDIT, CANCEL, MARK, IDENTIFY, CORNER_A, CORNER_B, CORNER_INPUT, POINTS, POINT_META, VOLUME, REMOVE, LIST, VALIDATE, PREVIEW,
        SAVE, SAVE_CONFIRM, GATE_ON, GATE_OFF, RELOAD
    }

    public static final Type<EchoEditorActionPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/editor_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EchoEditorActionPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.sequence()); buf.writeEnum(p.op());
        buf.writeUtf(p.a(), MAX_ARG); buf.writeUtf(p.b(), MAX_ARG); buf.writeUtf(p.c(), MAX_ARG);
    }, buf -> new EchoEditorActionPayload(buf.readVarInt(), buf.readEnum(Op.class),
            buf.readUtf(MAX_ARG), buf.readUtf(MAX_ARG), buf.readUtf(MAX_ARG)));

    public EchoEditorActionPayload {
        Objects.requireNonNull(op); a = a == null ? "" : a; b = b == null ? "" : b; c = c == null ? "" : c;
        if (a.length() > MAX_ARG || b.length() > MAX_ARG || c.length() > MAX_ARG) throw new IllegalArgumentException("编辑参数过长");
    }

    public static EchoEditorActionPayload of(Op op) { return new EchoEditorActionPayload(0, op, "", "", ""); }

    public static EchoEditorActionPayload of(Op op, String a) { return new EchoEditorActionPayload(0, op, a, "", ""); }

    public static EchoEditorActionPayload of(Op op, String a, String b) { return new EchoEditorActionPayload(0, op, a, b, ""); }

    public static EchoEditorActionPayload of(Op op, String a, String b, String c) {
        return new EchoEditorActionPayload(0, op, a, b, c);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
