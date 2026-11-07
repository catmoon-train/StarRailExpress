package io.wifi.srepackcompat.startup;

import java.io.IOException;

/** 缓存只读取当前类路径上的本地原始字节；null 表示交回 Knot 原流程。 */
interface ClassBytesSource extends AutoCloseable {
    byte[] read(String className) throws IOException;
    boolean isCurrent();
    @Override default void close() { }
}
