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
