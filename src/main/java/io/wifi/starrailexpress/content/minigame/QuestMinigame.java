package io.wifi.starrailexpress.content.minigame;

import net.minecraft.network.chat.Component;

/**
 * 小游戏任务点的小游戏接口（纯服务端安全）
 * 只存储小游戏的 ID 和显示名称，不引用任何客户端类
 */
public record QuestMinigame(String id, Component displayName) {

    public static QuestMinigame of(String id, String translationKey) {
        return new QuestMinigame(id, Component.translatable(translationKey));
    }
}
