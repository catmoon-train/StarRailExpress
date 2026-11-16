package io.wifi.srepackcompat.jei;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.function.BooleanSupplier;

/** 读取本体的实时大厅状态；接口缺失时关闭 JEI，不使用本地配置冒充大厅。 */
public final class JeiLobbyPolicy {
    private static final class Core { private static final BooleanSupplier LOBBY = discover(); }
    public static boolean lobby() { return Core.LOBBY.getAsBoolean(); }
    public static boolean allows(boolean inWorld, boolean lobby) { return inWorld && lobby; }
    public static boolean isPresentation(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().startsWith("mezz.jei.")) return true;
        }
        return false;
    }
    private static BooleanSupplier discover() {
        try { return bind(Class.forName("io.wifi.starrailexpress.SRE")); }
        catch (ReflectiveOperationException error) { return () -> false; }
    }
    static BooleanSupplier bind(Class<?> core) throws ReflectiveOperationException {
        MethodHandle getter = MethodHandles.publicLookup().unreflectGetter(core.getField("isLobby"));
        return () -> {
            try { return (boolean) getter.invokeExact(); }
            catch (Throwable error) { throw new IllegalStateException("无法读取 SRE 大厅状态", error); }
        };
    }
    private JeiLobbyPolicy() { }
}
