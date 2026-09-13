/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.network;

import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * 单个数据包类型的累加统计。
 *
 * <p>收包路径运行在 netty 事件循环线程，发包路径运行在服务端/客户端主线程，两者会并发写入
 * 同一条统计。因此计数与字节使用 {@link LongAdder}（分段累加，争用时比 AtomicLong 快），
 * 最大/最小值使用 {@link LongAccumulator}，取代原先的普通 long 字段——那些字段在两条线程上
 * 会互相覆盖，导致统计偏低。
 */
public final class PacketStats {

    private final LongAdder count = new LongAdder();
    private final LongAdder totalSize = new LongAdder();
    private final LongAccumulator maxSize = new LongAccumulator(Long::max, 0L);
    private final LongAccumulator minSize = new LongAccumulator(Long::min, Long.MAX_VALUE);

    /** 记录一次数据包。仅在记录开启时由网络线程/主线程调用。 */
    public void update(long size) {
        count.increment();
        totalSize.add(size);
        maxSize.accumulate(size);
        minSize.accumulate(size);
    }

    public long getCount() {
        return count.sum();
    }

    public long getTotalSize() {
        return totalSize.sum();
    }

    public long getMaxSize() {
        return maxSize.get();
    }

    /** 没有样本时返回 0，而不是 {@link Long#MAX_VALUE}。 */
    public long getMinSize() {
        long value = minSize.get();
        return value == Long.MAX_VALUE ? 0L : value;
    }

    public double getAverageSize() {
        long total = getCount();
        return total > 0L ? (double) getTotalSize() / total : 0.0D;
    }
}
