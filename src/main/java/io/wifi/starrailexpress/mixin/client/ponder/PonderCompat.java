package io.wifi.starrailexpress.mixin.client.ponder;

/**
 * Ponderer 与 Minecraft 1.21.1 的兼容性判断。
 *
 * <p>Ponderer 1.12.0 的 PonderEditorMixin 仍然使用旧版本 Minecraft 的 intermediary 字段名
 * （field_22789 / field_22790，对应旧版 Screen.width / Screen.height）。
 * 1.21.1 里这两个字段的 intermediary 名已经变成 field_19690 / field_19691，
 * 于是 PonderUI 渲染悬停提示时会抛 NoSuchFieldError 并崩溃。
 */
public final class PonderCompat {
    /** Ponderer 编辑器 mixin 依赖的旧字段名。 */
    private static final String[] LEGACY_SCREEN_FIELDS = {"field_22789", "field_22790"};

    private static final boolean PONDERER_EDITOR_BROKEN = detectBrokenEditor();

    private PonderCompat() {
    }

    /** Ponderer 的场景编辑器在当前环境下是否会因为字段缺失而崩溃。 */
    public static boolean isPondererEditorBroken() {
        return PONDERER_EDITOR_BROKEN;
    }

    private static boolean detectBrokenEditor() {
        try {
            Class<?> ui = Class.forName("net.createmod.ponder.foundation.ui.PonderUI");
            for (String field : LEGACY_SCREEN_FIELDS) {
                if (!hasField(ui, field)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable error) {
            return true;
        }
    }

    private static boolean hasField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                current.getDeclaredField(name);
                return true;
            } catch (NoSuchFieldException ignored) {
                // 继续向上查找
            }
        }
        return false;
    }
}
