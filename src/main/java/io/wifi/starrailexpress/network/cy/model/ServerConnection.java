package io.wifi.starrailexpress.network.cy.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Serializable;

/**
 * 服务器连接数据模型
 */
public class ServerConnection implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    
    private String ip;
    private int port;
    private String serverId;
    private String serverName;
    private String lastHeartbeat;
    private boolean isActive;
    private long totalConnections;
    private String version;
    
    public ServerConnection() {}
    
    public ServerConnection(String ip, int port, String serverId, String serverName) {
        this.ip = ip;
        this.port = port;
        this.serverId = serverId;
        this.serverName = serverName;
        this.lastHeartbeat = System.currentTimeMillis() + "";
        this.isActive = true;
        this.totalConnections = 0;
    }
    
    // Getters and Setters
    public String getIp() {
        return ip;
    }
    
    public void setIp(String ip) {
        this.ip = ip;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getServerId() {
        return serverId;
    }
    
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
    
    public String getServerName() {
        return serverName;
    }
    
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
    
    public String getLastHeartbeat() {
        return lastHeartbeat;
    }
    
    public void setLastHeartbeat(String lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public long getTotalConnections() {
        return totalConnections;
    }
    
    public void setTotalConnections(long totalConnections) {
        this.totalConnections = totalConnections;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String toJson() {
        return GSON.toJson(this);
    }
    
    public static ServerConnection fromJson(String json) {
        return GSON.fromJson(json, ServerConnection.class);
    }
    
    @Override
    public String toString() {
        return toJson();
    }
}
