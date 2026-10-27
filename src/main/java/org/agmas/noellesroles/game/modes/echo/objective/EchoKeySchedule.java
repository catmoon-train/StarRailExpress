package org.agmas.noellesroles.game.modes.echo.objective;

import java.util.*;
import java.util.random.RandomGenerator;

/** 三祭坛地面占位与补给节奏；持有者钥匙不阻止下一周期补给。 */
public final class EchoKeySchedule {
    private final List<String> altars;
    private final Map<String,UUID> groundKeys=new HashMap<>();
    private long nextSupply;
    private int cursor;
    private final int supplyTicks;
    public EchoKeySchedule(List<String> altars,long startedAt) {
        this(altars,startedAt,1200);
    }
    public EchoKeySchedule(List<String> altars,long startedAt,int supplyTicks) {
        if(supplyTicks<=0) throw new IllegalArgumentException("补给周期必须大于0");
        this.supplyTicks=supplyTicks; this.altars=List.copyOf(altars);
        if(this.altars.size()!=3 || new HashSet<>(altars).size()!=3) throw new IllegalArgumentException("需要三个不同祭坛");
        nextSupply=startedAt+supplyTicks;
    }
    public List<String> initial(RandomGenerator random) {
        List<String> shuffled=new ArrayList<>(altars);
        for(int index=2;index>0;index--) Collections.swap(shuffled,index,random.nextInt(index+1));
        return List.copyOf(shuffled.subList(0,2));
    }
    public Optional<String> due(long now) {
        if(now<nextSupply) return Optional.empty();
        nextSupply=now+supplyTicks;
        for(int n=0;n<3;n++) {
            String altar=altars.get(cursor++%3);
            if(!groundKeys.containsKey(altar)) return Optional.of(altar);
        }
        return Optional.empty();
    }
    public Optional<String> emptyAltar() { return altars.stream().filter(a -> !groundKeys.containsKey(a)).findFirst(); }
    public boolean occupy(String altar,UUID instance) {
        if(!altars.contains(altar)) throw new IllegalArgumentException("未知祭坛");
        return groundKeys.putIfAbsent(altar,Objects.requireNonNull(instance))==null;
    }
    public void release(String altar,UUID expected) { if(altar!=null) groundKeys.remove(altar,expected); }
    public Optional<UUID> at(String altar) { return Optional.ofNullable(groundKeys.get(altar)); }
}
