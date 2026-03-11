package io.wifi.starrailexpress.api.replay;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 游戏回放读取器接口。
 * 允许第三方模组读取游戏回放中的事件数据。
 */
public interface IGameReplayReader {

    /**
     * 获取回放中的所有事件。
     *
     * @return 事件列表。
     */
    List<ReplayEvent> getEvents();

    /**
     * 根据时间戳获取特定时间点或时间段内的事件。
     *
     * @param startTime 开始时间戳。
     * @param endTime   结束时间戳。
     * @return 匹配的事件列表。
     */
    List<ReplayEvent> getEventsInTimeRange(long startTime, long endTime);

    /**
     * 获取特定玩家相关的所有事件。
     *
     * @param playerUuid 玩家的UUID。
     * @return 玩家相关事件的列表。
     */
    List<ReplayEvent> getEventsByPlayer(UUID playerUuid);

    /**
     * 获取特定事件类型的所有事件。
     *
     * @param eventType 事件类型。
     * @return 匹配的事件列表。
     */
    List<ReplayEvent> getEventsByType(ReplayEventTypes.EventType eventType);

    /**
     * 获取回放中所有玩家的UUID。
     * @return 玩家UUID列表。
     */
    List<UUID> getAllPlayerUuids();

    /**
     * 获取回放中特定玩家的名称。
     * @param playerUuid 玩家的UUID。
     * @return 玩家名称的Optional，如果玩家不存在则为空。
     */
    Optional<String> getPlayerName(UUID playerUuid);

    // 可以根据需要添加更多读取方法，例如：
    // Optional<ReplayEvent> getEventById(UUID eventId);
    // List<ReplayEvent> getEventsByLocation(BlockPos pos, int radius);
}