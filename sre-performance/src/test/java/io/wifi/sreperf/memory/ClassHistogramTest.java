package io.wifi.sreperf.memory;

import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ClassHistogramTest {
    static final String TEXT = """
             num     #instances         #bytes  class name (module)
            -------------------------------------------------------
               1:             2             32  example.Pokemon
               2:             3            160  [B (java.base@21)
            Total             5            192
            """;

    @Test void invokesOnlyDiagnosticHistogramWithAllAndNeverGc() throws Exception {
        AtomicInteger invokes = new AtomicInteger();
        MBeanServer server = server((method, args) -> {
            if (method.equals("isRegistered")) {
                assertEquals("com.sun.management:type=DiagnosticCommand", args[0].toString());
                return true;
            }
            if (method.equals("invoke")) {
                invokes.incrementAndGet();
                assertEquals("gcClassHistogram", args[1]);
                assertArrayEquals(new String[]{"-all"}, (String[]) ((Object[]) args[2])[0]);
                assertArrayEquals(new String[]{"[Ljava.lang.String;"}, (String[]) args[3]);
                return TEXT;
            }
            fail("Unexpected MBean call: " + method);
            return null;
        });
        assertEquals(TEXT, ClassHistogram.capture(server));
        assertEquals(1, invokes.get());
    }

    @Test void unavailableMBeanIsExplicitAndDoesNotInvokeAnything() {
        MBeanServer server = server((method, args) -> {
            assertEquals("isRegistered", method);
            return false;
        });
        assertThrows(UnsupportedOperationException.class, () -> ClassHistogram.capture(server));
    }

    @Test void aggregatesShallowBytesAndSortsBySizeWithoutDuplicatingTotal() {
        Map<String, Object> out = ClassHistogram.parse(TEXT.replace("\n", "\r\n"));
        assertEquals(5L, out.get("totalInstances"));
        assertEquals(192L, out.get("totalShallowBytes"));
        List<?> rows = (List<?>) out.get("classes");
        assertEquals(2, rows.size());
        assertEquals("[B", ((Map<?, ?>) rows.getFirst()).get("class"));
        assertEquals(160L, ((Map<?, ?>) rows.getFirst()).get("shallowBytes"));
        assertTrue(out.get("measurement").toString().contains("NOT retained"));
    }

    @Test void malformedOutputDoesNotPretendToBeAnEmptyHeap() {
        assertThrows(IllegalArgumentException.class, () -> ClassHistogram.parse("unsupported command"));
    }

    private interface Calls { Object call(String method, Object[] args); }
    private static MBeanServer server(Calls calls) {
        return (MBeanServer) Proxy.newProxyInstance(ClassHistogramTest.class.getClassLoader(), new Class[]{MBeanServer.class},
                (proxy, method, args) -> calls.call(method.getName(), args));
    }
}
