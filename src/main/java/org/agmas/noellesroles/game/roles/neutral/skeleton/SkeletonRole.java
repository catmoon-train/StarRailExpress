package org.agmas.noellesroles.game.roles.neutral.skeleton;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.ExtraEffectRole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.event.AllowPlayerPunching;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 骷髅 —— PEAK 爬山图专属中立职业，只能通过「骸骨之书」把死亡的玩家复活而来。
 *
 * <p>
 * 特性一览（注册见 {@code ModRoles.SKELETON}）：
 * <ul>
 * <li>假心情、无限体力、无法打开本能透视；</li>
 * <li>常态效果：静音（别人听不到他说话）、禁用聊天栏（文字聊天别人看不到）、速度 1；</li>
 * <li>可以爬墙（复用童子军的攀爬系统，{@code setCanClimbWalls(true)}）；</li>
 * <li>下落超过 6 格直接摔死（见 {@code SkeletonRoleData}）；</li>
 * <li>空手左键攻击玩家：造成 1 点虚拟伤害（同 Dream 斧头的虚拟血量结算），
 * 被打死的玩家死因为「攻击」。</li>
 * </ul>
 *
 * <p>
 * 左键攻击逻辑复用红美玲（{@code HoanMeirinFistPunchHandler}）的写法：
 * {@code AllowPlayerPunching} 放行空手攻击 + {@code AttackEntityCallback} 命中结算。
 */
public class SkeletonRole extends ExtraEffectRole {

    /** 两次左键攻击之间的冷却（tick），与红美玲一致用屏障物品当通用冷却。 */
    public static final int PUNCH_COOLDOWN_TICKS = 10;

    /** 每次左键命中造成的虚拟伤害（同 Dream 斧头的虚拟血量结算）。 */
    public static final int PUNCH_VIRTUAL_DAMAGE = 1;

    public SkeletonRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /** 该玩家是否是骷髅。 */
    public static boolean isSkeleton(Player player) {
        if (player == null || player.level() == null || SRE.isLobby) {
            return false;
        }
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.SKELETON);
    }

    // ==================== 外观：始终伪装成原版骷髅 ====================

    /**
     * 骷髅外观：始终伪装成「我的世界原版骷髅」。
     *
     * <p>用法与紫怪一致（{@code EntityDisguise.disguise(target, TMMEntities.PURPLE_MONSTER)}），
     * 只是这里用的是原版 {@link EntityType#SKELETON}。开局 / 结束时核心会统一清空伪装，
     * 所以拿到职业时和每 tick 都要兜底补上（掉线重连会丢伪装状态）。
     */
    @Override
    public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {
        super.onInit(server, serverPlayer);
        applySkeletonDisguise(serverPlayer);
    }

    @Override
    public void onRemove(ServerPlayer player) {
        super.onRemove(player);
        // 职业被移除（换职业等）时解除伪装；局末清理由核心统一负责
        EntityDisguise.clear(player);
    }

    /** 未伪装时补上骷髅外观（已伪装成其它东西时不抢，交给调用方按需处理）。 */
    public static void applySkeletonDisguise(ServerPlayer player) {
        if (player == null || player.level() == null || player.level().isClientSide) {
            return;
        }
        if (!EntityDisguise.isDisguised(player)) {
            EntityDisguise.disguise(player, EntityType.SKELETON);
        }
    }

    /**
     * 注册战斗相关事件（由 {@code NRCombatEvents.registerWeaponHandlers()} 调用）。
     */
    public static void registerEvents() {
        // 放行骷髅的空手近战攻击
        AllowPlayerPunching.EVENT.register(player -> {
            if (player.hasEffect(ModEffects.SAFE_TIME)) {
                return false;
            }
            return isSkeleton(player) && player.getMainHandItem().isEmpty();
        });
        // 命中结算
        AttackEntityCallback.EVENT.register(SkeletonRole::onAttackEntity);
    }

    /**
     * 骷髅左键命中玩家：造成 1 点虚拟伤害（虚拟血量归零时按「攻击」死因判死）。
     *
     * <p>
     * 返回 {@code SUCCESS_NO_ITEM_USED} 取消原版伤害，与红美玲一致。
     */
    public static InteractionResult onAttackEntity(Player attacker, Level level, InteractionHand hand, Entity entity,
            EntityHitResult hitResult) {
        if (!GameUtils.isPlayerAliveAndSurvival(attacker)) {
            return InteractionResult.PASS;
        }
        if (!(entity instanceof Player victim)) {
            return InteractionResult.PASS;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(victim)) {
            return InteractionResult.PASS;
        }
        if (!SREGameWorldComponent.KEY.get(level).isRole(attacker, ModRoles.SKELETON)) {
            return InteractionResult.PASS;
        }
        if (victim.getCooldowns().isOnCooldown(Items.CLOCK)) {
            return InteractionResult.PASS; // 目标在安全时间
        }
        if (!attacker.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // 必须空手
        }
        if (attacker.getCooldowns().isOnCooldown(Items.BARRIER)) {
            return InteractionResult.PASS; // 攻击冷却中
        }
        if (attacker.getUUID().equals(victim.getUUID())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            // 与红美玲一致：客户端只取消原版攻击的预测，实际结算在服务端
            return InteractionResult.SUCCESS;
        }
        if (!(attacker instanceof ServerPlayer serverAttacker)) {
            return InteractionResult.PASS;
        }

        attacker.getCooldowns().addCooldown(Items.BARRIER, PUNCH_COOLDOWN_TICKS);

        // 1 点虚拟伤害：虚拟血量归零时由组件内部按传入的死因判死
        DreamHealthComponent.KEY.get(victim).hurt(serverAttacker, PUNCH_VIRTUAL_DAMAGE,
                GameConstants.DeathReasons.GENERAL_ATTACK);

        int remaining = DreamHealthComponent.KEY.get(victim).getEffectiveHealth(level.getGameTime());
        serverAttacker.displayClientMessage(Component.translatable("message.noellesroles.skeleton.hit",
                remaining, DreamHealthComponent.maxHealth()).withStyle(ChatFormatting.WHITE), true);

        return InteractionResult.SUCCESS_NO_ITEM_USED;
    }

    /**
     * 骸骨之书：把尸体对应的死亡玩家复活成骷髅（复活流程参考「亡灵法师」）。
     *
     * @return 处理后的物品堆（只在真正复活成功时消耗 1 个）
     */
    public static ItemStack reviveWithBoneBook(ServerPlayer user, ServerLevel level, PlayerBodyEntity body,
            ItemStack stack) {
        var playerUuid = body.getPlayerUuid();
        // 尸体对应的玩家不在线：不消耗也不释放
        if (playerUuid == null || !(level.getPlayerByUUID(playerUuid) instanceof ServerPlayer revived)) {
            user.displayClientMessage(
                    Component.translatable("message.noellesroles.bone_book.offline").withStyle(ChatFormatting.RED),
                    true);
            return stack;
        }
        // 葬仪伪造的尸体不能复活
        if (PlayerBodyEntityComponent.KEY.get(body).isFakeBody) {
            user.displayClientMessage(
                    Component.translatable("message.noellesroles.bone_book.fake_body")
                            .withStyle(ChatFormatting.RED),
                    true);
            return stack;
        }
        // 目标并不是死亡状态
        if (!revived.isSpectator()) {
            user.displayClientMessage(
                    Component.translatable("message.noellesroles.bone_book.not_dead").withStyle(ChatFormatting.RED),
                    true);
            return stack;
        }

        // 复活：全服播放不死图腾音效 + actionbar 通报
        level.players().forEach(a -> {
            a.playNotifySound(SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.2F, 1.5F);
            a.displayClientMessage(
                    Component.translatable("hud.noellesroles.bone_book.used").withStyle(ChatFormatting.DARK_RED),
                    true);
        });

        revived.getInventory().clearContent();
        GameUtils.revivePlayer(revived, body.getX(), body.getY(), body.getZ());
        RoleUtils.changeRole(revived, ModRoles.SKELETON);
        SRE.REPLAY_MANAGER.recordPlayerRevival(revived.getUUID(), ModRoles.SKELETON);
        SREPlayerShopComponent.KEY.get(revived).setBalance(200);
        body.remove(Entity.RemovalReason.DISCARDED);
        RoleUtils.sendWelcomeAnnouncement(revived);

        stack.consume(1, user);
        return stack;
    }
}
