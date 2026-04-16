package org.agmas.noellesroles.roles.fool;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.entity.NoteEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

import java.util.List;

/**
 * 祷告管理器 - 处理玩家对尊名纸条的祷告（按V键）
 */
public class PrayerHandler {

    /** 祷告读条时间（tick） */
    public static final int PRAYER_DURATION_TICKS = 3 * 20; // 3秒

    /** 纸条检测距离 */
    public static final double NOTE_DETECTION_RANGE = 5.0;

    /**
     * 玩家按V键开始祷告
     */
    public static void startPrayer(ServerPlayer player) {
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(player.level());
        ServerLevel serverLevel = (ServerLevel) player.level();
        ServerPlayer fool = TarotAssemblyManager.findFoolPlayer(serverLevel, gameComponent);
        FoolPlayerComponent foolComp = fool != null ? FoolPlayerComponent.KEY.get(fool) : null;

        // 愚者自己不需要祷告
        if (gameComponent.isRole(player, ModRoles.THE_FOOL)) {
            FoolPlayerComponent selfFoolComp = FoolPlayerComponent.KEY.get(player);
            if (selfFoolComp.inMeeting) {
                TarotAssemblyManager.requestVoteScreen(player);
            }
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player))return;

        // 已经是成员时，会议中的 J 优先用于入会/打开投票页，不再要求附近必须有尊名纸条
        if (foolComp != null && foolComp.isTarotMember(player.getUUID())) {
            if (foolComp.inMeeting) {
                if (player.hasEffect(ModEffects.TAROT_ASSEMBLY)) {
                    TarotAssemblyManager.requestVoteScreen(player);
                } else {
                    TarotAssemblyManager.memberJoinMeeting(player);
                }
            } else {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.fool.already_member")
                                .withStyle(ChatFormatting.YELLOW),
                        true);
            }
            return;
        }

        // 检查玩家附近是否有尊名纸条
        if (!isNearHonoredNote(player)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.fool.no_note_nearby")
                            .withStyle(ChatFormatting.GRAY),
                    true);
            return;
        }

        // 检查是否已经是塔罗会成员
        // 开始祷告读条（使用服务端tick计数）
        // 注意：在简化实现中，我们直接完成祷告而不实现3秒读条
        // 完整实现中应使用组件的isPraying状态 + serverTick检查
        completePrayer(player);
    }

    /**
     * 完成祷告
     */
    public static void completePrayer(ServerPlayer player) {
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(player.level());
        ServerLevel serverLevel = (ServerLevel) player.level();

        ServerPlayer fool = TarotAssemblyManager.findFoolPlayer(serverLevel, gameComponent);
        if (fool == null) return;

        FoolPlayerComponent foolComp = FoolPlayerComponent.KEY.get(fool);

        // 检查是否已达到最大成员数
        if (!foolComp.addTarotMember(player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.fool.tarot_members_full",
                            FoolPlayerComponent.MAX_TAROT_MEMBERS)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 通知玩家
        player.displayClientMessage(
                Component.translatable("message.noellesroles.fool.prayer_complete")
                        .withStyle(ChatFormatting.GOLD),
                true);

        // 通知愚者
        fool.displayClientMessage(
                Component.translatable("message.noellesroles.fool.player_prayed", player.getName().getString())
                        .withStyle(ChatFormatting.GOLD),
                true);
    }

    /**
     * 检查玩家附近是否有尊名纸条实体
     */
    public static boolean isNearHonoredNote(ServerPlayer player) {
        Vec3 playerPos = player.position();
        AABB searchBox = new AABB(
                playerPos.x - NOTE_DETECTION_RANGE, playerPos.y - NOTE_DETECTION_RANGE,
                playerPos.z - NOTE_DETECTION_RANGE,
                playerPos.x + NOTE_DETECTION_RANGE, playerPos.y + NOTE_DETECTION_RANGE,
                playerPos.z + NOTE_DETECTION_RANGE);

        List<HonoredNoteItem.HonoredNoteEntity> entities = player.level().getEntitiesOfClass(HonoredNoteItem.HonoredNoteEntity.class, searchBox,
                entity -> true);

        for (NoteEntity noteEntity : entities) {
            double distance = player.distanceTo(noteEntity);
            if (distance <= NOTE_DETECTION_RANGE) {
                // 检查视线无障碍
                if (hasLineOfSight(player, noteEntity)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查视线是否无障碍
     */
    private static boolean hasLineOfSight(ServerPlayer player, Entity target) {
        Vec3 playerEye = player.getEyePosition();
        Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2.0, 0);

        // 使用简单的射线检测
        return player.level().clip(new net.minecraft.world.level.ClipContext(
                playerEye, targetPos,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }
}
