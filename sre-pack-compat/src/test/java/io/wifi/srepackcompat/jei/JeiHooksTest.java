package io.wifi.srepackcompat.jei;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 执行正式回调字节码，只替换大厅查询，不启动 Minecraft 或 JEI。 */
public class JeiHooksTest {
    public static class Gate {
        static boolean lobby;
        public static boolean enabled() { return lobby; }
    }
    private Class<?> probe(String simpleName) throws Exception {
        String original = "io/wifi/srepackcompat/mixin/jei/" + simpleName;
        var writer = new ClassWriter(0);
        try (var in = getClass().getResourceAsStream("/" + original + ".class")) {
            assertNotNull(in);
            new ClassReader(in).accept(new ClassRemapper(writer, new SimpleRemapper(Map.of(
                    original, "io/wifi/srepackcompat/jei/probe/" + simpleName,
                    "io/wifi/srepackcompat/jei/JeiLobbyGate", Type.getInternalName(Gate.class)))), 0);
        }
        class Loader extends ClassLoader {
            Loader() { super(JeiHooksTest.class.getClassLoader()); }
            Class<?> define() { var bytes = writer.toByteArray(); return defineClass(null, bytes, 0, bytes.length); }
        }
        return new Loader().define();
    }
    @Test void overlaysAndTextHandlersOnlyCancelOutsideLobby() throws Exception {
        for (String name : new String[]{"GuiEventHandlerMixin", "ClientInputHandlerMixin"}) {
            var type = probe(name);
            for (var method : type.getDeclaredMethods()) {
                if (method.getParameterTypes()[0] != CallbackInfo.class) continue;
                method.setAccessible(true);
                for (boolean lobby : new boolean[]{false, true, false}) {
                    Gate.lobby = lobby;
                    var callback = new CallbackInfo(method.getName(), true);
                    method.invoke(type.getConstructor().newInstance(), callback);
                    assertEquals(!lobby, callback.isCancelled(), name + ":" + method.getName());
                }
            }
        }
    }
    @Test void disabledJeiNeverConsumesKeysMouseOrCharacters() throws Exception {
        for (String name : new String[]{"ClientInputHandlerMixin", "JeiKeyMappingMixin", "ClientToggleStateMixin", "GuiEventHandlerMixin"}) {
            var type = probe(name);
            for (var method : type.getDeclaredMethods()) {
                if (method.getParameterTypes()[0] != CallbackInfoReturnable.class) continue;
                method.setAccessible(true);
                for (boolean lobby : new boolean[]{false, true, false}) {
                    Gate.lobby = lobby;
                    var callback = new CallbackInfoReturnable<Boolean>(method.getName(), true);
                    method.invoke(type.getConstructor().newInstance(), callback);
                    assertEquals(!lobby, callback.isCancelled());
                    if (!lobby) assertFalse(callback.getReturnValue());
                }
            }
        }
    }
}
