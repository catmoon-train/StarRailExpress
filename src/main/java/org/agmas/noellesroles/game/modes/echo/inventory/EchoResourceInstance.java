package org.agmas.noellesroles.game.modes.echo.inventory;

import org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition;
import org.agmas.noellesroles.game.modes.echo.cards.EchoCardRegistry;
import org.agmas.noellesroles.game.modes.echo.cards.EchoDraftService.PackQuality;
import java.util.Objects;
import java.util.UUID;

/** 不可变资源身份；包候选与交易来源由服务器服务保管，不随展示栈复制。 */
public record EchoResourceInstance(UUID matchId,UUID instanceId,String definitionId,Kind kind,
                                   EchoCardDefinition.Tier tier,UUID originEventId,PackQuality quality) {
    public enum Kind { PACK, CARD, KEY }
    public EchoResourceInstance {
        Objects.requireNonNull(matchId); Objects.requireNonNull(instanceId); Objects.requireNonNull(definitionId);
        Objects.requireNonNull(kind); Objects.requireNonNull(originEventId);
        if(kind==Kind.PACK) {
            if(quality==null || tier!=null || !definitionId.equals("pack/"+quality.name())) throw new IllegalArgumentException("包元数据不匹配");
        } else {
            var card=EchoCardRegistry.require(definitionId);
            if(quality!=null || tier!=card.tier() || (kind==Kind.KEY)!=card.isKey()) throw new IllegalArgumentException("卡元数据不匹配");
        }
    }
    public static EchoResourceInstance pack(UUID match,UUID id,PackQuality quality,UUID origin) {
        return new EchoResourceInstance(match,id,"pack/"+quality.name(),Kind.PACK,null,origin,quality);
    }
    public static EchoResourceInstance card(UUID match,UUID id,EchoCardDefinition definition,UUID origin) {
        return new EchoResourceInstance(match,id,definition.id(),definition.isKey()?Kind.KEY:Kind.CARD,definition.tier(),origin,null);
    }
    public boolean key() { return kind==Kind.KEY; }
    public boolean stacks(EchoResourceInstance other) { return kind==Kind.PACK && other.kind==Kind.PACK && quality==other.quality; }
}
