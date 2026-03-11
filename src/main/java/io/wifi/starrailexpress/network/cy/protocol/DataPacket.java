package io.wifi.starrailexpress.network.cy.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Serializable;

/**
 * 数据包协议类 - 用于TCP通信
 */
public class DataPacket implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    
    public enum PacketType {
        AUTH,              // 认证
        PLAYER_DATA,       // 玩家数据
        SERVER_INFO,       // 服务器信息
        CONFIG,            // 配置
        QUERY,             // 查询
        RESPONSE,          // 响应
        ERROR,             // 错误
        HEARTBEAT          // 心跳
    }
    
    private PacketType type;
    private String token;
    private String clientIp;
    private String action;
    private String data;
    private long timestamp;
    private int statusCode;
    private String message;
    
    public DataPacket() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public DataPacket(PacketType type, String action, String data) {
        this();
        this.type = type;
        this.action = action;
        this.data = data;
        this.statusCode = 200;
    }
    
    // Getters and Setters
    public PacketType getType() {
        return type;
    }
    
    public void setType(PacketType type) {
        this.type = type;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getData() {
        return data;
    }
    
    public void setData(String data) {
        this.data = data;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    // 序列化和反序列化
    public String toJson() {
        return GSON.toJson(this);
    }
    
    public static DataPacket fromJson(String json) {
        return GSON.fromJson(json, DataPacket.class);
    }
    
    @Override
    public String toString() {
        return toJson();
    }
}
