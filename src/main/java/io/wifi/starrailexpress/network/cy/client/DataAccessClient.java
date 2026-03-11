package io.wifi.starrailexpress.network.cy.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.network.cy.model.PlayerData;
import io.wifi.starrailexpress.network.cy.model.ServerConnection;
import io.wifi.starrailexpress.network.cy.protocol.DataPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

/**
 * 数据访问客户端 - 用于远程连接到服务器
 * 开发者可以通过此类轻松访问服务器数据
 * 
 * 基本用法：
 * DataAccessClient client = new DataAccessClient("127.0.0.1", 8888);
 * client.connect();
 * client.authenticate();
 * PlayerData player = client.getPlayerData("player-uuid");
 * client.disconnect();
 */
public class DataAccessClient {
    private static final Logger logger = LoggerFactory.getLogger(DataAccessClient.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private String host;
    private int port;
    private String token;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private int timeout;
    
    // Merge兼容相关字段
    private MergeDataManager mergeManager; // 数据合并管理器
    
    /**
     * 初始化客户端
     */
    public DataAccessClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.timeout = 30000;
        this.mergeManager = new MergeDataManager();
    }
    
    /**
     * 连接到服务器
     */
    public boolean connect() {
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(timeout);
            
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            logger.info("已连接到服务器: {}:{}", host, port);
            return true;
        } catch (Exception e) {
            logger.error("连接到服务器失败", e);
            return false;
        }
    }
    
    /**
     * 进行身份认证
     */
    public boolean authenticate() {
        try {
            DataPacket authPacket = new DataPacket();
            authPacket.setType(DataPacket.PacketType.AUTH);
            authPacket.setAction("authenticate");
            
            logger.debug("发送认证请求: {}", authPacket.toJson());
            sendPacket(authPacket);
            
            DataPacket response = receivePacket();
            if (response != null) {
                logger.debug("收到认证响应: 类型={}, 状态码={}, 消息={}, 数据={}", 
                    response.getType(), response.getStatusCode(), response.getMessage(), response.getData());
                
                // 检查多种成功条件
                boolean isSuccess = false;
                if (response.getType() == DataPacket.PacketType.ERROR) {
                    logger.error("服务器返回错误: {}", response.getMessage());
                    return false;
                } else if (response.getStatusCode() == 200) {
                    isSuccess = true;
                } else if (response.getMessage() != null && 
                          (response.getMessage().toLowerCase().contains("success") || 
                           response.getMessage().toLowerCase().contains("ok"))) {
                    isSuccess = true;
                } else if (response.getData() != null && 
                          !response.getData().isEmpty() && 
                          !response.getData().startsWith("{\"type\":\"ERROR\"")) {
                    isSuccess = true;
                }
                
                if (isSuccess) {
                    this.token = response.getData() != null ? response.getData() : "default-token";
                    logger.info("身份认证成功");
                    return true;
                } else {
                    logger.error("认证失败，状态码: {}, 消息: {}", 
                        response.getStatusCode(), response.getMessage());
                }
            } else {
                logger.error("未收到服务器响应");
            }
        } catch (Exception e) {
            logger.error("身份认证失败", e);
        }
        return false;
    }
    
    /**
     * 获取玩家数据
     */
    public PlayerData getPlayerData(String uuid) {
        try {
            DataPacket packet = new DataPacket();
            packet.setType(DataPacket.PacketType.PLAYER_DATA);
            packet.setAction("get");
            packet.setData(uuid);
            packet.setToken(token);
            
            sendPacket(packet);
            
            DataPacket response = receivePacket();
            if (response != null && response.getStatusCode() == 200) {
                return PlayerData.fromJson(response.getData());
            }
        } catch (Exception e) {
            logger.error("获取玩家数据失败", e);
        }
        return null;
    }
    
    /**
     * 保存玩家数据
     */
    public boolean savePlayerData(PlayerData player) {
        try {
            DataPacket packet = new DataPacket();
            packet.setType(DataPacket.PacketType.PLAYER_DATA);
            packet.setAction("save");
            packet.setData(player.toJson());
            packet.setToken(token);
            
            sendPacket(packet);
            
            DataPacket response = receivePacket();
            return response != null && response.getStatusCode() == 200;
        } catch (Exception e) {
            logger.error("保存玩家数据失败", e);
        }
        return false;
    }
    
    /**
     * 获取所有玩家数据列表
     */
    public String getPlayerList() {
        try {
            DataPacket packet = new DataPacket();
            packet.setType(DataPacket.PacketType.PLAYER_DATA);
            packet.setAction("list");
            packet.setToken(token);
            
            sendPacket(packet);
            
            DataPacket response = receivePacket();
            if (response != null && response.getStatusCode() == 200) {
                return response.getData();
            }
        } catch (Exception e) {
            logger.error("获取玩家列表失败", e);
        }
        return null;
    }
    
    /**
     * 注册服务器连接
     */
    public boolean registerServer(ServerConnection server) {
        try {
            DataPacket packet = new DataPacket();
            packet.setType(DataPacket.PacketType.SERVER_INFO);
            packet.setAction("register");
            packet.setData(server.toJson());
            packet.setToken(token);
            
            sendPacket(packet);
            
            DataPacket response = receivePacket();
            return response != null && response.getStatusCode() == 200;
        } catch (Exception e) {
            logger.error("注册服务器失败", e);
        }
        return false;
    }
    
    /**
     * 获取所有服务器连接列表
     */
    public String getServerList() {
        try {
            DataPacket packet = new DataPacket();
            packet.setType(DataPacket.PacketType.SERVER_INFO);
            packet.setAction("list");
            packet.setToken(token);
            
            sendPacket(packet);
            
            DataPacket response = receivePacket();
            if (response != null && response.getStatusCode() == 200) {
                return response.getData();
            }
        } catch (Exception e) {
            logger.error("获取服务器列表失败", e);
        }
        return null;
    }
    
    /**
     * 发送心跳包
     */
    public boolean heartbeat() {
        try {
            DataPacket packet = new DataPacket();
            packet.setType(DataPacket.PacketType.HEARTBEAT);
            packet.setToken(token);
            
            sendPacket(packet);
            
            DataPacket response = receivePacket();
            return response != null && "pong".equals(response.getMessage());
        } catch (Exception e) {
            logger.error("心跳失败", e);
        }
        return false;
    }
    
    /**
     * 获取配置信息
     */
    public String getConfig() {
        try {
            DataPacket packet = new DataPacket();
            packet.setType(DataPacket.PacketType.CONFIG);
            packet.setToken(token);
            
            sendPacket(packet);
            
            DataPacket response = receivePacket();
            if (response != null && response.getStatusCode() == 200) {
                return response.getData();
            }
        } catch (Exception e) {
            logger.error("获取配置失败", e);
        }
        return null;
    }
    
    /**
     * 发送数据包
     */
    private void sendPacket(DataPacket packet) {
        if (writer != null) {
            writer.println(packet.toJson());
            writer.flush();
        }
    }
    
    /**
     * 接收数据包
     */
    private DataPacket receivePacket() throws IOException {
        if (reader != null) {
            String line = reader.readLine();
            if (line != null) {
                logger.debug("收到原始数据: {}", line);
                try {
                    // 尝试解析为JSON对象
                    return DataPacket.fromJson(line);
                } catch (Exception e) {
                    // 如果解析失败，创建一个错误响应包
                    logger.warn("JSON解析失败: {}", e.getMessage());
                    DataPacket errorPacket = new DataPacket();
                    errorPacket.setType(DataPacket.PacketType.ERROR);
                    errorPacket.setStatusCode(500);
                    errorPacket.setMessage("服务器响应格式错误: " + line);
                    errorPacket.setData(line);
                    return errorPacket;
                }
            }
        }
        return null;
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
            logger.info("已断开连接");
        } catch (IOException e) {
            logger.error("断开连接失败", e);
        }
    }
    
    /**
     * 设置超时时间
     */
    public void setTimeout(int milliseconds) {
        this.timeout = milliseconds;
        if (socket != null) {
            try {
                socket.setSoTimeout(milliseconds);
            } catch (Exception e) {
                logger.error("设置超时失败", e);
            }
        }
    }
    
    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && writer != null && reader != null;
    }
    
    /**
     * 获取主机名（用于内部调用）
     */
    public String getHost() {
        return host;
    }
    
    /**
     * 获取端口号（用于内部调用）
     */
    public int getPort() {
        return port;
    }
    
    // ==================== 可选Merge多服务器同步功能 ====================
    // 以下方法用于多服务器场景，可选使用
    
    /**
     * 启用merge同步功能（多服务器场景）
     */
    public void enableMergeSynchronization() {
        mergeManager.enableMergeSynchronization();
    }
    
    /**
     * 禁用merge同步功能
     */
    public void disableMergeSynchronization() {
        mergeManager.disableMergeSynchronization();
    }
    
    /**
     * 添加连接的服务器（用于多服务器同步）
     */
    public void addConnectedServer(DataAccessClient serverClient) {
        mergeManager.addConnectedServer(serverClient);
    }
    
    /**
     * 移除连接的服务器
     */
    public void removeConnectedServer(DataAccessClient serverClient) {
        mergeManager.removeConnectedServer(serverClient);
    }
    
    /**
     * 合并保存玩家数据（防止数据丢失）
     */
    public boolean savePlayerDataWithMerge(PlayerData player) {
        return mergeManager.savePlayerDataWithMerge(this, player);
    }
    
    /**
     * 获取连接的服务器数量
     */
    public int getConnectedServerCount() {
        return mergeManager.getConnectedServerCount();
    }
    
    /**
     * 主方法 - 用于测试和演示客户端功能
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.out.println("=== ServerDataLink 客户端测试 ===");
        
        // 默认连接参数
        String host = "127.0.0.1";
        int port = 8888;
        
        // 如果提供了命令行参数，则使用参数值
        if (args.length >= 2) {
            host = args[0];
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("端口号格式错误，使用默认端口: " + port);
            }
        }
        
        System.out.println("连接地址: " + host + ":" + port);
        
        // 创建客户端实例
        DataAccessClient client = new DataAccessClient(host, port);
        
        try {
            // 连接到服务器
            System.out.println("正在连接到服务器...");
            if (!client.connect()) {
                System.err.println("连接失败！请检查服务器是否正在运行。");
                return;
            }
            System.out.println("✓ 连接成功");
            
            // 身份认证
            System.out.println("正在进行身份认证...");
            if (!client.authenticate()) {
                System.err.println("身份认证失败！");
                client.disconnect();
                return;
            }
            System.out.println("✓ 身份认证成功");
            
            // 发送心跳测试
            System.out.println("发送心跳包...");
            if (client.heartbeat()) {
                System.out.println("✓ 心跳响应正常");
            } else {
                System.out.println("✗ 心跳无响应");
            }
            
            // 获取配置信息
            System.out.println("获取服务器配置...");
            String config = client.getConfig();
            if (config != null) {
                System.out.println("✓ 配置获取成功");
                System.out.println("配置内容: " + config);
            } else {
                System.out.println("✗ 配置获取失败");
            }
            
            // 获取玩家列表
            System.out.println("获取玩家列表...");
            String playerList = client.getPlayerList();
            if (playerList != null) {
                System.out.println("✓ 玩家列表获取成功");
                System.out.println("玩家列表: " + playerList);
            } else {
                System.out.println("✗ 玩家列表获取失败");
            }
            
            // 获取服务器列表
            System.out.println("获取服务器列表...");
            String serverList = client.getServerList();
            if (serverList != null) {
                System.out.println("✓ 服务器列表获取成功");
                System.out.println("服务器列表: " + serverList);
            } else {
                System.out.println("✗ 服务器列表获取失败");
            }
            
            System.out.println("\n=== 测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 断开连接
            client.disconnect();
            System.out.println("已断开连接");
        }
    }
}
