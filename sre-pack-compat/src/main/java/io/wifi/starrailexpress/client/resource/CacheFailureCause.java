package io.wifi.starrailexpress.client.resource;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** 还原已知可回退异常，不将普通构造错误或错误消息文本误判成缓存未覆盖。 */
public final class CacheFailureCause {
    private CacheFailureCause() { }

    public static RuntimeException find(Throwable error, String exceptionClass) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = error; current != null && visited.add(current); current = current.getCause()) {
            if (current instanceof RuntimeException runtime) {
                for (Class<?> type = current.getClass(); type != null; type = type.getSuperclass()) {
                    if (type.getName().equals(exceptionClass)) {
                        return runtime;
                    }
                }
            }
        }
        return null;
    }
}
