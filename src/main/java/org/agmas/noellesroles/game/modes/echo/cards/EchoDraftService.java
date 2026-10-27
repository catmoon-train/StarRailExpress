package org.agmas.noellesroles.game.modes.echo.cards;

import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;

import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import static org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition.Rarity.*;

/** 品质桶内按权重无放回抽三选一；只返回计划，配额由外层事务统一预留。 */
public final class EchoDraftService {
    public enum PackQuality { COMMON, RARE, BLACK_MARKET }
    public record Context(EchoFaction faction, Set<String> enabled, boolean keySlotAvailable, boolean globalKeyAvailable) {
        public Context { Objects.requireNonNull(faction); enabled=Set.copyOf(enabled); }
        public static Context all(EchoFaction faction, boolean keySlotAvailable, boolean globalKeyAvailable) {
            return new Context(faction,EchoCardRegistry.all().stream().map(EchoCardDefinition::id).collect(Collectors.toSet()),keySlotAvailable,globalKeyAvailable);
        }
    }
    public record Offer(List<EchoCardDefinition> candidates, boolean requiresKeyReservation) {
        public Offer {
            candidates=List.copyOf(candidates);
            if(candidates.size()!=3 || candidates.stream().map(EchoCardDefinition::id).distinct().count()!=3) throw new IllegalArgumentException("候选必须为三张不同定义");
            if(requiresKeyReservation!=candidates.stream().anyMatch(EchoCardDefinition::isKey)) throw new IllegalArgumentException("KEY配额计划不一致");
        }
    }
    private EchoDraftService() { }

    public static Offer draw(PackQuality quality, Context context, RandomGenerator random) {
        return draw(quality,context,random,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT.draft());
    }
    public static Offer draw(PackQuality quality,Context context,RandomGenerator random,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Draft rules) {
        return draw(quality,context,random,rules,EchoCardRegistry.all());
    }
    public static Offer draw(PackQuality quality,Context context,RandomGenerator random,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Draft rules,Collection<EchoCardDefinition> definitions) {
        Objects.requireNonNull(quality); Objects.requireNonNull(context); Objects.requireNonNull(random);
        List<EchoCardDefinition> remaining=definitions.stream()
                .filter(c -> c.drawFaction()==context.faction() && context.enabled().contains(c.id()))
                .filter(c -> !c.isKey() || context.keySlotAvailable() && context.globalKeyAvailable())
                .collect(Collectors.toCollection(ArrayList::new));
        List<EchoCardDefinition> selected=new ArrayList<>();
        for(int slot=0;slot<3;slot++) {
            var rarity=rarity(quality,slot,random.nextDouble(),rules);
            List<EchoCardDefinition> bucket=bucket(remaining,rarity);
            while(bucket.isEmpty() && rarity!=COMMON) {
                rarity=rarity==EPIC ? RARE : COMMON;
                bucket=bucket(remaining,rarity);
            }
            if(bucket.isEmpty() || slot==0 && quality!=PackQuality.COMMON && rarity==COMMON) throw new IllegalStateException("卡池不足或保底不可满足");
            int total=bucket.stream().mapToInt(EchoCardDefinition::weight).sum();
            int roll=random.nextInt(total);
            EchoCardDefinition pick=bucket.getLast();
            for(var card:bucket) {
                roll-=card.weight();
                if(roll<0) { pick=card; break; }
            }
            selected.add(pick);
            remaining.remove(pick);
        }
        return new Offer(selected,selected.stream().anyMatch(EchoCardDefinition::isKey));
    }
    private static List<EchoCardDefinition> bucket(List<EchoCardDefinition> cards,EchoCardDefinition.Rarity rarity) {
        return cards.stream().filter(c -> c.rarity()==rarity).toList();
    }
    private static EchoCardDefinition.Rarity rarity(PackQuality quality,int slot,double roll,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Draft rules) {
        var distribution=switch(quality) {
            case COMMON -> rules.common();
            case RARE -> slot==0?rules.rareFirst():rules.rareOther();
            case BLACK_MARKET -> slot==0?rules.blackFirst():rules.blackOther();
        };
        return roll<distribution.common()?COMMON:roll<distribution.common()+distribution.rare()?RARE:EPIC;
    }
}
