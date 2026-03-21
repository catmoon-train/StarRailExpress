package net.exmo.sre.client.chat;

import com.google.gson.JsonSyntaxException;
import io.wifi.starrailexpress.SRE;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端聊天对话管理器。
 * <p>
 * 从 {@code <world>/chat_dialogues/} 目录加载所有 {@code .json} 文件，
 * 每个文件解析为一个 {@link ChatDialogueData}。
 */
public class ChatDialogueManager {

    private static final LevelResource DIALOGUE_DIR = new LevelResource("chat_dialogues");
    private final Map<String, ChatDialogueData> dialogues = new ConcurrentHashMap<>();
    private Path dialogueDir;

    private static ChatDialogueManager instance;

    public static ChatDialogueManager getInstance(MinecraftServer server) {
        if (instance == null) {
            instance = new ChatDialogueManager();
            instance.load(server);
        }
        return instance;
    }

    public static void reset() {
        instance = null;
    }

    /**
     * 重新加载所有对话配置。
     */
    public void load(MinecraftServer server) {
        dialogues.clear();
        dialogueDir = server.getWorldPath(DIALOGUE_DIR);

        if (!Files.isDirectory(dialogueDir)) {
            try {
                Files.createDirectories(dialogueDir);
                SRE.LOGGER.info("[SRE-Chat] Created chat_dialogues directory: {}", dialogueDir);
                // 首次创建时写入示例文件
                writeExampleFile();
            } catch (IOException e) {
                SRE.LOGGER.error("[SRE-Chat] Failed to create chat_dialogues directory", e);
                return;
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dialogueDir, "*.json")) {
            for (Path file : stream) {
                loadFile(file);
            }
        } catch (IOException e) {
            SRE.LOGGER.error("[SRE-Chat] Failed to list chat_dialogues directory", e);
        }

        validateBranchTargets();

        SRE.LOGGER.info("[SRE-Chat] Loaded {} dialogue(s)", dialogues.size());
    }

    private void loadFile(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            ChatDialogueData data = ChatDialogueData.GSON.fromJson(json, ChatDialogueData.class);
            if (data == null) {
                SRE.LOGGER.warn("[SRE-Chat] Skipping {} — empty or invalid JSON root", file.getFileName());
                return;
            }
            data.normalize();
            if (data.id == null || data.id.isEmpty()) {
                SRE.LOGGER.warn("[SRE-Chat] Skipping {} — missing 'id' field", file.getFileName());
                return;
            }
            dialogues.put(data.id, data);
        } catch (IOException e) {
            SRE.LOGGER.error("[SRE-Chat] Failed to read {}", file.getFileName(), e);
        } catch (JsonSyntaxException e) {
            SRE.LOGGER.error("[SRE-Chat] JSON syntax error in {}", file.getFileName(), e);
        }
    }

    /**
     * 写入示例对话文件。
     */
    private void writeExampleFile() {
        ChatDialogueData example = new ChatDialogueData();
        example.id = "example_welcome";
        example.title = "欢迎登车";
        example.lines.add(new ChatDialogueData.DialogueLine(
                "帕姆", "欢迎乘坐「星穹列车」！我是列车上的导航员帕姆~", "#FFD700", ""));
        example.lines.add(new ChatDialogueData.DialogueLine(
                "帕姆", "想先了解哪一部分？", "#FFD700", "",
                java.util.List.of(
                        new ChatDialogueData.DialogueChoice("介绍一下列车", "", "example_train_intro", -1),
                        new ChatDialogueData.DialogueChoice("先不聊了", "say 开拓者决定先四处看看", "", -1))));

        ChatDialogueData branch = new ChatDialogueData();
        branch.id = "example_train_intro";
        branch.title = "列车介绍";
        branch.lines.add(new ChatDialogueData.DialogueLine(
                "帕姆", "这里是大家休息和出发的地方，别忘了常回来看看哦。", "#FFD700", ""));
        branch.lines.add(new ChatDialogueData.DialogueLine(
                "系统", "教程：按下「回车」推进对话；遇到选项时可用上下键、数字键或鼠标选择。", "#88AACC", ""));

        example.normalize();
        branch.normalize();

        try {
            writeExampleDialogue(dialogueDir.resolve("example_welcome.json"), example);
            writeExampleDialogue(dialogueDir.resolve("example_train_intro.json"), branch);
        } catch (IOException e) {
            SRE.LOGGER.error("[SRE-Chat] Failed to write example", e);
        }
    }

    private void writeExampleDialogue(Path file, ChatDialogueData data) throws IOException {
        Files.writeString(file, ChatDialogueData.GSON.toJson(data), StandardCharsets.UTF_8);
        SRE.LOGGER.info("[SRE-Chat] Written example dialogue: {}", file);
    }

    private void validateBranchTargets() {
        for (ChatDialogueData dialogue : dialogues.values()) {
            for (int lineIndex = 0; lineIndex < dialogue.lines.size(); lineIndex++) {
                ChatDialogueData.DialogueLine line = dialogue.lines.get(lineIndex);
                if (!line.hasChoices()) continue;

                for (int choiceIndex = 0; choiceIndex < line.choices.size(); choiceIndex++) {
                    ChatDialogueData.DialogueChoice choice = line.choices.get(choiceIndex);
                    if (choice.opensDialogue() && !dialogues.containsKey(choice.nextDialogue)) {
                        SRE.LOGGER.warn(
                                "[SRE-Chat] Dialogue '{}' line {} choice {} points to missing dialogue '{}'",
                                dialogue.id, lineIndex, choiceIndex, choice.nextDialogue);
                    }
                }
            }
        }
    }

    public ChatDialogueData get(String id) {
        return dialogues.get(id);
    }

    public Map<String, ChatDialogueData> getAll() {
        return Collections.unmodifiableMap(dialogues);
    }
}
