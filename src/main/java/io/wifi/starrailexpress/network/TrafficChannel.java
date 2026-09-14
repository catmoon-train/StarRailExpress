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

/**
 * 网络统计里除 Minecraft 数据包之外的两条通道，服务端与客户端命令共用这里的开关与取值逻辑，
 * 免得两边的 {@code http}/{@code sql} 子命令各写一遍。
 *
 * <p>两条通道的开关互相独立，也可被主命令的 {@code start}/{@code stop} 联动。
 */
public enum TrafficChannel {

    HTTP("HTTP", "http"),
    SQL("SQL", "sql");

    private final String label;
    private final String argument;

    TrafficChannel(String label, String argument) {
        this.label = label;
        this.argument = argument;
    }

    /** 显示名，例如 {@code HTTP}。 */
    public String label() {
        return label;
    }

    /** 命令里使用的字面量，例如 {@code http}。 */
    public String argument() {
        return argument;
    }

    public ChannelTrafficStats stats(NetworkStatistics statistics) {
        return this == HTTP ? statistics.getHttpTraffic() : statistics.getSqlTraffic();
    }

    public boolean isRecording(NetworkStatistics statistics) {
        return this == HTTP ? statistics.isHttpRecording() : statistics.isSqlRecording();
    }

    public void start(NetworkStatistics statistics) {
        if (this == HTTP) {
            statistics.startHttpRecording();
        } else {
            statistics.startSqlRecording();
        }
    }

    public void stop(NetworkStatistics statistics) {
        if (this == HTTP) {
            statistics.stopHttpRecording();
        } else {
            statistics.stopSqlRecording();
        }
    }

    public void reset(NetworkStatistics statistics) {
        if (this == HTTP) {
            statistics.resetHttpStats();
        } else {
            statistics.resetSqlStats();
        }
    }

    public long recordingStartedAt(NetworkStatistics statistics) {
        return this == HTTP ? statistics.getHttpRecordingStartedAt() : statistics.getSqlRecordingStartedAt();
    }

    /** 该通道的数据来源，用于命令提示——避免看到 0 就以为是统计坏了。 */
    public String scopeNote(boolean serverSide) {
        if (this == HTTP) {
            return serverSide
                    ? "覆盖: SyncRequests 接口（玩家数据、赞助者名单）与服务端语言文件下载"
                    : "覆盖: 场景资源下载";
        }
        return serverSide
                ? "覆盖: MySQL 玩家数据同步与全局战绩读写"
                : "SQL 只在服务端发生，客户端侧通常没有数据";
    }
}
