package org.agmas.noellesroles.game.modes.echo.result;

import org.agmas.noellesroles.game.modes.echo.state.EchoContract;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoLifeState;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 停局前冻结的终局事实，不保留玩家实体或可变职业数据。 */
public record EchoResultSnapshot(UUID matchId, String mapId, Outcome outcome, String reason,
                                 long endedAtTick, boolean debug, List<PlayerResult> players,String rulesHash) {
    public enum Outcome { CARRIERS, INTERCEPTORS, VOID }
    public EchoResultSnapshot(UUID matchId,String mapId,Outcome outcome,String reason,long endedAtTick,boolean debug,List<PlayerResult> players) {
        this(matchId,mapId,outcome,reason,endedAtTick,debug,players,"");
    }

    public EchoResultSnapshot {
        Objects.requireNonNull(matchId);
        Objects.requireNonNull(mapId);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(rulesHash);
        if(!rulesHash.isEmpty() && !rulesHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("规则摘要无效");
        if (endedAtTick < 0) throw new IllegalArgumentException("终局时间不能为负");
        players = List.copyOf(players);
        if (players.stream().map(PlayerResult::uuid).distinct().count() != players.size()) {
            throw new IllegalArgumentException("结算名单含重复玩家");
        }
        if (outcome == Outcome.VOID && players.stream().anyMatch(PlayerResult::hasWin)) {
            throw new IllegalArgumentException("中止局没有正式获胜者");
        }
    }

    public boolean didWin(UUID uuid) {
        return players.stream().anyMatch(player -> player.uuid().equals(uuid) && player.hasWin());
    }

    public record PlayerResult(UUID uuid, String name, EchoFaction faction, EchoLifeState lifeState,
                               EchoContract contract, boolean contractSucceeded, boolean connected,
                               boolean hasWin, org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization specialization) {
        public PlayerResult(UUID uuid,String name,EchoFaction faction,EchoLifeState lifeState,EchoContract contract,
                            boolean contractSucceeded,boolean connected,boolean hasWin) {
            this(uuid,name,faction,lifeState,contract,contractSucceeded,connected,hasWin,null);
        }
        public PlayerResult {
            Objects.requireNonNull(uuid);
            Objects.requireNonNull(name);
            Objects.requireNonNull(faction);
            Objects.requireNonNull(lifeState);
        }
    }
}
