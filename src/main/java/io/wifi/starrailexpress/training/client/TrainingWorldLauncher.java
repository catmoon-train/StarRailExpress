package io.wifi.starrailexpress.training.client;

import io.wifi.starrailexpress.training.TrainingWorldInstaller;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.ContentValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 只通过原版断开、存档锁、备份及打开世界流程进入本地训练场。 */
public final class TrainingWorldLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrainingWorldLauncher.class);
    private static boolean busy;
    private static boolean opening;

    private TrainingWorldLauncher() {
    }

    /** 世界加载也算忙碌；原版成功加入后无需额外注册事件或依赖 TrainingClient 初始化。 */
    public static boolean isBusy() {
        Minecraft client = Minecraft.getInstance();
        if (opening && client.level != null && client.player != null) {
            busy = false;
            opening = false;
        }
        return busy;
    }

    public static void open(Screen parent) {
        if (isBusy()) {
            return;
        }
        if (TrainingClient.isTraining()) {
            TrainingClient.openPanel(parent);
            return;
        }
        request(parent, false);
    }

    /** 两次确认后才退出、原版备份、重建；备份异常绝不调用安装器重建。 */
    public static void rebuild(Screen parent) {
        if (TrainingClient.isDedicated()) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) client.player.displayClientMessage(Component.translatable("training.sre.ui.server_rebuild"), false);
            return;
        }
        if (!isBusy()) {
            request(parent, true);
        }
    }

    private static void request(Screen parent, boolean rebuild) {
        Minecraft client = Minecraft.getInstance();
        if (!FabricLoader.getInstance().isModLoaded("carpet")) {
            client.setScreen(new AlertScreen(() -> client.setScreen(parent),
                    text("missing_carpet.title"), text("missing_carpet.message")));
            return;
        }
        busy = true;
        if (rebuild) {
            client.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    client.setScreen(new ConfirmScreen(second -> {
                        if (second) {
                            start(parent, true);
                        } else {
                            cancel(parent);
                        }
                    }, text("rebuild.title"), exitMessage(client, true),
                            text("rebuild.confirm"), Component.translatable("gui.cancel")));
                } else {
                    cancel(parent);
                }
            }, text("rebuild.title"), text("rebuild.warning"),
                    Component.translatable("gui.proceed"), Component.translatable("gui.cancel")));
        } else if (client.level != null || client.getConnection() != null) {
            client.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    start(parent, false);
                } else {
                    cancel(parent);
                }
            }, text("open.title"), exitMessage(client, false),
                    Component.translatable("gui.proceed"), Component.translatable("gui.cancel")));
        } else {
            start(parent, false);
        }
    }

    private static Component exitMessage(Minecraft client, boolean rebuild) {
        String suffix = client.hasSingleplayerServer() ? "singleplayer"
                : client.level != null || client.getConnection() != null ? "multiplayer" : "offline";
        return text((rebuild ? "rebuild." : "open.") + suffix);
    }

    private static void cancel(Screen parent) {
        busy = false;
        opening = false;
        Minecraft.getInstance().setScreen(parent);
    }

    private static void start(Screen parent, boolean rebuild) {
        Minecraft client = Minecraft.getInstance();
        LevelStorageSource storage = client.getLevelSource();
        Path currentTraining = TrainingClient.isTraining() && client.getSingleplayerServer() != null
                ? client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                : null;
        Screen returnScreen = client.level == null && client.getConnection() == null ? parent : new TitleScreen();
        GenericMessageScreen progress = new GenericMessageScreen(text(rebuild ? "backing_up" : "preparing"));
        client.setScreen(progress);
        try {
            // 与原版暂停菜单一样，先通知连接退出，再等待集成服务器正常保存并停止。
            if (client.level != null) {
                client.level.disconnect();
            }
            client.disconnect(progress);
            TrainingClient.reset();
        } catch (RuntimeException failure) {
            fail(returnScreen, "error.message", failure);
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                Path world = currentTraining != null ? currentTraining
                        : TrainingWorldInstaller.installOrFind(storage.getBaseDir());
                if (rebuild) {
                    backupAndRebuild(storage, world);
                }
                return world;
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }).whenComplete((world, failure) -> client.execute(() -> {
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause() : failure;
                fail(returnScreen, cause instanceof BackupFailure ? "error.backup" : "error.message", cause);
                return;
            }
            client.setScreen(new GenericMessageScreen(text("loading")));
            opening = true;
            try {
                // 原版负责锁定、DataFixer、资源包确认、加载进度以及损坏存档提示。
                client.createWorldOpenFlows().openWorld(world.getFileName().toString(), () -> cancel(returnScreen));
            } catch (RuntimeException loadFailure) {
                fail(returnScreen, "error.message", loadFailure);
            }
        }));
    }

    private static void backupAndRebuild(LevelStorageSource storage, Path world) throws IOException {
        Path saves = storage.getBaseDir().toAbsolutePath().normalize();
        if (!saves.equals(world.getParent()) || !TrainingWorldInstaller.isTrainingWorld(world)) {
            throw new IOException("Refusing to back up or rebuild an unrelated save: " + world);
        }
        try (var access = storage.validateAndCreateAccess(world.getFileName().toString())) {
            if (access.makeWorldBackup() <= 0) {
                throw new IOException("Minecraft produced an empty backup");
            }
        } catch (IOException | ContentValidationException failure) {
            throw new BackupFailure(failure);
        }
        // 必须在备份成功且原版存档锁关闭之后调用；保留 training-settings.json。
        TrainingWorldInstaller.rebuild(world);
    }

    private static void fail(Screen parent, String message, Throwable failure) {
        LOGGER.error("Cannot open/rebuild the training world", failure);
        busy = false;
        opening = false;
        Minecraft client = Minecraft.getInstance();
        String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        client.setScreen(new AlertScreen(() -> client.setScreen(parent), text("error.title"),
                Component.translatable("training.sre." + message, detail)));
    }

    private static Component text(String key) {
        return Component.translatable("training.sre." + key);
    }

    private static final class BackupFailure extends IOException {
        BackupFailure(Exception cause) {
            super(cause.getMessage(), cause);
        }
    }
}
