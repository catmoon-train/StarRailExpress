package io.wifi.srepackcompat.config;

import java.util.function.Predicate;

/** 保留已安装的原模组，由它独占对应功能，防止重复限帧、校验注入和显存删除。 */
public final class RuntimeFeatureSelection {
    private RuntimeFeatureSelection() { }

    public static boolean shouldApply(String mixinClassName, Predicate<String> modLoaded) {
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        String external = switch (name) {
            case "FramePacerMixin" -> "framepacer";
            case "TextureUrlMixin", "TexturePropertyMixin", "TextureServicesKeyMixin" -> "skin-unlocker";
            case "RenderTargetLifetimeMixin" -> "gpumemleakfix";
            default -> null;
        };
        return external == null || !modLoaded.test(external);
    }
}
