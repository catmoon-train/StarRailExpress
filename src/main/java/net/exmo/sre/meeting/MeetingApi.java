package net.exmo.sre.meeting;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 紧急会议系统对外 API（供扩展模组调用）。
 *
 * 会议需要地图配置启用（{@code meetingEnabled} 等字段，可在地图配置 GUI 的
 * 「会议」标签页或 {@code /sre:area_manager set meetingEnabled true} 配置）。
 * 右键尸体召开会议的默认交互已内置；扩展可用本类实现「紧急按钮」等自定义入口。
 */
public final class MeetingApi {

    private MeetingApi() {
    }

    /**
     * 以「发现尸体」的名义召开会议（与右键尸体等价，带重复上报 / 冷却检查）。
     *
     * @return 是否成功召开
     */
    public static boolean reportBody(ServerPlayer reporter, PlayerBodyEntity body) {
        return MeetingManager.tryReportBody(reporter, body);
    }

    /**
     * 直接召开一场紧急会议（无尸体，Among Us 的紧急按钮式）。
     *
     * @param victimName 可选的「死者」展示名；无则传 null
     * @return 是否成功召开（未启用 / 冷却中 / 已在会议中返回 false）
     */
    public static boolean startMeeting(ServerLevel level, ServerPlayer reporter, @Nullable String victimName) {
        return MeetingManager.startMeeting(level, reporter, victimName);
    }

    /**
     * 召开紧急会议（紧急模式：绕过开局冷却与会议间冷却）。
     *
     * @param victimName 可选的「死者」展示名；无则传 null
     * @param emergency   是否为紧急会议（如角色死亡触发），紧急时忽略冷却
     * @return 是否成功召开
     */
    public static boolean startMeeting(ServerLevel level, ServerPlayer reporter, @Nullable String victimName,
            boolean emergency) {
        return MeetingManager.startMeeting(level, reporter, victimName, emergency);
    }

    /** 立即结束当前会议（全员送回原位）。 */
    public static void endMeeting() {
        MeetingManager.endMeeting(false);
    }

    /** 当前是否有会议进行中。 */
    public static boolean isMeetingActive() {
        return MeetingManager.isActive();
    }

    /** 指定玩家是否是本场会议的参会者。 */
    public static boolean isParticipant(UUID playerUuid) {
        return MeetingManager.isParticipant(playerUuid);
    }
}
