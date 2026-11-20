/* GNU General Public License v3 or later */
package io.wifi.starrailexpress.client.ponder;

/**
 * Ponderer 与 Minecraft 1.21.1 的兼容性判断。
 *
 * <p>不能放在 {@code io.wifi.starrailexpress.mixin.client.ponder} 包里：
 * 那是 {@code sre_ponder.mixins.json} 声明的 mixin 包，普通类被注入代码直接引用时
 * 会触发 {@code IllegalClassLoadError} 并让整个客户端崩溃。
 */
public final class PondererCompat {

    /** Ponderer 编辑器 mixin 依赖的旧字段名。 */
    private static final String[] LEGACY_SCREEN_FIELDS = {"field_22789", "field_22790"};

    private static final boolean PONDERER_EDITOR_BROKEN = detectBrokenEditor();

    private PondererCompat() {
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
