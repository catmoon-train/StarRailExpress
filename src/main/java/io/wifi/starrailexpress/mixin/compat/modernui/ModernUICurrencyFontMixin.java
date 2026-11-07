package io.wifi.starrailexpress.mixin.compat.modernui;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 让货币图片字形使用独立字体，避免 ModernUI 替换默认字体后被系统字体的私有区符号接管。
 * 字符收集发生在分段、测宽和渲染之前，因此 String、翻译文本与格式化文本均可保持一致。
 * 入口按 ModernUI Fabric 1.21.1 / 3.13.0.1 的字符 sink 字节码核对；无需链接其内部类。
 */
@Pseudo
@Mixin(targets = "icyllis.modernui.mc.text.TextLayoutProcessor", remap = false)
public abstract class ModernUICurrencyFontMixin {
    @Unique
    private static final ResourceLocation SRE_CURRENCY_FONT =
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "currency_icons");

    @ModifyVariable(method = "lambda$new$1", at = @At("HEAD"),
            argsOnly = true, ordinal = 0, remap = false)
    private Style sre$useCurrencyBitmapFont(Style style, int index, Style originalStyle, int codePoint) {
        // 不处理 E000/E001 等旧码位，避免抢占其他模组的图标；显式指定的其他字体也保持不变。
        if ((codePoint == 0xF6A8 || codePoint == 0xF6A9)
                && Style.DEFAULT_FONT.equals(style.getFont())) {
            return style.withFont(SRE_CURRENCY_FONT);
        }
        return style;
    }
}
