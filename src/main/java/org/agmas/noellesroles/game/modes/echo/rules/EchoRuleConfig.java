package org.agmas.noellesroles.game.modes.echo.rules;

import java.util.Objects;

/** 不可变规则快照；内部时间只使用tick，文件层负责秒的精确转换。 */
public record EchoRuleConfig(Logistics logistics,Beacon beacon,Gate gate,Combat combat,Draft draft,Signals signals,Trade trade,EchoCardTuning cards,Specialization specialization) {
    public record Logistics(int capacity,double cargoPenalty,int collectTicks,int nodeRecoveryTicks,int submitTicks,
                            int altarTicks,int keySupplyTicks,int crateSearchTicks,int blackMarketSearchTicks,int crateRecoveryTicks,
                            int crystalGroundTicks,int keyGroundTicks,int resourceGroundTicks,int blackCollectTicks,
                            int blackDeliverTicks,int blackRecoveryTicks,int blackGroundTicks,double bonusPackChance) { }
    public record Beacon(int requiredCrystals,int chargeTicks,double escortBonus,int maxEscorts,double escortRange,
                         int stallTicks,int stallIntervalTicks) { }
    public record Gate(int coreOpenTicks,int wedgeExtraTicks,int wedgeMaxTicks,int masterOpenTicks,int evacuationDwellTicks,
                       int overloadTicks,double overloadRange) { }
    public record Combat(int cutterChargeTicks,int cutterCooldownTicks,double cutterRange,int shoveCooldownTicks,
                         int shoveControlTicks,int controlImmunityTicks,int bleedTicks,int rescueTicks,int rescueProtectionTicks,
                         int environmentControlTicks,int environmentProtectionTicks,double rescueRange,double shoveRange) { }
    public record Distribution(double common,double rare,double epic) {
        public Distribution {
            probability(common); probability(rare); probability(epic);
            if(Math.abs(common+rare+epic-1)>1e-9) throw new IllegalArgumentException("品质概率之和必须为1");
        }
    }
    public record Draft(int channelTicks,int cancelCooldownTicks,int choiceTicks,double openingSpeed,
                        Distribution common,Distribution rareFirst,Distribution rareOther,Distribution blackFirst,Distribution blackOther) {
        public Draft {
            Objects.requireNonNull(common); Objects.requireNonNull(rareFirst); Objects.requireNonNull(rareOther); Objects.requireNonNull(blackFirst); Objects.requireNonNull(blackOther);
            if(common.epic()!=0 || rareFirst.common()!=0 || rareFirst.epic()!=0 || rareOther.epic()!=0 || blackFirst.common()!=0)
                throw new IllegalArgumentException("概率覆盖不得破坏包品质池和首位保底");
        }
    }
    public record Signals(int cargoIntervalTicks,int keyIntervalTicks,double directionRange,double packSoundRange) { }
    public record Trade(double range,int offerTicks,int proposalCooldownTicks) { }
    public record Specialization(Porter porter,Scout scout,Hunter hunter,Saboteur saboteur,Smuggler smuggler,Fixer fixer) {
        public record Porter(int cargoCapacityBonus,double cargoPenaltyDelta) { }
        public record Scout(int cargoCapacityBonus,double collectSpeed) { }
        public record Hunter(double cutterChargeSpeed,double cutterCooldownSpeed) { }
        public record Saboteur(double stallSpeed,double cutterCooldownSpeed) { }
        public record Smuggler(double blackCollectSpeed) { }
        public record Fixer(int tradeRangeBonus,double proposalCooldownSpeed) { }
        public Specialization {
            Objects.requireNonNull(porter); Objects.requireNonNull(scout); Objects.requireNonNull(hunter);
            Objects.requireNonNull(saboteur); Objects.requireNonNull(smuggler); Objects.requireNonNull(fixer);
            capacityBonus(porter.cargoCapacityBonus()); capacityBonus(scout.cargoCapacityBonus());
            delta(porter.cargoPenaltyDelta());
            speed(scout.collectSpeed()); speed(hunter.cutterChargeSpeed()); speed(hunter.cutterCooldownSpeed());
            speed(saboteur.stallSpeed()); speed(saboteur.cutterCooldownSpeed());
            speed(smuggler.blackCollectSpeed()); speed(fixer.proposalCooldownSpeed());
            if(fixer.tradeRangeBonus()<0 || fixer.tradeRangeBonus()>4) throw new IllegalArgumentException("fixer.tradeRangeBonus超出0..4");
        }
        private static void capacityBonus(int value) { if(value<-1 || value>1) throw new IllegalArgumentException("cargoCapacityBonus超出-1..1"); }
        private static void delta(double value) { if(!Double.isFinite(value) || value<-0.05 || value>0.05) throw new IllegalArgumentException("cargoPenaltyDelta超出-0.05..0.05"); }
        private static void speed(double value) { if(!Double.isFinite(value) || value<0.5 || value>1.5) throw new IllegalArgumentException("专长倍率超出0.5..1.5"); }
        public static Specialization defaults() { return new Specialization(new Porter(1,-0.03),new Scout(-1,0.8),new Hunter(0.85,0.9),new Saboteur(0.75,1.2),new Smuggler(0.8),new Fixer(2,0.6)); }
    }
    public EchoRuleConfig(Logistics logistics,Beacon beacon,Gate gate,Combat combat,Draft draft,Signals signals) {
        this(logistics,beacon,gate,combat,draft,signals,new Trade(3,400,300),EchoCardTuning.EMPTY,Specialization.defaults());
    }
    public org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition card(String id) {
        return cards.apply(org.agmas.noellesroles.game.modes.echo.cards.EchoCardRegistry.require(id),this);
    }
    public static final EchoRuleConfig DEFAULT=new EchoRuleConfig(
            new Logistics(2,0.10,80,400,20,20,1200,40,60,1800,1200,900,2400,80,40,1200,600,0.10),
            new Beacon(3,1000,0.20,3,6,400,900),
            new Gate(300,100,500,400,40,600,12),
            new Combat(12,50,2.75,500,25,160,1350,100,120,60,200,2.5,2.5),
            new Draft(30,200,400,0.5,new Distribution(0.85,0.15,0),new Distribution(0,1,0),new Distribution(0.7,0.3,0),new Distribution(0,0.7,0.3),new Distribution(0.5,0.4,0.1)),
            new Signals(400,300,48,16),new Trade(3,400,300),EchoCardTuning.EMPTY,Specialization.defaults());
    public EchoRuleConfig {
        Objects.requireNonNull(logistics); Objects.requireNonNull(beacon); Objects.requireNonNull(gate); Objects.requireNonNull(combat); Objects.requireNonNull(draft); Objects.requireNonNull(signals);
        Objects.requireNonNull(specialization);
        bounded(trade.range(),0.5,128,"trade.range");
        Objects.requireNonNull(trade); Objects.requireNonNull(cards); checkNumbers(trade);
        org.agmas.noellesroles.game.modes.echo.cards.EchoCardRegistry.all().forEach(c -> cards.apply(c,gate,beacon));
        bounded(logistics.capacity(),1,4,"logistics.capacity"); probability(logistics.cargoPenalty()); probability(logistics.bonusPackChance());
        bounded(beacon.requiredCrystals(),1,12,"beacon.requiredCrystals"); bounded(beacon.maxEscorts(),0,8,"beacon.maxEscorts");
        probability(beacon.escortBonus());
        bounded(beacon.stallTicks(),1,72000,"beacon.stallTicks"); bounded(beacon.stallIntervalTicks(),1,72000,"beacon.stallIntervalTicks");
        if(combat.rescueTicks()>=combat.bleedTicks()) throw new IllegalArgumentException("救援时长必须短于流血时长");
        if(combat.environmentProtectionTicks()<combat.environmentControlTicks()) throw new IllegalArgumentException("环境保护不得短于环境控制");
        if(gate.wedgeMaxTicks()<Math.max(gate.coreOpenTicks(),gate.masterOpenTicks())) throw new IllegalArgumentException("门撑上限不得短于基础/高级开门");
        // 固定人数档最短撤离窗为60秒，配置不改变01的主胜负/人数档。
        if(gate.overloadTicks()>=1200) throw new IllegalArgumentException("过载必须短于最短撤离窗60秒");
        if(draft.openingSpeed()<0.5 || draft.openingSpeed()>1) throw new IllegalArgumentException("开包速度倍率必须在0.5..1内");
        checkNumbers(logistics); checkNumbers(beacon); checkNumbers(gate); checkNumbers(combat); checkNumbers(draft); checkNumbers(signals);
    }
    private static void checkNumbers(Record record) {
        try {
            for(var component:record.getClass().getRecordComponents()) {
                Object value=component.getAccessor().invoke(record);
                if(value instanceof Number number) {
                    double n=number.doubleValue();
                    if(!Double.isFinite(n) || n<0) throw new IllegalArgumentException(component.getName()+"必须为非负有限数");
                    if(component.getName().endsWith("Ticks")) bounded(n,1,72000,component.getName());
                    if(component.getName().endsWith("Range")) bounded(n,0.5,128,component.getName());
                }
            }
        } catch(ReflectiveOperationException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void bounded(double value,double min,double max,String name) { if(value<min || value>max) throw new IllegalArgumentException(name+"超出"+min+".."+max); }
    private static void probability(double value) { if(!Double.isFinite(value) || value<0 || value>1) throw new IllegalArgumentException("概率/倍率必须在0..1内"); }
}
