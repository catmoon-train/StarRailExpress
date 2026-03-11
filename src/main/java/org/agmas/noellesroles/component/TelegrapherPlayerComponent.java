package org.agmas.noellesroles.component;

import net.minecraft.world.entity.player.Player;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.GameWorldComponent;

import java.util.Random;

/**
 * 电报员组件
 *
 * 功能：
 * - 存储剩余使用次数
 * - 管理匿名消息发送
 * - 向所有玩家发送匿名消息（使用Title显示）
 */
public class TelegrapherPlayerComponent implements RoleComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<TelegrapherPlayerComponent> KEY = ModComponents.TELEGRAPHER;

    // 最大使用次数
    public static final int MAX_USES = 6;

    private final Player player;

    // 剩余使用次数
    public int remainingUses = MAX_USES;

    public TelegrapherPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void reset() {
        this.remainingUses = MAX_USES;
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
    }

    /**
     * 检查是否还有剩余次数
     */
    public boolean hasUsesRemaining() {
        return remainingUses > 0;
    }

    /**
     * 使用一次能力
     * 
     * @return true 如果成功使用
     */
    public boolean useAbility() {
        if (!hasUsesRemaining()) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.telegrapher.no_uses")
                                .withStyle(ChatFormatting.RED),
                        false);
            }
            return false;
        }

        remainingUses--;
        this.sync();
        return true;
    }

    /**
     * 发送匿名消息给所有玩家（使用Title显示）
     * 
     * @param message 要发送的消息
     */
    public void sendAnonymousMessage(String message) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 检查是否还有使用次数
        if (!useAbility()) {
            return;
        }

        // 创建Title文本 - 淡蓝色显示消息内容
        Component titleText = Component.literal(message).withStyle(ChatFormatting.AQUA);

        // 向所有玩家显示Title（包括生存模式玩家）
        for (ServerPlayer targetPlayer : serverPlayer.getServer().getPlayerList().getPlayers()) {
            // 只使用主标题显示消息，淡蓝色
            // 参数：fadeIn(淡入), stay(停留), fadeOut(淡出) - 单位：tick
            targetPlayer.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(titleText));
            targetPlayer.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 60, 10));
        }

        // 向发送者确认
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.telegrapher.sent", remainingUses)
                        .withStyle(ChatFormatting.GREEN),
                false);
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("remaining_uses", this.remainingUses);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.remainingUses = tag.getInt("remaining_uses");
    }
}
