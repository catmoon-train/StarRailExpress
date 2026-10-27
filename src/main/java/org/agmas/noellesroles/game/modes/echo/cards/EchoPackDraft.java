package org.agmas.noellesroles.game.modes.echo.cards;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** 单包服务器事务状态；槽位锁与KEY配额由外层库存事务持有，界面不是权威。 */
public final class EchoPackDraft {
    public enum State { UNOPENED, CHANNELING, BOUND, OFFERED, RESOLVED, VOID }
    public record Resolution(UUID packId, EchoCardDefinition chosen, boolean automatic) { }
    public static final int CHANNEL_TICKS=30, CANCEL_COOLDOWN_TICKS=200, CHOICE_TICKS=400;
    private final UUID packId;
    private final EchoDraftService.PackQuality quality;
    private final org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Draft rules;
    private final List<EchoCardDefinition> definitions;
    private State state=State.UNOPENED;
    private UUID owner;
    private EchoDraftService.Offer offer;
    private long channelEnd, readyAt, choiceEnd, lastObserved;
    private boolean consumed;

    public EchoPackDraft(UUID packId,EchoDraftService.PackQuality quality) {
        this(packId,quality,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT.draft());
    }
    public EchoPackDraft(UUID packId,EchoDraftService.PackQuality quality,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Draft rules) {
        this(packId,quality,rules,List.copyOf(EchoCardRegistry.all()));
    }
    public EchoPackDraft(UUID packId,EchoDraftService.PackQuality quality,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig rules) {
        this(packId,quality,rules.draft(),EchoCardRegistry.all().stream().map(c -> rules.card(c.id())).toList());
    }
    private EchoPackDraft(UUID packId,EchoDraftService.PackQuality quality,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Draft rules,List<EchoCardDefinition> definitions) {
        this.packId=Objects.requireNonNull(packId); this.quality=Objects.requireNonNull(quality); this.rules=Objects.requireNonNull(rules); this.definitions=List.copyOf(definitions);
    }
    public State state() { return state; }
    public boolean consumed() { return consumed; }
    public long readyAt() { return readyAt; }
    public long choiceEnd() { return choiceEnd; }
    public boolean requiresKeyReservation() { return offer!=null && offer.requiresKeyReservation() && state!=State.RESOLVED && state!=State.VOID; }

    /** 必须先由外层校验空格与一人单绑定包；抽取失败保持UNOPENED，不先扣包。 */
    public boolean begin(UUID player,long now,boolean extraSlot,EchoDraftService.Context context,RandomGenerator random) {
        time(now); Objects.requireNonNull(player);
        if(!extraSlot || now<readyAt || state!=State.UNOPENED && state!=State.BOUND || owner!=null && !owner.equals(player)) return false;
        long nextEnd=Math.addExact(now,rules.channelTicks());
        if(offer==null) {
            EchoDraftService.Offer planned=EchoDraftService.draw(quality,context,random,rules,definitions);
            offer=planned; owner=player;
        }
        channelEnd=nextEnd;
        state=State.CHANNELING;
        return true;
    }

    public boolean cancel(UUID player,long now) {
        time(now);
        if(state!=State.CHANNELING || !Objects.equals(owner,player)) return false;
        readyAt=Math.addExact(now,rules.cancelCooldownTicks());
        state=State.BOUND;
        return true;
    }

    /** 服务器推进时钟；暂停时不推进now。跨多个tick调用也不重新延长选择期限。 */
    public Optional<Resolution> advance(long now) {
        time(now);
        if(state==State.CHANNELING && now>=channelEnd) {
            choiceEnd=Math.addExact(channelEnd,rules.choiceTicks());
            state=State.OFFERED;
            consumed=true;
        }
        if(state==State.OFFERED && now>=choiceEnd) return resolve(0,true);
        return Optional.empty();
    }

    public Optional<Resolution> choose(UUID player,int index,long now) {
        time(now);
        if(state!=State.OFFERED || !Objects.equals(owner,player)) return Optional.empty();
        if(index<0 || index>=3) throw new IllegalArgumentException("无效候选下标");
        return resolve(now>=choiceEnd ? 0:index,now>=choiceEnd);
    }

    public List<EchoCardDefinition> visibleCandidates(UUID player) {
        if(!Objects.equals(owner,player) || state!=State.OFFERED) return List.of();
        return offer.candidates();
    }

    public void close(UUID player,long now) { if(state==State.CHANNELING) cancel(player,now); }

    /** 中止/结算作废，不生成选卡事件；调用者释放保留槽与KEY预留。 */
    public void discard() { state=State.VOID; offer=null; }

    private Optional<Resolution> resolve(int index,boolean automatic) {
        var result=new Resolution(packId,offer.candidates().get(index),automatic);
        state=State.RESOLVED;
        return Optional.of(result);
    }
    private void time(long now) {
        if(now<0 || now<lastObserved) throw new IllegalArgumentException("开包时钟倒退");
        lastObserved=now;
    }
}
