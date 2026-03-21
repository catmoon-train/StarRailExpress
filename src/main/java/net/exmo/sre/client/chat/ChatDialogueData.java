package net.exmo.sre.client.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天对话数据模型。
 * <p>
 * 对应服务端 JSON 配置文件，格式示例：
 * <pre>
 * {
 *   "id": "welcome_npc",
 *   "title": "旅行者的问候",
 *   "lines": [
 *     { "speaker": "帕姆", "text": "欢迎乘坐星穹列车！", "color": "#FFD700", "command": "" },
 *     {
 *       "speaker": "玩家",
 *       "text": "我接下来该做什么？",
 *       "color": "#FFFFFF",
 *       "choices": [
 *         { "text": "介绍一下列车", "nextDialogue": "train_intro" },
 *         { "text": "先不聊了", "command": "say 玩家决定稍后再来", "nextLine": -1 }
 *       ]
 *     },
 *     { "speaker": "帕姆", "text": "需要我为您做些什么吗？", "color": "#FFD700", "command": "say 帕姆向你挥手" }
 *   ]
 * }
 * </pre>
 */
public class ChatDialogueData {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 对话配置唯一标识符 */
    public String id = "";
    /** 对话标题（显示在 UI 顶部） */
    public String title = "";
    /** 对话行列表 */
    public List<DialogueLine> lines = new ArrayList<>();

    public void normalize() {
        if (id == null) id = "";
        if (title == null) title = "";
        if (lines == null) {
            lines = new ArrayList<>();
            return;
        }

        List<DialogueLine> normalizedLines = new ArrayList<>(lines.size());
        for (DialogueLine line : lines) {
            if (line == null) continue;
            line.normalize();
            normalizedLines.add(line);
        }
        lines = normalizedLines;
    }

    public static class DialogueLine {
        /** 说话者名称 */
        public String speaker = "";
        /** 对话内容 */
        public String text = "";
        /** 文字颜色（十六进制如 "#FFD700"，留空则用默认色） */
        public String color = "";
        /** 推进到此行时执行的命令（留空则不执行） */
        public String command = "";
        /** 命令执行端：client/server，默认 client */
        public String commandSide = "client";
        /** 该行可选分支，存在时需要玩家做出选择 */
        public List<DialogueChoice> choices = new ArrayList<>();

        public DialogueLine() {}

        public DialogueLine(String speaker, String text, String color, String command) {
            this.speaker = speaker;
            this.text = text;
            this.color = color;
            this.command = command;
        }

        public DialogueLine(String speaker, String text, String color, String command, String commandSide) {
            this.speaker = speaker;
            this.text = text;
            this.color = color;
            this.command = command;
            this.commandSide = commandSide;
        }

        public DialogueLine(String speaker, String text, String color, String command,
                            List<DialogueChoice> choices) {
            this.speaker = speaker;
            this.text = text;
            this.color = color;
            this.command = command;
            this.choices = choices;
        }

        public void normalize() {
            if (speaker == null) speaker = "";
            if (text == null) text = "";
            if (color == null) color = "";
            if (command == null) command = "";
            if (command.startsWith("server:")) {
                command = command.substring("server:".length()).trim();
                commandSide = "server";
            } else if (command.startsWith("client:")) {
                command = command.substring("client:".length()).trim();
                commandSide = "client";
            }
            if (commandSide == null || commandSide.isEmpty()) {
                commandSide = "client";
            } else {
                commandSide = commandSide.toLowerCase();
                if (!commandSide.equals("client") && !commandSide.equals("server")) {
                    commandSide = "client";
                }
            }
            if (choices == null) {
                choices = new ArrayList<>();
                return;
            }

            List<DialogueChoice> normalizedChoices = new ArrayList<>(choices.size());
            for (DialogueChoice choice : choices) {
                if (choice == null) continue;
                choice.normalize();
                normalizedChoices.add(choice);
            }
            choices = normalizedChoices;
        }

        public boolean hasChoices() {
            return choices != null && !choices.isEmpty();
        }

        public boolean runsOnServer() {
            return "server".equals(commandSide);
        }

        /** 解析颜色字符串为 ARGB int，失败时返回默认白色 */
        public int parseColor() {
            if (color == null || color.isEmpty()) return 0xFFFFFF;
            try {
                String hex = color.startsWith("#") ? color.substring(1) : color;
                return Integer.parseUnsignedInt(hex, 16) & 0xFFFFFF;
            } catch (NumberFormatException e) {
                return 0xFFFFFF;
            }
        }
    }

    public static class DialogueChoice {
        /** 选项文本 */
        public String text = "";
        /** 选择该项时执行的命令 */
        public String command = "";
        /** 命令执行端：client/server，默认 client */
        public String commandSide = "client";
        /** 选择该项后打开的新对话 ID；留空则不切换到新对话 */
        public String nextDialogue = "";
        /** 选择该项后跳转到当前对话中的行号；-1 表示不跳转 */
        public int nextLine = -1;

        public DialogueChoice() {}

        public DialogueChoice(String text, String command, String nextDialogue, int nextLine) {
            this.text = text;
            this.command = command;
            this.nextDialogue = nextDialogue;
            this.nextLine = nextLine;
        }

        public DialogueChoice(String text, String command, String commandSide, String nextDialogue, int nextLine) {
            this.text = text;
            this.command = command;
            this.commandSide = commandSide;
            this.nextDialogue = nextDialogue;
            this.nextLine = nextLine;
        }

        public void normalize() {
            if (text == null) text = "";
            if (command == null) command = "";
            if (command.startsWith("server:")) {
                command = command.substring("server:".length()).trim();
                commandSide = "server";
            } else if (command.startsWith("client:")) {
                command = command.substring("client:".length()).trim();
                commandSide = "client";
            }
            if (commandSide == null || commandSide.isEmpty()) {
                commandSide = "client";
            } else {
                commandSide = commandSide.toLowerCase();
                if (!commandSide.equals("client") && !commandSide.equals("server")) {
                    commandSide = "client";
                }
            }
            if (nextDialogue == null) nextDialogue = "";
            if (nextLine < -1) nextLine = -1;
        }

        public boolean opensDialogue() {
            return nextDialogue != null && !nextDialogue.isEmpty();
        }

        public boolean runsOnServer() {
            return "server".equals(commandSide);
        }
    }
}
