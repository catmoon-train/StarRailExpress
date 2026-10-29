package org.agmas.noellesroles.game.modes.echo.rules;

import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.List;
import java.util.Objects;

/** 撤离无名额门槛：一人撤离锁定胜利，其余人仍有机会；只消费服务器已提交事实。 */
public final class EchoWinPolicy {
    public enum Outcome { NONE, CARRIERS, INTERCEPTORS, VOID }
    /** 旧配额原因保留给历史报告，新对局不再产生。 */
    public enum Reason { PENDING, ABORTED, ESCAPE_QUOTA, INSUFFICIENT_CARRIERS, NO_INTERCEPTOR, TIME_EXPIRED,
                         ESCAPE_COMPLETED, NO_CARRIERS_LEFT }
    public record Decision(Outcome outcome, Reason reason) { }
    public record PlayerFact(EchoFaction faction, EchoLifeState life, boolean connected, boolean inGrace) {
        public PlayerFact { Objects.requireNonNull(faction); Objects.requireNonNull(life); }
        public boolean available() { return connected || inGrace; }
    }
    /** quota 仅兼容旧调用与报告，0 表示不限名额，任何取值都不再参与胜负。 */
    public record Facts(EchoPhase phase, int remainingTicks, int quota, boolean aborted, List<PlayerFact> players) {
        public Facts {
            Objects.requireNonNull(phase);
            players = List.copyOf(players);
            if (remainingTicks < 0 || quota < 0) throw new IllegalArgumentException("无效胜负输入");
        }
    }
    private EchoWinPolicy() { }

    public static Decision evaluate(Facts facts) {
        if (facts.aborted()) return new Decision(Outcome.VOID, Reason.ABORTED);
        if (!facts.phase().acceptsActions()) return new Decision(Outcome.NONE, Reason.PENDING);
        boolean escaped = facts.phase() == EchoPhase.EVACUATION && facts.players().stream().anyMatch(p ->
                p.faction() == EchoFaction.CARRIER && p.life() == EchoLifeState.ESCAPED);
        boolean possible = facts.players().stream().anyMatch(p -> p.faction() == EchoFaction.CARRIER
                && p.available() && p.life().potentiallyEscapable());
        if (!possible) return escaped ? new Decision(Outcome.CARRIERS, Reason.ESCAPE_COMPLETED)
                : new Decision(Outcome.INTERCEPTORS, Reason.NO_CARRIERS_LEFT);
        boolean wolf = facts.players().stream().anyMatch(p -> p.faction() == EchoFaction.INTERCEPTOR
                && p.available() && p.life().potentiallyEscapable());
        // 已成功撤离的事实不会因追捕方随后断线而失效，也不会抢停其他人的撤离机会。
        if (!wolf && !escaped) return new Decision(Outcome.VOID, Reason.NO_INTERCEPTOR);
        if (facts.remainingTicks() == 0) return escaped ? new Decision(Outcome.CARRIERS, Reason.ESCAPE_COMPLETED)
                : new Decision(Outcome.INTERCEPTORS, Reason.TIME_EXPIRED);
        return new Decision(Outcome.NONE, Reason.PENDING);
    }

    public static boolean didPlayerWin(Decision result, PlayerFact player, EchoContract contract,
                                        boolean successLocked, int resolvedPacks) {
        if (result.outcome() == Outcome.NONE || result.outcome() == Outcome.VOID) return false;
        if (player.faction() == EchoFaction.CARRIER) return result.outcome() == Outcome.CARRIERS;
        if (player.faction() == EchoFaction.INTERCEPTOR) return result.outcome() == Outcome.INTERCEPTORS;
        if (contract == null) return false;
        if (contract.requiresFinalSurvival()) return resolvedPacks >= 3 && player.connected() && player.life().standing();
        return successLocked;
    }
}
