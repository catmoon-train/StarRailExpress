package org.agmas.noellesroles.game.modes.echo.rules;

import org.agmas.noellesroles.game.modes.echo.cards.*;
import java.util.*;

/** 每卡数值白名单；保存内部tick差异，不允许配置改变效果类型或资源语义。 */
public record EchoCardTuning(Map<String,Map<String,Double>> overrides) {
    public static final EchoCardTuning EMPTY=new EchoCardTuning(Map.of());
    private static final Set<String> FIXED=Set.of("dropKey","packs","cargo");
    public EchoCardTuning {
        var normalized=new TreeMap<String,Map<String,Double>>();
        for(var entry:overrides.entrySet()) {
            var card=EchoCardRegistry.require("noellesroles:echo/"+entry.getKey());
            var values=new TreeMap<String,Double>();
            for(var field:entry.getValue().entrySet()) {
                String key=field.getKey(); double value=Objects.requireNonNull(field.getValue());
                if(!allowed(card,key)) throw new IllegalArgumentException("不支持的卡牌字段: "+entry.getKey()+"."+key);
                validate(key,value);
                double baseline=key.equals("weight")?card.weight():card.params().get(key);
                if(Double.compare(baseline,value)!=0) values.put(key,value);
            }
            if(!values.isEmpty()) normalized.put(entry.getKey(),Collections.unmodifiableMap(values));
        }
        overrides=Collections.unmodifiableMap(normalized);
    }
    public static boolean alias(EchoCardDefinition card,String key) {
        return switch(card.shortId()) {
            case "sound_key","master_key" -> key.equals("openTicks");
            case "door_wedge" -> key.equals("extendTicks") || key.equals("maxOpenTicks");
            // 迟滞时长由 beacon 域统一配置，逐卡只允许调范围。
            case "stall_pulse","stall_surge" -> key.equals("stallTicks");
            case "beacon_boost","overdrive_beacon" -> key.equals("boostPermille");
            default -> false;
        };
    }
    public static boolean allowed(EchoCardDefinition card,String key) {
        return key.equals("weight") || card.params().containsKey(key) && !FIXED.contains(key) && !alias(card,key);
    }
    private static void validate(String key,double value) {
        if(!Double.isFinite(value)) throw new IllegalArgumentException("卡牌数值必须有限");
        double min,max; boolean integer=false;
        if(key.endsWith("Ticks")) { min=1; max=72000; integer=true; }
        else if(key.equals("weight")) { min=1; max=10000; integer=true; }
        else if(key.equals("cargoCapacity")) { min=1; max=4; integer=true; }
        else if(key.equals("maxTargets")) { min=1; max=3; integer=true; }
        else if(key.equals("boostPermille")) { min=1; max=1000; integer=true; }
        else if(key.equals("charges") || key.equals("suppressedKeyPulses")) { min=1; max=8; integer=true; }
        else if(key.equals("clearStall")) { min=1; max=1; integer=true; }
        else if(key.equals("speedBonus")) { min=0.01; max=0.5; }
        else if(key.equals("speedPenalty") || key.equals("cargoPenalty")) { min=0; max=1; }
        else if(key.toLowerCase(Locale.ROOT).endsWith("range") || key.equals("minSeparation")) { min=0.5; max=128; }
        else throw new IllegalArgumentException("未定义边界的卡牌字段: "+key);
        if(value<min || value>max || integer && value!=Math.rint(value)) throw new IllegalArgumentException(key+"超出边界或不是整数");
    }
    public EchoCardDefinition apply(EchoCardDefinition base,EchoRuleConfig rules) { return apply(base,rules.gate(),rules.beacon()); }
    public EchoCardDefinition apply(EchoCardDefinition base,EchoRuleConfig.Gate gate,EchoRuleConfig.Beacon beacon) {
        var params=new TreeMap<>(base.params());
        switch(base.shortId()) {
            case "sound_key" -> params.put("openTicks",(double)gate.coreOpenTicks());
            case "master_key" -> params.put("openTicks",(double)gate.masterOpenTicks());
            case "door_wedge" -> { params.put("extendTicks",(double)gate.wedgeExtraTicks()); params.put("maxOpenTicks",(double)gate.wedgeMaxTicks()); }
            case "stall_pulse" -> params.put("stallTicks",(double)beacon.stallTicks());
            default -> { }
        }
        var changes=overrides.getOrDefault(base.shortId(),Map.of());
        changes.forEach((key,value) -> { if(!key.equals("weight")) params.put(key,value); });
        if(base.shortId().equals("stall_pulse") && params.get("stallTicks")>=beacon.stallIntervalTicks()) throw new IllegalArgumentException("单次迟滞必须短于共享间隔");
        if(base.shortId().equals("stall_surge") && params.get("stallTicks")>beacon.stallIntervalTicks()) throw new IllegalArgumentException("强迟滞不得超过共享间隔");
        if(base.shortId().equals("hunt_mark") && params.get("outlineTicks")>params.get("intervalTicks")) throw new IllegalArgumentException("猎杀轮廓不得长于刷新间隔");
        if(base.shortId().equals("silent_dummy") && params.get("deceptionTicks")>params.get("durationTicks")) throw new IllegalArgumentException("假人诱导不得长于假人持续");
        if(base.shortId().equals("false_dispatch") && params.get("minSeparation")>2*params.get("range")) throw new IllegalArgumentException("双点间距超过可选范围");
        return new EchoCardDefinition(base.id(),base.drawFaction(),base.allowedFactions(),base.series(),base.tier(),base.rarity(),
                changes.getOrDefault("weight",(double)base.weight()).intValue(),base.activation(),base.target(),base.effectHandlerId(),params);
    }
}
