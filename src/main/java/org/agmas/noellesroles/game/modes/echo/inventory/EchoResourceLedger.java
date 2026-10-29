package org.agmas.noellesroles.game.modes.echo.inventory;

import java.util.*;

/** 模式资源唯一归属台账；批量移动先验证最终布局，再一次提交。 */
public final class EchoResourceLedger {
    public enum Kind { INVENTORY, CARGO, GROUND, OFFER, PENDING_DELIVERY, CONSUMED, RECYCLED }
    public record Location(Kind kind, UUID owner, int slot, UUID reference) {
        public Location {
            Objects.requireNonNull(kind);
            if (kind == Kind.INVENTORY || kind == Kind.CARGO) {
                if (owner == null || slot < 0 || slot >= (kind == Kind.INVENTORY ? 6 : 4) || reference != null) {
                    throw new IllegalArgumentException("无效玩家资源位置");
                }
            } else if (slot != -1) throw new IllegalArgumentException("非玩家容器不含槽号");
            if ((kind == Kind.GROUND || kind == Kind.OFFER || kind == Kind.PENDING_DELIVERY) && reference == null) {
                throw new IllegalArgumentException("临时容器缺少引用ID");
            }
            if ((kind == Kind.GROUND || kind == Kind.PENDING_DELIVERY) && owner != null
                    || kind == Kind.OFFER && owner == null) {
                throw new IllegalArgumentException("临时容器所有者字段不匹配");
            }
            if ((kind == Kind.CONSUMED || kind == Kind.RECYCLED) && (owner != null || reference != null)) {
                throw new IllegalArgumentException("终态不保留容器所有者");
            }
        }
        public boolean terminal() { return kind == Kind.CONSUMED || kind == Kind.RECYCLED; }
        public static Location inventory(UUID owner, int slot) { return new Location(Kind.INVENTORY, owner, slot, null); }
        public static Location cargo(UUID owner, int slot) { return new Location(Kind.CARGO, owner, slot, null); }
        public static Location ground(UUID dropId) { return new Location(Kind.GROUND, null, -1, dropId); }
        public static Location consumed() { return new Location(Kind.CONSUMED, null, -1, null); }
        public static Location recycled() { return new Location(Kind.RECYCLED, null, -1, null); }
    }
    public record Move(UUID instanceId, Location expected, Location destination) {
        public Move { Objects.requireNonNull(instanceId); Objects.requireNonNull(expected); Objects.requireNonNull(destination); }
    }
    private final UUID matchId;
    private final Map<UUID, Location> locations = new LinkedHashMap<>();

    public EchoResourceLedger(UUID matchId) { this.matchId = Objects.requireNonNull(matchId); }
    public UUID matchId() { return matchId; }
    public Map<UUID, Location> snapshot() { return Map.copyOf(locations); }
    public Optional<Location> find(UUID instanceId) { return Optional.ofNullable(locations.get(instanceId)); }

    public void register(UUID match, UUID instanceId, Location location) {
        requireMatch(match);
        Objects.requireNonNull(instanceId);
        Objects.requireNonNull(location);
        if (location.terminal() || locations.containsKey(instanceId)) throw new IllegalStateException("实例重复或初始位置非法");
        Map<UUID, Location> plan = new LinkedHashMap<>(locations);
        plan.put(instanceId, location);
        validateOccupancy(plan);
        locations.put(instanceId, location);
    }

    public void move(UUID match, UUID instanceId, Location expected, Location destination) {
        batchMove(match, List.of(new Move(instanceId, expected, destination)));
    }

    public void batchMove(UUID match, List<Move> moves) { commit(match,moves,Map.of()); }

    /** 新产物与旧实例终态同批提交，校验失败不留下已登记产物或半份合成。 */
    public void commit(UUID match,List<Move> moves,Map<UUID,Location> created) {
        requireMatch(match);
        List<Move> copy = List.copyOf(moves);
        Set<UUID> touched = new HashSet<>();
        Map<UUID, Location> plan = new LinkedHashMap<>(locations);
        for (Move move : copy) {
            Location current = locations.get(move.instanceId());
            if (!touched.add(move.instanceId()) || current == null || current.terminal() || !current.equals(move.expected())) {
                throw new IllegalStateException("实例位置冲突、重复移动或终态不可复活");
            }
            plan.put(move.instanceId(), move.destination());
        }
        for(var entry:created.entrySet()) {
            if(entry.getKey()==null || entry.getValue()==null || entry.getValue().terminal() || plan.containsKey(entry.getKey()))
                throw new IllegalStateException("新实例重复或初始位置非法");
            plan.put(entry.getKey(),entry.getValue());
        }
        validateOccupancy(plan);
        locations.clear();
        locations.putAll(plan);
    }

    private void requireMatch(UUID match) {
        if (!matchId.equals(match)) throw new IllegalArgumentException("资源不属于此对局");
    }

    private static void validateOccupancy(Map<UUID, Location> plan) {
        Map<Location, Integer> occupancy = new HashMap<>();
        for (Location location : plan.values()) {
            if (location.terminal()) continue;
            int capacity = location.kind() == Kind.INVENTORY ? 2 : 1;
            // 同格两包在库存服务校验品质/种类；台账只保存两个独立实例的容器引用。
            if (occupancy.merge(location, 1, Integer::sum) > capacity) throw new IllegalStateException("目标容器容量冲突");
        }
    }
}
