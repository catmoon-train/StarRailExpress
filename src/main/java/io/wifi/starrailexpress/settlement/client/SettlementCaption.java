package io.wifi.starrailexpress.settlement.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;

import java.util.Optional;

/** 只调整结算显示层的暗色正文；原回放组件及其语义颜色不被改写。 */
final class SettlementCaption {
    private SettlementCaption() { }

    static int readableColor(int rgb) {
        int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
        double light=.2126*r+.7152*g+.0722*b;
        if (light>=145) return rgb;
        if (Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<28) return 0xF3EDDD;
        double mix=(155-light)/(255-light);
        return ((int)(r+(255-r)*mix)<<16)|((int)(g+(255-g)*mix)<<8)|(int)(b+(255-b)*mix);
    }

    static Component readable(Component input) {
        MutableComponent output=Component.empty();
        StringBuilder run=new StringBuilder();
        Style[] previous={null};
        input.visit((style,value)-> {
            StringDecomposer.iterateFormatted(value,0,style,(index,glyphStyle,codepoint)-> {
                Style adjusted=glyphStyle.withColor(glyphStyle.getColor()==null?0xF3EDDD:readableColor(glyphStyle.getColor().getValue()));
                if (!adjusted.equals(previous[0])) {
                    if (!run.isEmpty()) { output.append(Component.literal(run.toString()).setStyle(previous[0]));run.setLength(0); }
                    previous[0]=adjusted;
                }
                run.appendCodePoint(codepoint);return true;
            });
            return Optional.empty();
        },Style.EMPTY);
        if (!run.isEmpty()) output.append(Component.literal(run.toString()).setStyle(previous[0]));
        return output;
    }
}
