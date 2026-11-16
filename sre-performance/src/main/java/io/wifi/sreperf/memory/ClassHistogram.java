package io.wifi.sreperf.memory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 只有用户显式请求时才执行 HotSpot 诊断命令；-all 包含尚未回收的不可达对象。 */
public final class ClassHistogram {
    private static final Pattern ROW = Pattern.compile("^\\s*\\d+:\\s+(\\d+)\\s+(\\d+)\\s+(.+)$");
    private ClassHistogram() { }

    public static String capture() throws Exception {
        return capture(ManagementFactory.getPlatformMBeanServer());
    }

    static String capture(MBeanServer server) throws Exception {
        ObjectName name = new ObjectName("com.sun.management:type=DiagnosticCommand");
        if (!server.isRegistered(name)) throw new UnsupportedOperationException("DiagnosticCommand MBean is unavailable on this JVM");
        Object result = server.invoke(name, "gcClassHistogram", new Object[]{new String[]{"-all"}}, new String[]{"[Ljava.lang.String;"});
        if (!(result instanceof String text) || text.isBlank()) {
            throw new UnsupportedOperationException("gcClassHistogram returned no text");
        }
        return text;
    }

    public static Map<String, Object> parse(String text) {
        List<Map<String, Object>> rows = new ArrayList<>();
        long instances = 0, bytes = 0;
        for (String line : text.split("\\R")) {
            var match = ROW.matcher(line);
            if (!match.matches()) continue;
            long count = Long.parseLong(match.group(1));
            long shallow = Long.parseLong(match.group(2));
            String classAndModule = match.group(3).trim();
            int space = classAndModule.indexOf(' ');
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("class", space < 0 ? classAndModule : classAndModule.substring(0, space));
            row.put("classAndModule", classAndModule);
            row.put("instances", count);
            row.put("shallowBytes", shallow);
            rows.add(row);
            instances = Math.addExact(instances, count);
            bytes = Math.addExact(bytes, shallow);
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("Unrecognized class histogram format; see histogram.txt");
        rows.sort(Comparator.comparingLong((Map<String, Object> row) -> (Long) row.get("shallowBytes")).reversed());
        return Map.of("supported", true, "command", "DiagnosticCommand.gcClassHistogram -all", "classes", rows,
                "totalInstances", instances, "totalShallowBytes", bytes,
                "measurement", "All objects including unreachable/not-yet-collected objects; per-class shallow bytes only, NOT retained bytes or mod ownership. Explicit safepoint heap scan can pause the game. No explicit GC requested.");
    }
}
