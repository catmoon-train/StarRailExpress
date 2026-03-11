package io.wifi.starrailexpress.network.cy.client;


import io.wifi.starrailexpress.network.cy.config.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 控制台客户端 - 提供交互式命令行界面
 * 支持重连机制和多种服务器操作命令
 */
public class ConsoleClient {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleClient.class);
    
    private DataAccessClient client;
    private ClientConfig config;
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private AtomicBoolean isRunning = new AtomicBoolean(true);
    private Scanner scanner;
    
    public ConsoleClient(ClientConfig config) {
        this.config = config;
        this.scanner = new Scanner(System.in);
        this.client = new DataAccessClient(config.getHost(), config.getPort());
        // 设置客户端超时
        this.client.setTimeout(config.getConnectionTimeout());
    }
    
    /**
     * 启动控制台客户端
     */
    public void start() {
        System.out.println("=== ServerDataLink 控制台客户端 ===");
        System.out.println("服务器地址: " + config.getHost() + ":" + config.getPort());
        System.out.println("输入 'help' 查看可用命令");
        System.out.println("输入 'quit' 或 'exit' 退出程序");
        System.out.println();
        
        // 尝试初始连接
        if (!connectWithRetry()) {
            System.out.println("初始连接失败，进入待机模式...");
            standbyMode();
        }
        
        // 主循环
        while (isRunning.get()) {
            try {
                System.out.print("> ");
                String input = scanner.nextLine().trim();
                
                if (input.isEmpty()) continue;
                
                handleCommand(input);
                
            } catch (Exception e) {
                System.err.println("命令执行错误: " + e.getMessage());
                logger.error("命令执行异常", e);
            }
        }
        
        // 清理资源
        cleanup();
    }
    
    /**
     * 带重试的连接方法
     */
    private boolean connectWithRetry() {
        int maxRetries = config.getMaxRetries();
        int retryDelay = config.getRetryDelay();
        
        for (int i = 1; i <= maxRetries; i++) {
            System.out.println("第 " + i + "/" + maxRetries + " 次连接尝试...");
            
            if (client.connect()) {
                System.out.println("✓ 连接成功");
                
                if (client.authenticate()) {
                    System.out.println("✓ 身份认证成功");
                    isConnected.set(true);
                    return true;
                } else {
                    System.out.println("✗ 身份认证失败");
                    client.disconnect();
                }
            } else {
                System.out.println("✗ 连接失败");
            }
            
            if (i < maxRetries) {
                System.out.println("等待 " + (retryDelay / 1000) + " 秒后重试...");
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 待机模式 - 等待用户命令或自动重连
     */
    private void standbyMode() {
        System.out.println("=== 进入待机模式 ===");
        System.out.println("可用命令:");
        System.out.println("  reconnect - 尝试重新连接");
        System.out.println("  config - 查看/修改配置");
        System.out.println("  quit/exit - 退出程序");
        System.out.println("  status - 查看当前状态");
        System.out.println();
        
        while (isRunning.get() && !isConnected.get()) {
            try {
                System.out.print("[待机] > ");
                String input = scanner.nextLine().trim();
                
                switch (input.toLowerCase()) {
                    case "reconnect":
                        if (connectWithRetry()) {
                            System.out.println("重连成功！");
                            return; // 退出待机模式
                        }
                        break;
                    case "config":
                        handleConfigCommands();
                        break;
                    case "quit":
                    case "exit":
                        isRunning.set(false);
                        return;
                    case "status":
                        System.out.println("当前状态: 未连接");
                        System.out.println("服务器: " + config.getHost() + ":" + config.getPort());
                        break;
                    case "":
                        // 空命令，继续循环
                        break;
                    default:
                        System.out.println("未知命令: " + input);
                        System.out.println("在待机模式下，可使用 reconnect, config, quit, exit, status 命令");
                }
                
            } catch (Exception e) {
                System.err.println("待机模式错误: " + e.getMessage());
            }
        }
    }
    
    /**
     * 处理用户命令
     */
    private void handleCommand(String command) {
        String[] parts = command.split("\\s+");
        String cmd = parts[0].toLowerCase();
        
        switch (cmd) {
            case "help":
                showHelp();
                break;
            case "connect":
                handleConnect();
                break;
            case "disconnect":
                handleDisconnect();
                break;
            case "status":
                showStatus();
                break;
            case "ping":
                handlePing();
                break;
            case "getconfig":
                handleGetConfig();
                break;
            case "getplayers":
                handleGetPlayers();
                break;
            case "getservers":
                handleGetServers();
                break;
            case "getplayer":
                handleGetPlayer(parts);
                break;
            case "config":
                handleConfigCommands();
                break;
            case "quit":
            case "exit":
                isRunning.set(false);
                break;
            default:
                System.out.println("未知命令: " + cmd);
                System.out.println("输入 'help' 查看可用命令");
        }
    }
    
    /**
     * 显示帮助信息
     */
    private void showHelp() {
        System.out.println("=== 可用命令 ===");
        System.out.println("help          - 显示此帮助信息");
        System.out.println("connect       - 连接到服务器");
        System.out.println("disconnect    - 断开连接");
        System.out.println("status        - 显示连接状态");
        System.out.println("ping          - 发送心跳包测试连接");
        System.out.println("getconfig     - 获取服务器配置");
        System.out.println("getplayers    - 获取玩家列表");
        System.out.println("getservers    - 获取服务器列表");
        System.out.println("getplayer <uuid> - 获取指定玩家数据");
        System.out.println("config        - 查看/修改配置");
        System.out.println("quit/exit     - 退出程序");
        System.out.println();
    }
    
    /**
     * 处理连接命令
     */
    private void handleConnect() {
        if (isConnected.get()) {
            System.out.println("已经连接到服务器");
            return;
        }
        
        if (connectWithRetry()) {
            System.out.println("连接成功！");
        } else {
            System.out.println("连接失败，进入待机模式");
            standbyMode();
        }
    }
    
    /**
     * 处理断开连接命令
     */
    private void handleDisconnect() {
        if (!isConnected.get()) {
            System.out.println("当前未连接到服务器");
            return;
        }
        
        client.disconnect();
        isConnected.set(false);
        System.out.println("已断开连接");
    }
    
    /**
     * 显示状态信息
     */
    private void showStatus() {
        System.out.println("=== 客户端状态 ===");
        System.out.println("连接状态: " + (isConnected.get() ? "已连接" : "未连接"));
        System.out.println("服务器地址: " + config.getHost() + ":" + config.getPort());
        if (isConnected.get()) {
            System.out.println("认证状态: 已认证");
        }
        System.out.println();
    }
    
    /**
     * 处理心跳测试
     */
    private void handlePing() {
        if (!isConnected.get()) {
            System.out.println("未连接到服务器，请先执行 connect 命令");
            return;
        }
        
        System.out.println("发送心跳包...");
        if (client.heartbeat()) {
            System.out.println("✓ 心跳响应正常");
        } else {
            System.out.println("✗ 心跳无响应");
        }
    }
    
    /**
     * 获取服务器配置
     */
    private void handleGetConfig() {
        if (!isConnected.get()) {
            System.out.println("未连接到服务器，请先执行 connect 命令");
            return;
        }
        
        System.out.println("获取服务器配置...");
        String config = client.getConfig();
        if (config != null) {
            System.out.println("✓ 配置获取成功:");
            System.out.println(config);
        } else {
            System.out.println("✗ 配置获取失败");
        }
    }
    
    /**
     * 获取玩家列表
     */
    private void handleGetPlayers() {
        if (!isConnected.get()) {
            System.out.println("未连接到服务器，请先执行 connect 命令");
            return;
        }
        
        System.out.println("获取玩家列表...");
        String players = client.getPlayerList();
        if (players != null) {
            System.out.println("✓ 玩家列表获取成功:");
            System.out.println(players);
        } else {
            System.out.println("✗ 玩家列表获取失败");
        }
    }
    
    /**
     * 获取服务器列表
     */
    private void handleGetServers() {
        if (!isConnected.get()) {
            System.out.println("未连接到服务器，请先执行 connect 命令");
            return;
        }
        
        System.out.println("获取服务器列表...");
        String servers = client.getServerList();
        if (servers != null) {
            System.out.println("✓ 服务器列表获取成功:");
            System.out.println(servers);
        } else {
            System.out.println("✗ 服务器列表获取失败");
        }
    }
    
    /**
     * 获取指定玩家数据
     */
    private void handleGetPlayer(String[] parts) {
        if (!isConnected.get()) {
            System.out.println("未连接到服务器，请先执行 connect 命令");
            return;
        }
        
        if (parts.length < 2) {
            System.out.println("用法: getplayer <玩家UUID>");
            return;
        }
        
        String uuid = parts[1];
        System.out.println("获取玩家数据: " + uuid);
        
        // 注意：这里需要PlayerData类的支持
        // PlayerData player = client.getPlayerData(uuid);
        // if (player != null) {
        //     System.out.println("✓ 玩家数据获取成功:");
        //     System.out.println(player.toJson());
        // } else {
        //     System.out.println("✗ 玩家数据获取失败");
        // }
        System.out.println("功能暂未完全实现");
    }
    
    /**
     * 处理配置相关命令
     */
    private void handleConfigCommands() {
        System.out.println("=== 配置管理 ===");
        System.out.println("1. view - 查看当前配置");
        System.out.println("2. edit - 编辑配置");
        System.out.println("3. save - 保存配置到文件");
        System.out.println("4. load - 从文件加载配置");
        System.out.println("5. reset - 恢复默认配置");
        System.out.println("请输入选项 (1-5): ");
        
        try {
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    config.displayConfig();
                    break;
                case "2":
                    editConfiguration();
                    break;
                case "3":
                    config.save();
                    System.out.println("配置已保存");
                    break;
                case "4":
                    ClientConfig loadedConfig = ClientConfig.load();
                    this.config = loadedConfig;
                    // 更新客户端配置
                    this.client = new DataAccessClient(config.getHost(), config.getPort());
                    this.client.setTimeout(config.getConnectionTimeout());
                    System.out.println("配置已从文件加载");
                    config.displayConfig();
                    break;
                case "5":
                    this.config = new ClientConfig();
                    // 更新客户端配置
                    this.client = new DataAccessClient(config.getHost(), config.getPort());
                    this.client.setTimeout(config.getConnectionTimeout());
                    System.out.println("已恢复默认配置");
                    config.displayConfig();
                    break;
                default:
                    System.out.println("无效选项: " + choice);
            }
        } catch (Exception e) {
            System.err.println("配置操作错误: " + e.getMessage());
        }
    }
    
    /**
     * 编辑配置
     */
    private void editConfiguration() {
        System.out.println("=== 编辑配置 ===");
        System.out.println("当前配置:");
        config.displayConfig();
        
        try {
            System.out.print("服务器地址 (当前: " + config.getHost() + "): ");
            String hostInput = scanner.nextLine().trim();
            if (!hostInput.isEmpty()) {
                config.setHost(hostInput);
            }
            
            System.out.print("端口号 (当前: " + config.getPort() + "): ");
            String portInput = scanner.nextLine().trim();
            if (!portInput.isEmpty()) {
                try {
                    config.setPort(Integer.parseInt(portInput));
                } catch (NumberFormatException e) {
                    System.out.println("端口号格式错误，保持原值");
                }
            }
            
            System.out.print("连接超时(ms) (当前: " + config.getConnectionTimeout() + "): ");
            String timeoutInput = scanner.nextLine().trim();
            if (!timeoutInput.isEmpty()) {
                try {
                    config.setConnectionTimeout(Integer.parseInt(timeoutInput));
                } catch (NumberFormatException e) {
                    System.out.println("超时时间格式错误，保持原值");
                }
            }
            
            System.out.print("最大重试次数 (当前: " + config.getMaxRetries() + "): ");
            String retriesInput = scanner.nextLine().trim();
            if (!retriesInput.isEmpty()) {
                try {
                    config.setMaxRetries(Integer.parseInt(retriesInput));
                } catch (NumberFormatException e) {
                    System.out.println("重试次数格式错误，保持原值");
                }
            }
            
            System.out.print("是否启用自动重连 (当前: " + config.isAutoReconnect() + ") [true/false]: ");
            String autoReconnectInput = scanner.nextLine().trim();
            if (!autoReconnectInput.isEmpty()) {
                config.setAutoReconnect(Boolean.parseBoolean(autoReconnectInput));
            }
            
            System.out.print("是否启用调试模式 (当前: " + config.isDebugMode() + ") [true/false]: ");
            String debugInput = scanner.nextLine().trim();
            if (!debugInput.isEmpty()) {
                config.setDebugMode(Boolean.parseBoolean(debugInput));
            }
            
            System.out.println("配置已更新");
            config.displayConfig();
            
        } catch (Exception e) {
            System.err.println("编辑配置时出错: " + e.getMessage());
        }
    }
    
    /**
     * 清理资源
     */
    private void cleanup() {
        if (isConnected.get()) {
            client.disconnect();
        }
        if (scanner != null) {
            scanner.close();
        }
        System.out.println("程序已退出");
    }
    
    /**
     * 主方法
     */
    public static void main(String[] args) {
        System.out.println("正在加载配置...");
        
        // 加载配置
        ClientConfig config = ClientConfig.load();
        
        // 如果提供了命令行参数，覆盖配置
        if (args.length >= 2) {
            config.setHost(args[0]);
            try {
                config.setPort(Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                System.err.println("端口号格式错误，使用配置文件中的端口: " + config.getPort());
            }
            // 保存更新后的配置
            config.save();
        }
        
        ConsoleClient consoleClient = new ConsoleClient(config);
        consoleClient.start();
    }
}