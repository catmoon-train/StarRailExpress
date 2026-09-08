package io.wifi.mixins;

import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 专用服务器替换服务端 {@link Language} 实例用。
 * 直接写私有静态字段 {@code instance}，不依赖可能被服务端 jar 裁剪的 inject 方法。
 */
@Mixin(Language.class)
public interface LanguageInstanceAccessor {

    @Accessor("instance")
    static void sre_setLanguage(Language language) {
        throw new AssertionError("LanguageInstanceAccessor not applied");
    }
}
