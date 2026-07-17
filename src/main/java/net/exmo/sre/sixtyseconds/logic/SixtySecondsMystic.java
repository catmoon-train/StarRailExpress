package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.ModItems;

import java.util.UUID;

/**
 * 神秘技术（祭坛产物）的机制：
 * <ul>
 *   <li><b>污秽玻璃罐</b>：背包里带着它处决一个倒地玩家 → 变成<b>存血的玻璃罐</b>
 *       （复活图腾的核心材料；见 {@link #onExecuteDowned}，由 HealthSystem 处决路径调用）。</li>
 *   <li><b>不死图腾</b>（原版图腾）：背包里有图腾时本应死亡 → 消耗图腾免死，
 *       健康回 {@link #TOTEM_HEALTH}（见 {@link #tryUndyingTotem}，die() 前拦截）。</li>
 *   <li><b>复活图腾</b>：手持右键<b>本队队友的尸体</b> → 消耗图腾把队友复活在尸体旁
 *       （退出死亡频道，走 {@link GameUtils#revivePlayer}）。</li>
 * </ul>
 */
public final class SixtySecondsMystic {

    /** 不死图腾触发后的健康值。 */
    public static final int TOTEM_HEALTH = 50;
    /** 复活后的初始健康值。 */
    public static final int REVIVE_HEALTH = 50;

    private SixtySecondsMystic() {
    }

    public static void register() {
        // 复活图腾：右键本队队友尸体复活
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer reviver)
                    || !(entity instanceof PlayerBodyEntity body)
                    || !SixtySecondsMod.isActive(level)) {
                return InteractionResult.PASS;
            }
            ItemStack held = reviver.getItemInHand(hand);
            if (!held.is(ModItems.SIXTY_SECONDS_REVIVAL_TOTEM)) {
                return InteractionResult.PASS;
            }
            return tryReviveCorpse(reviver, body, held) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        });
    }

    /** 处决倒地玩家后调用（HealthSystem）：把处决者背包里的一个污秽玻璃罐变成存血的玻璃罐。 */
    public static void onExecuteDowned(ServerPlayer executor) {
        for (int slot = 0; slot < executor.getInventory().getContainerSize(); slot++) {
            ItemStack stack = executor.getInventory().getItem(slot);
            if (stack.is(ModItems.SIXTY_SECONDS_FILTHY_JAR)) {
                stack.shrink(1);
                executor.getInventory().placeItemBackInInventory(
                        new ItemStack(ModItems.SIXTY_SECONDS_BLOOD_JAR));
                executor.playNotifySound(SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 0.6F);
                executor.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.filthy_jar_filled")
                        .withStyle(ChatFormatting.DARK_RED), false);
                return;
            }
        }
    }

    /**
     * 不死图腾：将死之际消耗背包内一个原版图腾免死。返回 true = 已拦截（不要继续死亡流程）。
     * 怪物玩家不适用。
     */
    public static boolean tryUndyingTotem(ServerPlayer victim, SixtySecondsStatsComponent stats) {
        if (stats.monster) {
            return false;
        }
        for (int slot = 0; slot < victim.getInventory().getContainerSize(); slot++) {
            ItemStack stack = victim.getInventory().getItem(slot);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                stack.shrink(1);
                stats.downed = false;
                stats.downedFromInjury = false;
                stats.downedCountToday = 0;
                stats.recovering = false;
                stats.health = TOTEM_HEALTH;
                stats.sync();
                victim.setSwimming(false);
                victim.removeEffect(org.agmas.noellesroles.init.ModEffects.MOVE_BANED);
                victim.removeEffect(org.agmas.noellesroles.init.ModEffects.USED_BANED);
                ServerLevel level = victim.serverLevel();
                // 原版图腾表现：全屏动画 + 音效 + 粒子
                level.broadcastEntityEvent(victim, (byte) 35);
                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                victim.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.totem_saved")
                        .withStyle(ChatFormatting.GOLD), false);
                return true;
            }
        }
        return false;
    }

    /** 复活图腾右键尸体：校验同队 + 死者在线 → 消耗图腾复活。 */
    private static boolean tryReviveCorpse(ServerPlayer reviver, PlayerBodyEntity body, ItemStack totem) {
        UUID deadId = body.getPlayerUuid();
        ServerLevel level = reviver.serverLevel();
        if (deadId == null || !(level.getPlayerByUUID(deadId) instanceof ServerPlayer dead)) {
            reviver.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.revive_offline"), true);
            return false;
        }
        if (!GameUtils.isPlayerEliminated(dead)) {
            return false; // 还活着（观战外）——不消耗
        }
        int reviverTeam = SixtySecondsStatsComponent.KEY.get(reviver).teamId;
        int deadTeam = SixtySecondsStatsComponent.KEY.get(dead).teamId;
        if (reviverTeam != deadTeam) {
            reviver.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.revive_not_teammate"), true);
            return false;
        }
        if (!reviver.isCreative()) {
            totem.shrink(1);
        }
        // 备份尸体物品栏 → 复活后继承尸体物品，清空原有物品
        SimpleContainer corpseInv = body.getComponent().getCorpseInventory();
        ItemStack[] saved = new ItemStack[14]; // hotbar 0-8 + armor/offhand 9-13
        for (int i = 0; i < 14; i++) {
            saved[i] = corpseInv.getItem(i).copy();
        }
        dead.getInventory().clearContent();
        // 复活在尸体位置；重置 60s 状态到可行动水平
        GameUtils.revivePlayer(dead, body.getX(), body.getY(), body.getZ());
        // 把尸体物品写入复活玩家的背包
        for (int i = 0; i < 9; i++) {
            dead.getInventory().setItem(i, saved[i]);
        }
        dead.getInventory().setItem(39, saved[9]);   // head
        dead.getInventory().setItem(38, saved[10]);  // chest
        dead.getInventory().setItem(37, saved[11]);  // legs
        dead.getInventory().setItem(36, saved[12]);  // feet
        dead.getInventory().setItem(40, saved[13]);  // offhand
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(dead);
        stats.downed = false;
        stats.downedFromInjury = false;
        stats.downedCountToday = 0;
        stats.reviveEndTick = 0L;
        stats.sanZeroTick = 0L;
        stats.health = REVIVE_HEALTH;
        stats.sync();
        // 清空尸体物品栏防止掉落
        corpseInv.clearContent();
        body.discard();
        level.playSound(null, dead.getX(), dead.getY(), dead.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
        dead.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.revived")
                .withStyle(ChatFormatting.GOLD), false);
        return true;
    }
}
