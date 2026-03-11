package io.wifi.starrailexpress.network.cy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 客户端配置管理类
 * 支持从配置文件加载和保存配置
 */
public class ClientConfig {
    private static final Logger logger = LoggerFactory.getLogger(ClientConfig.class);
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    
    // 配置文件路径
    private static final String CONFIG_FILE = "client-config.json";
    
    // 默认配置值
    private String host = "127.0.0.1";
    private int port = 8888;
    private int connectionTimeout = 30000;
    private int maxRetries = 5;
    private int retryDelay = 3000;
    private boolean autoReconnect = true;
    private boolean debugMode = false;
    private String logLevel = "INFO";
    
    public ClientConfig() {
        // 默认构造函数
    }
    
    /**
     * 从配置文件加载配置
     */
    public static ClientConfig load() {
        Path configPath = Paths.get(CONFIG_FILE);
        
        if (!Files.exists(configPath)) {
            logger.info("配置文件不存在，使用默认配置");
            ClientConfig defaultConfig = new ClientConfig();
            defaultConfig.save(); // 保存默认配置
            return defaultConfig;
        }
        
        try (Reader reader = Files.newBufferedReader(configPath)) {
            ClientConfig config = GSON.fromJson(reader, ClientConfig.class);
            logger.info("配置文件加载成功: {}", CONFIG_FILE);
            return config;
        } catch (Exception e) {
            logger.error("加载配置文件失败，使用默认配置", e);
            return new ClientConfig();
        }
    }
    
    /**
     * 保存配置到文件
     */
    public void save() {
        try (Writer writer = Files.newBufferedWriter(Paths.get(CONFIG_FILE))) {
            GSON.toJson(this, writer);
            logger.info("配置已保存到: {}", CONFIG_FILE);
        } catch (Exception e) {
            logger.error("保存配置文件失败", e);
        }
    }
    
    /**
     * 从Properties对象加载配置（向后兼容）
     */
    public static ClientConfig fromProperties(Properties props) {
        ClientConfig config = new ClientConfig();
        
        config.host = props.getProperty("host", config.host);
        config.port = Integer.parseInt(props.getProperty("port", String.valueOf(config.port)));
        config.connectionTimeout = Integer.parseInt(props.getProperty("connection.timeout", 
            String.valueOf(config.connectionTimeout)));
        config.maxRetries = Integer.parseInt(props.getProperty("max.retries", 
            String.valueOf(config.maxRetries)));
        config.retryDelay = Integer.parseInt(props.getProperty("retry.delay", 
            String.valueOf(config.retryDelay)));
        config.autoReconnect = Boolean.parseBoolean(props.getProperty("auto.reconnect", 
            String.valueOf(config.autoReconnect)));
        config.debugMode = Boolean.parseBoolean(props.getProperty("debug.mode", 
            String.valueOf(config.debugMode)));
        config.logLevel = props.getProperty("log.level", config.logLevel);
        
        return config;
    }
    
    // Getter和Setter方法
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public int getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    public int getRetryDelay() {
        return retryDelay;
    }
    
    public void setRetryDelay(int retryDelay) {
        this.retryDelay = retryDelay;
    }
    
    public boolean isAutoReconnect() {
        return autoReconnect;
    }
    
    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }
    
    public boolean isDebugMode() {
        return debugMode;
    }
    
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
    
    public String getLogLevel() {
        return logLevel;
    }
    
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
    
    /**
     * 显示当前配置
     */
    public void displayConfig() {
        System.out.println("=== 当前配置 ===");
        System.out.println("服务器地址: " + host + ":" + port);
        System.out.println("连接超时: " + connectionTimeout + " ms");
        System.out.println("最大重试次数: " + maxRetries);
        System.out.println("重试延迟: " + retryDelay + " ms");
        System.out.println("自动重连: " + (autoReconnect ? "开启" : "关闭"));
        System.out.println("调试模式: " + (debugMode ? "开启" : "关闭"));
        System.out.println("日志级别: " + logLevel);
        System.out.println();
    }
    
    @Override
    public String toString() {
        return GSON.toJson(this);
    }
}