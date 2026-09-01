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

package io.wifi.starrailexpress.schedule;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * 定时任务管理:负责 /sre:schedule 的持久化与触发。
 * 配置保存在 config/starrailexpress/schedule.json(Gson 漂亮输出)。
 */
public final class ScheduleManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path LOCAL_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("starrailexpress/schedule.json");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private static final List<ScheduleTask> schedules = new CopyOnWriteArrayList<>();
    private static volatile MinecraftServer server;

    private ScheduleManager() {
    }

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(ScheduleManager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(ScheduleManager::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        ServerTickEvents.END_SERVER_TICK.register(ScheduleManager::tick);
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    private static void onServerStarted(MinecraftServer startedServer) {
        server = startedServer;
        loadFromFile();
        // 重新初始化运行时状态,避免重启后立即触发
        long now = System.currentTimeMillis();
        for (ScheduleTask task : schedules) {
            initNextRun(task, now, startedServer.getTickCount());
        }
        // 服务器启动任务
        for (ScheduleTask task : schedules) {
            if (task.type == ScheduleType.SERVER_START && !task.paused) {
                execute(startedServer, task);
            }
        }
        saveToFile();
    }

    private static void onServerStopping(MinecraftServer stoppingServer) {
        for (ScheduleTask task : schedules) {
            if (task.type == ScheduleType.SERVER_STOP && !task.paused) {
                execute(stoppingServer, task);
            }
        }
        saveToFile();
    }

    private static void tick(MinecraftServer tickedServer) {
        long now = System.currentTimeMillis();
        long tickCount = tickedServer.getTickCount();
        for (ScheduleTask task : schedules) {
            if (task.paused) {
                continue;
            }
            switch (task.type) {
                case REALTIME_DAILY, REALTIME_WEEKLY, REALTIME_ONCE, REALTIME_INTERVAL -> {
                    if (now >= task.nextRunAtMillis) {
                        execute(tickedServer, task);
                        recomputeNext(task, now);
                    }
                }
                case GAMETIME_INTERVAL -> {
                    if (tickCount >= task.nextRunAtTick) {
                        execute(tickedServer, task);
                        // 服务器卡顿错过多个周期时只补一次,防止瞬间刷屏
                        while (task.nextRunAtTick <= tickCount) {
                            task.nextRunAtTick += Math.max(1, task.intervalTicks);
                        }
                    }
                }
                case SERVER_START, SERVER_STOP -> {
                    // 由生命周期事件触发,不在此处理
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 触发时间计算
    // ------------------------------------------------------------------

    private static void initNextRun(ScheduleTask task, long now, long currentTick) {
        switch (task.type) {
            case REALTIME_DAILY, REALTIME_WEEKLY -> task.nextRunAtMillis = nextRealtimeOccurrence(task, now);
            case REALTIME_ONCE -> task.nextRunAtMillis = parseDateTimeMillis(task.datetime);
            case REALTIME_INTERVAL -> task.nextRunAtMillis = now + Math.max(1, task.intervalSeconds) * 1000L;
            case GAMETIME_INTERVAL -> task.nextRunAtTick = currentTick + Math.max(1, task.intervalTicks);
            case SERVER_START, SERVER_STOP -> {
                // 无运行时状态
            }
        }
    }

    private static void recomputeNext(ScheduleTask task, long now) {
        switch (task.type) {
            case REALTIME_DAILY, REALTIME_WEEKLY -> task.nextRunAtMillis = nextRealtimeOccurrence(task, now);
            case REALTIME_ONCE -> {
                // 一次性任务执行后移除
                schedules.remove(task);
                saveToFile();
            }
            case REALTIME_INTERVAL -> task.nextRunAtMillis = now + Math.max(1, task.intervalSeconds) * 1000L;
            default -> {
                // 其他类型不在这里重算
            }
        }
    }

    /** 计算 daily/weekly 任务的下一次触发时刻(严格晚于 now)。 */
    private static long nextRealtimeOccurrence(ScheduleTask task, long now) {
        LocalDateTime nowLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZONE);
        LocalTime time = LocalTime.of(task.hour, task.minute);
        if (task.type == ScheduleType.REALTIME_DAILY) {
            LocalDateTime candidate = LocalDateTime.of(nowLocal.toLocalDate(), time);
            if (!candidate.isAfter(nowLocal)) {
                candidate = candidate.plusDays(1);
            }
            return candidate.atZone(ZONE).toInstant().toEpochMilli();
        }
        // REALTIME_WEEKLY:在指定星期中找下一个
        for (int i = 0; i < 8; i++) {
            LocalDate date = nowLocal.toLocalDate().plusDays(i);
            if (task.days.contains(date.getDayOfWeek().getValue())) {
                LocalDateTime candidate = LocalDateTime.of(date, time);
                if (candidate.isAfter(nowLocal)) {
                    return candidate.atZone(ZONE).toInstant().toEpochMilli();
                }
            }
        }
        // 未配置任何星期,永不触发(保持远未来的占位值)
        return now + 365L * 24 * 60 * 60 * 1000;
    }

    private static long parseDateTimeMillis(String datetime) {
        try {
            return LocalDateTime.parse(datetime, DATETIME_FORMAT).atZone(ZONE).toInstant().toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------------
    // 执行与存储
    // ------------------------------------------------------------------

    private static void execute(MinecraftServer srv, ScheduleTask task) {
        GameUtils.executeFunction(srv.createCommandSourceStack().withPermission(4), task.function);
    }

    private static void saveToFile() {
        try {
            Files.createDirectories(LOCAL_FILE.getParent());
            Files.writeString(LOCAL_FILE, GSON.toJson(schedules), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            SRE.LOGGER.warn("[Schedule] 写入本地定时任务配置失败", exception);
        }
    }

    private static boolean loadFromFile() {
        try {
            if (!Files.exists(LOCAL_FILE)) {
                return false;
            }
            String json = Files.readString(LOCAL_FILE, StandardCharsets.UTF_8);
            List<ScheduleTask> parsed = GSON.fromJson(json, new TypeToken<List<ScheduleTask>>() {
            }.getType());
            schedules.clear();
            if (parsed != null) {
                for (ScheduleTask task : parsed) {
                    if (task == null) {
                        continue;
                    }
                    task.normalized();
                    if (!task.id.isBlank() && !task.function.isBlank()) {
                        schedules.add(task);
                    }
                }
            }
            return true;
        } catch (Exception exception) {
            SRE.LOGGER.warn("[Schedule] 读取本地定时任务配置失败", exception);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 命令接口
    // ------------------------------------------------------------------

    public static boolean addTask(ScheduleTask task) {
        if (task.id == null || task.id.isBlank() || task.function == null || task.function.isBlank()) {
            return false;
        }
        for (ScheduleTask existing : schedules) {
            if (existing.id.equals(task.id)) {
                return false;
            }
        }
        task.normalized();
        MinecraftServer srv = server;
        initNextRun(task, System.currentTimeMillis(), srv == null ? 0 : srv.getTickCount());
        schedules.add(task);
        saveToFile();
        return true;
    }

    public static boolean removeTask(String id) {
        boolean removed = schedules.removeIf(task -> task.id.equals(id));
        if (removed) {
            saveToFile();
        }
        return removed;
    }

    public static void clearTasks() {
        schedules.clear();
        saveToFile();
    }

    public static List<ScheduleTask> getTasks() {
        return new ArrayList<>(schedules);
    }

    public static boolean pauseTask(String id) {
        for (ScheduleTask task : schedules) {
            if (task.id.equals(id)) {
                task.paused = true;
                saveToFile();
                return true;
            }
        }
        return false;
    }

    /** 暂停全部计划,返回实际被暂停的数量。 */
    public static int pauseAll() {
        int count = 0;
        for (ScheduleTask task : schedules) {
            if (!task.paused) {
                task.paused = true;
                count++;
            }
        }
        if (count > 0) {
            saveToFile();
        }
        return count;
    }

    public static boolean resumeTask(String id) {
        for (ScheduleTask task : schedules) {
            if (task.id.equals(id)) {
                if (task.paused) {
                    task.paused = false;
                    initNextRun(task, System.currentTimeMillis(),
                            server == null ? 0 : server.getTickCount());
                    removeExpiredOnce(task);
                    saveToFile();
                }
                return true;
            }
        }
        return false;
    }

    /** 恢复全部计划,返回实际被恢复的数量。 */
    public static int resumeAll() {
        int count = 0;
        MinecraftServer srv = server;
        long now = System.currentTimeMillis();
        for (ScheduleTask task : schedules) {
            if (task.paused) {
                task.paused = false;
                initNextRun(task, now, srv == null ? 0 : srv.getTickCount());
                removeExpiredOnce(task);
                count++;
            }
        }
        if (count > 0) {
            saveToFile();
        }
        return count;
    }

    /** 一次性任务的时间在暂停期间已过则移除。 */
    private static void removeExpiredOnce(ScheduleTask task) {
        if (task.type == ScheduleType.REALTIME_ONCE && task.nextRunAtMillis <= System.currentTimeMillis()) {
            schedules.remove(task);
        }
    }

    /** 从本地文件重新加载并重算运行时状态。 */
    public static boolean reload() {
        if (!loadFromFile()) {
            return false;
        }
        MinecraftServer srv = server;
        long now = System.currentTimeMillis();
        for (ScheduleTask task : schedules) {
            initNextRun(task, now, srv == null ? 0 : srv.getTickCount());
        }
        return true;
    }
}
