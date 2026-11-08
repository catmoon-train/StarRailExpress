package io.wifi.starrailexpress.settlement.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** 字幕显示层回归：暗色正文提亮，亮色语义和原组件保留。 */
class SettlementCaptionTest {
    @Test void darkGreyIsReadableAndBrightSemanticColorsStayIntact() {
        assertEquals(0xF3EDDD,SettlementCaption.readableColor(0x555555));
        assertEquals(0x55FFFF,SettlementCaption.readableColor(0x55FFFF));
        int warm=SettlementCaption.readableColor(0x990022);
        assertTrue((warm>>16&255)>(warm>>8&255));
    }

    @Test void textAndOriginalStylesAreNotMutated() {
        var original=Component.literal("购买了 ").withStyle(s->s.withColor(0x555555))
                .append(Component.literal("物品").withStyle(s->s.withColor(0x55FFFF)));
        var shown=SettlementCaption.readable(original);
        assertEquals("购买了 物品",shown.getString());
        assertEquals(0x555555,original.getStyle().getColor().getValue());
        shown.visit((style,text)-> {
            assertNotNull(style.getColor());assertNotEquals(0x555555,style.getColor().getValue());
            return Optional.empty();
        },Style.EMPTY);
    }

    @Test void legacyDarkCodesAreNormalizedWithoutLosingCharacters() {
        assertEquals("购买了装备",SettlementCaption.readable(Component.literal("§8购买了§f装备")).getString());
    }
}
