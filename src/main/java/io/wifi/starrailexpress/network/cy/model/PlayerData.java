package io.wifi.starrailexpress.network.cy.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 玩家数据模型
 */
public class PlayerData implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private String uuid;
    private String username;
    private String firstLogin;
    private String lastLogin;
    private int playtimeMinutes;
    private String lastIp;
    private String customData;
    private Map<String ,String> skins;


    private int experience;
    private long dataVersion; // 数据版本号，用于merge冲突检测
    private String lastModifiedBy; // 最后修改者，用于追踪

    public PlayerData() {}

    public PlayerData(String uuid, String username) {
        this.uuid = uuid;
        this.username = username;
        this.firstLogin = System.currentTimeMillis() + "";
        this.lastLogin = this.firstLogin;
        this.playtimeMinutes = 0;
        this.skins = new HashMap<>();
        this.experience = 0;
    }
    public int getExperience() {
        return experience;
    }

    public PlayerData setExperience(int experience) {
        this.experience = experience;
        return this;
    }

    public Map<String, String> getSkins() {
        return skins;
    }

    public PlayerData setSkins(Map<String, String> skins) {
        this.skins = skins;
        return this;
    }

    // Getters and Setters
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstLogin() {
        return firstLogin;
    }

    public void setFirstLogin(String firstLogin) {
        this.firstLogin = firstLogin;
    }

    public String getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(String lastLogin) {
        this.lastLogin = lastLogin;
    }

    public int getPlaytimeMinutes() {
        return playtimeMinutes;
    }

    public void setPlaytimeMinutes(int playtimeMinutes) {
        this.playtimeMinutes = playtimeMinutes;
    }

    public String getLastIp() {
        return lastIp;
    }

    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    public String getCustomData() {
        return customData;
    }

    public void setCustomData(String customData) {
        this.customData = customData;
    }

    public long getDataVersion() {
        return dataVersion;
    }

    public void setDataVersion(long dataVersion) {
        this.dataVersion = dataVersion;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static PlayerData fromJson(String json) {
        return GSON.fromJson(json, PlayerData.class);
    }

    @Override
    public String toString() {
        return toJson();
    }
}
