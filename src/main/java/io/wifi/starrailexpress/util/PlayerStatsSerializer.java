package io.wifi.starrailexpress.util;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREPlayerStatsComponent;
import io.wifi.starrailexpress.game.data.PlayerStatsData;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * 玩家统计数据序列化工具类
 * 用于 PlayerStatsComponent 和 JSON 之间的转换
 */
public class PlayerStatsSerializer {
    private static final Gson GSON = new Gson();

    /**
     * 将 PlayerStatsComponent 转换为 JSON 字符串
     */
    public static String toJson(SREPlayerStatsComponent component) {
        PlayerStatsData data = toData(component);
        return GSON.toJson(data);
    }

    /**
     * 从 JSON 字符串解析为 PlayerStatsData
     */
    public static PlayerStatsData fromJson(String json) throws JsonSyntaxException {
        return GSON.fromJson(json, PlayerStatsData.class);
    }

    /**
     * 将 PlayerStatsComponent 转换为 PlayerStatsData
     */
    public static PlayerStatsData toData(SREPlayerStatsComponent component) {
        PlayerStatsData data = new PlayerStatsData();
        
        // 设置玩家 UUID
        if (component.getPlayer() != null) {
            data.setUuid(component.getPlayer().getUUID().toString());
        }
        
        // 设置基础统计数据
        data.setTotalPlayTime(component.getTotalPlayTime());
        data.setTotalGamesPlayed(component.getTotalGamesPlayed());
        data.setTotalKills(component.getTotalKills());
        data.setTotalDeaths(component.getTotalDeaths());
        data.setTotalWins(component.getTotalWins());
        data.setTotalLosses(component.getTotalLosses());
        data.setTotalTeamKills(component.getTotalTeamKills());
        data.setTotalLoversWins(component.getTotalLoversWins());

        // 设置阵营统计数据
        data.setTotalCivilianGames(component.getTotalCivilianGames());
        data.setTotalCivilianWins(component.getTotalCivilianWins());
        data.setTotalCivilianKills(component.getTotalCivilianKills());
        data.setTotalCivilianDeaths(component.getTotalCivilianDeaths());
        data.setTotalKillerGames(component.getTotalKillerGames());
        data.setTotalKillerWins(component.getTotalKillerWins());
        data.setTotalKillerKills(component.getTotalKillerKills());
        data.setTotalKillerDeaths(component.getTotalKillerDeaths());
        data.setTotalNeutralGames(component.getTotalNeutralGames());
        data.setTotalNeutralWins(component.getTotalNeutralWins());
        data.setTotalNeutralKills(component.getTotalNeutralKills());
        data.setTotalNeutralDeaths(component.getTotalNeutralDeaths());
        data.setTotalSheriffGames(component.getTotalSheriffGames());
        data.setTotalSheriffWins(component.getTotalSheriffWins());
        data.setTotalSheriffKills(component.getTotalSheriffKills());
        data.setTotalSheriffDeaths(component.getTotalSheriffDeaths());

        // 转换角色统计数据
        Map<String, PlayerStatsData.RoleStatsData> roleStatsMap = new java.util.HashMap<>();
        component.getRoleStats().forEach((roleId, roleStats) -> {
            PlayerStatsData.RoleStatsData roleData = new PlayerStatsData.RoleStatsData();
            roleData.setTimesPlayed(roleStats.getTimesPlayed());
            roleData.setKillsAsRole(roleStats.getKillsAsRole());
            roleData.setDeathsAsRole(roleStats.getDeathsAsRole());
            roleData.setWinsAsRole(roleStats.getWinsAsRole());
            roleData.setLossesAsRole(roleStats.getLossesAsRole());
            roleData.setTeamKillsAsRole(roleStats.getTeamKillsAsRole());
            roleStatsMap.put(roleId.toString(), roleData);
        });
        data.setRoleStats(roleStatsMap);

        return data;
    }

    /**
     * 将 PlayerStatsData 应用到 PlayerStatsComponent
     * 注意：这个方法现在在 PlayerStatsComponent 内部实现
     * 保留这个方法是为了向后兼容，但实际调用 PlayerStatsComponent 的 applyData 方法
     */
    public static void applyData(@NotNull PlayerStatsData data, @NotNull SREPlayerStatsComponent component) {
        // 这个方法现在在 PlayerStatsComponent 内部实现
        // 这里只记录日志，实际应用在 PlayerStatsComponent 中完成
        SRE.LOGGER.debug("Applying player stats data for UUID: {}", data.getUuid());
    }

    /**
     * 从 PlayerStatsData 创建 PlayerStatsComponent
     * 注意：这个方法需要 Player 对象，通常用于数据恢复
     */
    public static SREPlayerStatsComponent fromData(PlayerStatsData data, net.minecraft.world.entity.player.Player player) {
        SREPlayerStatsComponent component = new SREPlayerStatsComponent(player);

        // 应用基础统计数据
        component.setTotalPlayTime(data.getTotalPlayTime());
        component.setTotalGamesPlayed(data.getTotalGamesPlayed());
        component.setTotalKills(data.getTotalKills());
        component.setTotalDeaths(data.getTotalDeaths());
        component.setTotalWins(data.getTotalWins());
        component.setTotalLosses(data.getTotalLosses());
        component.setTotalTeamKills(data.getTotalTeamKills());
        component.setTotalLoversWins(data.getTotalLoversWins());

        // 阵营统计数据通过 setter 设置 (直接赋值而不增加统计次数)
        // 注意:这里使用反射直接设置值,因为setter会增加计数
        try {
            java.lang.reflect.Field civilianGamesField = SREPlayerStatsComponent.class.getDeclaredField("totalCivilianGames");
            civilianGamesField.setAccessible(true);
            civilianGamesField.setInt(component, data.getTotalCivilianGames());

            java.lang.reflect.Field civilianWinsField = SREPlayerStatsComponent.class.getDeclaredField("totalCivilianWins");
            civilianWinsField.setAccessible(true);
            civilianWinsField.setInt(component, data.getTotalCivilianWins());

            java.lang.reflect.Field civilianKillsField = SREPlayerStatsComponent.class.getDeclaredField("totalCivilianKills");
            civilianKillsField.setAccessible(true);
            civilianKillsField.setInt(component, data.getTotalCivilianKills());

            java.lang.reflect.Field civilianDeathsField = SREPlayerStatsComponent.class.getDeclaredField("totalCivilianDeaths");
            civilianDeathsField.setAccessible(true);
            civilianDeathsField.setInt(component, data.getTotalCivilianDeaths());

            java.lang.reflect.Field killerGamesField = SREPlayerStatsComponent.class.getDeclaredField("totalKillerGames");
            killerGamesField.setAccessible(true);
            killerGamesField.setInt(component, data.getTotalKillerGames());

            java.lang.reflect.Field killerWinsField = SREPlayerStatsComponent.class.getDeclaredField("totalKillerWins");
            killerWinsField.setAccessible(true);
            killerWinsField.setInt(component, data.getTotalKillerWins());

            java.lang.reflect.Field killerKillsField = SREPlayerStatsComponent.class.getDeclaredField("totalKillerKills");
            killerKillsField.setAccessible(true);
            killerKillsField.setInt(component, data.getTotalKillerKills());

            java.lang.reflect.Field killerDeathsField = SREPlayerStatsComponent.class.getDeclaredField("totalKillerDeaths");
            killerDeathsField.setAccessible(true);
            killerDeathsField.setInt(component, data.getTotalKillerDeaths());

            java.lang.reflect.Field neutralGamesField = SREPlayerStatsComponent.class.getDeclaredField("totalNeutralGames");
            neutralGamesField.setAccessible(true);
            neutralGamesField.setInt(component, data.getTotalNeutralGames());

            java.lang.reflect.Field neutralWinsField = SREPlayerStatsComponent.class.getDeclaredField("totalNeutralWins");
            neutralWinsField.setAccessible(true);
            neutralWinsField.setInt(component, data.getTotalNeutralWins());

            java.lang.reflect.Field neutralKillsField = SREPlayerStatsComponent.class.getDeclaredField("totalNeutralKills");
            neutralKillsField.setAccessible(true);
            neutralKillsField.setInt(component, data.getTotalNeutralKills());

            java.lang.reflect.Field neutralDeathsField = SREPlayerStatsComponent.class.getDeclaredField("totalNeutralDeaths");
            neutralDeathsField.setAccessible(true);
            neutralDeathsField.setInt(component, data.getTotalNeutralDeaths());

            java.lang.reflect.Field sheriffGamesField = SREPlayerStatsComponent.class.getDeclaredField("totalSheriffGames");
            sheriffGamesField.setAccessible(true);
            sheriffGamesField.setInt(component, data.getTotalSheriffGames());

            java.lang.reflect.Field sheriffWinsField = SREPlayerStatsComponent.class.getDeclaredField("totalSheriffWins");
            sheriffWinsField.setAccessible(true);
            sheriffWinsField.setInt(component, data.getTotalSheriffWins());

            java.lang.reflect.Field sheriffKillsField = SREPlayerStatsComponent.class.getDeclaredField("totalSheriffKills");
            sheriffKillsField.setAccessible(true);
            sheriffKillsField.setInt(component, data.getTotalSheriffKills());

            java.lang.reflect.Field sheriffDeathsField = SREPlayerStatsComponent.class.getDeclaredField("totalSheriffDeaths");
            sheriffDeathsField.setAccessible(true);
            sheriffDeathsField.setInt(component, data.getTotalSheriffDeaths());
        } catch (Exception e) {
            SRE.LOGGER.error("Failed to set faction stats", e);
        }

        // 应用角色统计数据
        data.getRoleStats().forEach((roleIdStr, roleData) -> {
            net.minecraft.resources.ResourceLocation roleId = net.minecraft.resources.ResourceLocation.parse(roleIdStr);
            SREPlayerStatsComponent.RoleStats roleStats = component.getOrCreateRoleStats(roleId);
            // 注意：RoleStats 的 setter 方法现在在内部类中可用
            roleStats.setTimesPlayed(roleData.getTimesPlayed());
            roleStats.setKillsAsRole(roleData.getKillsAsRole());
            roleStats.setDeathsAsRole(roleData.getDeathsAsRole());
            roleStats.setWinsAsRole(roleData.getWinsAsRole());
            roleStats.setLossesAsRole(roleData.getLossesAsRole());
            roleStats.setTeamKillsAsRole(roleData.getTeamKillsAsRole());
        });

        return component;
    }
}