package org.agmas.noellesroles.game.modes.echo.action;

import java.util.*;
import java.util.function.Predicate;

/** 每人一个引导；原版持续右键交互续约，服务器时钟决定完成。 */
public final class EchoActionService {
    public static final int LEASE_TICKS = 10;
    public record Action(UUID id, UUID player, String target, long startedAt, int duration,
                         double startX, double startZ, boolean exclusive) { }
    private static final class Lease {
        final Action action;
        long renewedAt;
        Lease(Action action, long now) { this.action=action; renewedAt=now; }
    }
    private final Map<UUID,Lease> players = new LinkedHashMap<>();
    private final Map<String,UUID> locks = new HashMap<>();

    public Optional<Action> request(UUID player, String target, int duration, long now,
                                     double x, double z, boolean exclusive) {
        Objects.requireNonNull(player); Objects.requireNonNull(target);
        if (duration<1 || now<0 || !Double.isFinite(x) || !Double.isFinite(z)) throw new IllegalArgumentException("无效引导");
        Lease existing=players.get(player);
        if(existing!=null && existing.action.target().equals(target)) {
            if(now<existing.renewedAt) throw new IllegalArgumentException("时钟倒退");
            existing.renewedAt=now;
            return Optional.of(existing.action);
        }
        cancel(player);
        if(exclusive && locks.containsKey(target)) return Optional.empty();
        Action action=new Action(UUID.randomUUID(),player,target,now,duration,x,z,exclusive);
        players.put(player,new Lease(action,now));
        if(exclusive) locks.put(target,action.id());
        return Optional.of(action);
    }

    public Optional<Action> current(UUID player) {
        Lease lease=players.get(player); return lease==null?Optional.empty():Optional.of(lease.action);
    }
    public void cancel(UUID player) {
        Lease lease=players.remove(player);
        if(lease!=null && lease.action.exclusive()) locks.remove(lease.action.target(),lease.action.id());
    }
    public boolean cancel(UUID player, UUID expectedAction) {
        Lease lease=players.get(player);
        if(lease==null || !lease.action.id().equals(expectedAction)) return false;
        cancel(player); return true;
    }
    public void cancelAll() { players.clear(); locks.clear(); }

    /** LinkedHashMap保留创建次序；完成列表仍需业务服务逐项重验容量。 */
    public List<Action> advance(long now, Predicate<Action> valid) {
        List<Action> completed=new ArrayList<>();
        for(Lease lease:List.copyOf(players.values())) {
            Action action=lease.action;
            if(now<lease.renewedAt || now-lease.renewedAt>LEASE_TICKS || !valid.test(action)) cancel(action.player());
            else if(now-action.startedAt()>=action.duration()) {
                cancel(action.player()); completed.add(action);
            }
        }
        return List.copyOf(completed);
    }
}
