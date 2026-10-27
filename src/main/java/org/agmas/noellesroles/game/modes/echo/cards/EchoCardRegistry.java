package org.agmas.noellesroles.game.modes.echo.cards;

import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;

import java.util.*;

import static org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition.*;
import static org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition.Activation.*;
import static org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition.Series.*;
import static org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition.Target.*;
import static org.agmas.noellesroles.game.modes.echo.state.EchoFaction.*;

/** 首发37定义及16个唯一产物，独立于Minecraft注册表，便于纯单元测试。 */
public final class EchoCardRegistry {
    private static final Map<String,EchoCardDefinition> DEFINITIONS;
    private static final Map<String,EchoCardDefinition> RECIPES;
    static {
        List<EchoCardDefinition> cards = new ArrayList<>();
        cards.add(base("silent_collector",CARRIER,SILENCE,Rarity.COMMON,12,ACTIVE,SELF,p("durationTicks",1200,"charges",1)));
        cards.add(base("decoy_dummy",CARRIER,DECOY,Rarity.COMMON,10,ACTIVE,GROUND,p("range",6,"durationTicks",300,"soundIntervalTicks",60,"soundRange",24)));
        cards.add(base("short_dash",CARRIER,MOBILITY,Rarity.COMMON,12,ACTIVE,SELF,p("durationTicks",60,"speedBonus",0.25)));
        cards.add(base("cargo_lining",CARRIER,LOGISTICS,Rarity.RARE,7,PASSIVE,SELF,p("cargoCapacity",3,"cargoPenalty",0.05)));
        cards.add(base("steady_flow",CARRIER,COUNTER,Rarity.COMMON,10,ACTIVE,BEACON,p("range",3,"clearStall",1)));
        cards.add(base("beacon_boost",CARRIER,COUNTER,Rarity.RARE,7,ACTIVE,BEACON,p("range",3,"boostPermille",100)));
        cards.add(base("team_radar",CARRIER,INTEL,Rarity.RARE,6,ACTIVE,SELF,p("range",20,"durationTicks",60)));
        cards.add(base("sound_key",CARRIER,ACCESS,Rarity.RARE,5,KEY,CORE_GATE,p("range",3,"openTicks",300)));
        cards.add(base("door_wedge",CARRIER,ACCESS,Rarity.COMMON,8,ACTIVE,CORE_GATE,p("range",3,"extendTicks",100,"maxOpenTicks",500)));
        cards.add(base("crystal_sniff",INTERCEPTOR,TRACKING,Rarity.COMMON,12,ACTIVE,DIRECTION,p("durationTicks",100,"intervalTicks",20,"maxTargets",1)));
        cards.add(base("stall_pulse",INTERCEPTOR,SABOTAGE,Rarity.COMMON,10,ACTIVE,BEACON,p("range",8,"stallTicks",400)));
        cards.add(base("cargo_raid",INTERCEPTOR,SABOTAGE,Rarity.RARE,7,ACTIVE,PLAYER,p("range",5,"cargo",1)));
        cards.add(base("false_echo",INTERCEPTOR,DECOY,Rarity.COMMON,10,ACTIVE,GROUND,p("range",12,"highlightTicks",60,"cooldownTicks",300)));
        cards.add(base("hunt_mark",INTERCEPTOR,HUNT,Rarity.RARE,6,ACTIVE,PLAYER,p("range",12,"durationTicks",240,"intervalTicks",80,"outlineTicks",20,"revealSelfTicks",20)));
        cards.add(base("key_disruptor",INTERCEPTOR,SABOTAGE,Rarity.RARE,6,ACTIVE,PLAYER,p("range",10,"dropKey",1)));
        cards.add(base("trade_offer",BROKER,TRADE,Rarity.COMMON,12,ACTIVE,SELF,p("durationTicks",1200,"range",10,"offerTicks",1200,"charges",1)));
        cards.add(base("faction_mask",BROKER,DISGUISE,Rarity.RARE,6,ACTIVE,SELF,p("durationTicks",600)));
        cards.add(base("afterimage",BROKER,SELF_PRESERVE,Rarity.COMMON,12,ACTIVE,SELF,p("imageTicks",80,"speedTicks",60,"speedBonus",0.20)));
        cards.add(base("black_sniff",BROKER,SALVAGE,Rarity.COMMON,10,ACTIVE,DIRECTION,p("range",48,"durationTicks",160)));
        cards.add(base("forged_signal",BROKER,FORGERY,Rarity.COMMON,10,ACTIVE,GROUND,p("range",12,"durationTicks",60,"cooldownTicks",300)));
        cards.add(base("pack_hook",BROKER,SALVAGE,Rarity.RARE,6,ACTIVE,PLAYER,p("range",5,"targetCooldownTicks",600,"packs",1)));
        cards.add(up("silent_rig",CARRIER,SILENCE,ACTIVE,SELF,p("durationTicks",1200,"charges",2)));
        cards.add(up("silent_dummy",CARRIER,DECOY,ACTIVE,GROUND,p("range",6,"durationTicks",400,"deceptionRange",20,"deceptionTicks",100)));
        cards.add(up("long_dash",CARRIER,MOBILITY,ACTIVE,SELF,p("durationTicks",100,"speedBonus",0.35)));
        cards.add(up("cargo_harness",CARRIER,LOGISTICS,PASSIVE,SELF,p("cargoCapacity",4,"cargoPenalty",0)));
        cards.add(up("overdrive_beacon",CARRIER,COUNTER,ACTIVE,BEACON,p("range",3,"clearStall",1,"boostPermille",150)));
        cards.add(up("team_scan",CARRIER,INTEL,ACTIVE,SELF,p("range",28,"durationTicks",100)));
        cards.add(up("master_key",CARRIER,ACCESS,KEY,CORE_GATE,p("range",3,"openTicks",400,"scanRange",12,"outlineTicks",40)));
        cards.add(up("global_pulse",INTERCEPTOR,TRACKING,ACTIVE,DIRECTION,p("durationTicks",160,"intervalTicks",20,"maxTargets",3)));
        cards.add(up("stall_surge",INTERCEPTOR,SABOTAGE,ACTIVE,BEACON,p("range",8,"stallTicks",900)));
        cards.add(up("false_dispatch",INTERCEPTOR,DECOY,ACTIVE,GROUND_PAIR,p("range",12,"minSeparation",6,"delayTicks",40,"highlightTicks",60,"cooldownTicks",300)));
        cards.add(up("hunt_pressure",INTERCEPTOR,HUNT,ACTIVE,PLAYER,p("range",12,"slowTicks",160,"speedPenalty",0.10,"outlineTicks",80,"revealSelfTicks",40)));
        cards.add(up("trade_pact",BROKER,TRADE,ACTIVE,SELF,p("durationTicks",1200,"range",16,"offerTicks",1200,"charges",2)));
        cards.add(up("double_mask",BROKER,DISGUISE,ACTIVE,SELF,p("durationTicks",900,"suppressedKeyPulses",1)));
        cards.add(up("residual_veil",BROKER,SELF_PRESERVE,ACTIVE,SELF,p("imageTicks",120,"speedTicks",80,"speedBonus",0.25)));
        cards.add(up("salvage_magnet",BROKER,SALVAGE,ACTIVE,DIRECTION,p("range",48,"durationTicks",400,"pickupRange",4,"packs",1)));
        cards.add(up("forged_dispatch",BROKER,FORGERY,ACTIVE,GROUND,p("range",12,"durationTicks",100,"cooldownTicks",300)));
        Map<String,EchoCardDefinition> definitions = new LinkedHashMap<>();
        Map<String,EchoCardDefinition> recipes = new LinkedHashMap<>();
        Set<String> baseGroups = new HashSet<>();
        for (var card : cards) {
            if (definitions.putIfAbsent(card.id(),card)!=null) throw new IllegalStateException("重复卡牌ID");
            if(card.tier()==Tier.BASE) baseGroups.add(card.recipeGroup());
            else if(recipes.putIfAbsent(card.recipeGroup(),card)!=null) throw new IllegalStateException("重复高级产物");
        }
        if(definitions.size()!=37 || recipes.size()!=16 || !recipes.keySet().equals(baseGroups)) throw new IllegalStateException("首发卡池/配方不闭合");
        DEFINITIONS = Collections.unmodifiableMap(definitions);
        RECIPES = Collections.unmodifiableMap(recipes);
    }
    private EchoCardRegistry() { }
    public static Collection<EchoCardDefinition> all() { return DEFINITIONS.values(); }
    public static Map<String,EchoCardDefinition> recipes() { return RECIPES; }
    public static Optional<EchoCardDefinition> find(String id) { return Optional.ofNullable(DEFINITIONS.get(id)); }
    public static EchoCardDefinition require(String id) { return find(id).orElseThrow(() -> new IllegalArgumentException("未知卡牌ID: "+id)); }

    public static Optional<EchoCardDefinition> craftOutput(UUID firstId, String firstDefinition, UUID secondId,
                                                         String secondDefinition, EchoFaction owner) {
        Objects.requireNonNull(firstId); Objects.requireNonNull(secondId); Objects.requireNonNull(owner);
        if(firstId.equals(secondId)) return Optional.empty();
        var first=find(firstDefinition); var second=find(secondDefinition);
        if(first.isEmpty() || second.isEmpty()) return Optional.empty();
        var a=first.get(); var b=second.get();
        if(a.tier()!=Tier.BASE || b.tier()!=Tier.BASE || !a.usableBy(owner) || !b.usableBy(owner)
                || !a.recipeGroup().equals(b.recipeGroup())) return Optional.empty();
        return Optional.ofNullable(RECIPES.get(a.recipeGroup()));
    }

    private static EchoCardDefinition base(String id,EchoFaction faction,Series series,Rarity rarity,int weight,Activation activation,Target target,Map<String,Double> params) {
        return define(id,faction,series,Tier.BASE,rarity,weight,activation,target,params);
    }
    private static EchoCardDefinition up(String id,EchoFaction faction,Series series,Activation activation,Target target,Map<String,Double> params) {
        return define(id,faction,series,Tier.UPGRADED,Rarity.EPIC,3,activation,target,params);
    }
    private static EchoCardDefinition define(String id,EchoFaction faction,Series series,Tier tier,Rarity rarity,int weight,Activation activation,Target target,Map<String,Double> params) {
        return new EchoCardDefinition("noellesroles:echo/"+id,faction,series==ACCESS ? EnumSet.allOf(EchoFaction.class) : Set.of(faction),
                series,tier,rarity,weight,activation,target,"echo/"+id,params);
    }
    private static Map<String,Double> p(Object... entries) {
        Map<String,Double> params=new LinkedHashMap<>();
        for(int i=0;i<entries.length;i+=2) params.put((String)entries[i],((Number)entries[i+1]).doubleValue());
        return params;
    }
}
