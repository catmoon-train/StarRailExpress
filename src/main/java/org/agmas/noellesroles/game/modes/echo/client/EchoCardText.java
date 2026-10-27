package org.agmas.noellesroles.game.modes.echo.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition;
import java.math.BigDecimal;
import java.util.*;

/** 机制文案不含默认数值；所有数字来自本局冻结定义。 */
public final class EchoCardText {
    private static final Set<String> FLAGS=Set.of("layers","unlock","dropKey","packs");
    private static final Set<String> PERCENT=Set.of("speedBonus","speedPenalty","cargoPenalty","rollback");
    private EchoCardText() { }
    public static MutableComponent description(EchoCardDefinition definition) {
        var data=EchoBagClient.data();
        if(data==null || !data.rulesReady()) return Component.translatable("echo.ui.waiting");
        var card=data.rules().card(definition.id());
        var text=Component.translatable("echo.card."+card.shortId()+".mechanic").copy();
        new TreeMap<>(card.params()).forEach((key,value) -> {
            if(FLAGS.contains(key)) return;
            boolean time=key.endsWith("Ticks"); double amount=time?value/20:PERCENT.contains(key)?value*100:value;
            var number=BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
            Component formatted=Component.translatable(time?"echo.rules.seconds":PERCENT.contains(key)?"echo.rules.percent":key.toLowerCase(Locale.ROOT).endsWith("range") || key.equals("minSeparation")?"echo.rules.blocks":"echo.rules.number",number);
            text.append("\n").append(Component.translatable("echo.rules.parameter",Component.translatable("echo.param."+key),formatted));
        });
        return text.append("\n").append(Component.translatable("echo.rules.parameter",Component.translatable("echo.param.weight"),card.weight()));
    }
}
