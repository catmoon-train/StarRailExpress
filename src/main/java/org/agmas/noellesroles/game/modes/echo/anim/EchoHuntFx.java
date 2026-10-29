package org.agmas.noellesroles.game.modes.echo.anim;

import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.state.EchoSkill;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追捕者动作的服务端广播：把「刚发生了什么」发成一包，客户端只负责播。
 *
 * <p>序号按玩家递增，用于客户端丢弃重复/乱序包；本类不保存任何判定状态，
 * 也不改变战斗结果，广播失败不影响主流程。
 *
 * <p>广播半径 96 格：追击通常发生在近距，超远距离的旁观者不需要动作细节，
 * 也避免把一包动作发给整张地图。
 */
public final class EchoHuntFx {
    private EchoHuntFx() { }

    private static final Map<UUID, Integer> SERIALS = new ConcurrentHashMap<>();
    /** 动作广播距离（格）。 */
    public static final double RANGE = 96.0D;

    public static void charge(EchoMatch match, ServerPlayer actor) {
        broadcast(match, actor, EchoHuntPayload.Kind.CHARGE, false, EchoHuntPayload.NO_STYLE);
    }

    public static void strike(EchoMatch match, ServerPlayer actor, boolean empowered) {
        broadcast(match, actor, EchoHuntPayload.Kind.STRIKE, empowered, EchoHuntPayload.NO_STYLE);
    }

    public static void miss(EchoMatch match, ServerPlayer actor) {
        broadcast(match, actor, EchoHuntPayload.Kind.MISS, false, EchoHuntPayload.NO_STYLE);
    }

    /**
     * 擦刀：命中后甩血 + 横抹收刀。
     *
     * @param hit true = 砍实了（有擦血手势与血珠），false = 挥空（只做收刀）
     */
    public static void wipe(EchoMatch match, ServerPlayer actor, boolean hit) {
        // empowered 位复用为「是否命中」，避免为擦刀再开一个字段。
        broadcast(match, actor, EchoHuntPayload.Kind.WIPE, hit, EchoHuntPayload.NO_STYLE);
    }

    public static void instinct(EchoMatch match, ServerPlayer actor) {
        broadcast(match, actor, EchoHuntPayload.Kind.INSTINCT, true, EchoSkillCastAnim.Style.SCAN.ordinal());
    }

    /** 任意专长的主动技能施法动作：动作母题由技能决定，敌人与队友都能看见。 */
    public static void cast(EchoMatch match, ServerPlayer actor, EchoSkill skill) {
        broadcast(match, actor, EchoHuntPayload.Kind.CAST, false, EchoSkillCastAnim.style(skill).ordinal());
    }

    private static void broadcast(EchoMatch match, ServerPlayer actor, EchoHuntPayload.Kind kind,
                                  boolean empowered, int style) {
        if (match == null || actor == null || match.faction(actor.getUUID()) == null) return;
        int serial = SERIALS.merge(actor.getUUID(), 1, Integer::sum);
        var payload = new EchoHuntPayload(match.id(), actor.getUUID(), kind, serial, match.matchTick(), empowered, style);
        for (ServerPlayer receiver : match.onlinePlayers()) {
            if (receiver.level() != actor.level()) continue;
            if (receiver.distanceToSqr(actor) > RANGE * RANGE) continue;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(receiver, payload);
        }
    }

    /** 对局清理时丢掉序号，避免下一局沿用旧值。 */
    public static void clear(UUID actor) { SERIALS.remove(actor); }

    public static void clearAll() { SERIALS.clear(); }
}