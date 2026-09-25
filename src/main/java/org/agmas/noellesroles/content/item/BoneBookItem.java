package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.game.roles.neutral.skeleton.SkeletonRole;

import java.util.List;

/**
 * 骸骨之书 —— 一次性道具：对着「玩家尸体」蓄力 1 秒释放，
 * 把该尸体对应的死亡玩家复活成「骷髅」职业（复活流程参考亡灵法师）。
 *
 * <p>
 * 使用方式：手持骸骨之书对准玩家尸体右键开始蓄力，蓄满 1 秒（20 tick）自动释放；
 * 中途松手则取消（不消耗）。若尸体对应的玩家不在线，则不消耗也不释放。
 *
 * <p>
 * 右键尸体本身会被原版走「实体交互」路径（尸体会打开自己的物品栏），
 * 所以这里用 {@code UseEntityCallback} 拦下这次交互：只要是手持骸骨之书对准尸体，
 * 就直接 {@code startUsingItem} 开始蓄力，并返回 CONSUME 阻止尸体界面打开
 * （写法与亡灵法师 {@code RevivalSelectionHandler} 一致）。
 */
public class BoneBookItem extends Item {

    /** 蓄力时长（tick）：1 秒。 */
    public static final int CHARGE_TICKS = 20;

    /** 蓄力释放时允许对准尸体的最远距离（格）。 */
    private static final double TARGET_RANGE = 3.0D;

    public BoneBookItem(Properties properties) {
        super(properties);
    }

    /**
     * 注册「对着玩家尸体右键 → 开始蓄力」的交互。
     * 由 {@code NRCombatEvents.registerWeaponHandlers()} 统一调用。
     */
    public static void registerEvents() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!(entity instanceof PlayerBodyEntity)) {
                return InteractionResult.PASS;
            }
            if (!(player.getItemInHand(hand).getItem() instanceof BoneBookItem)) {
                return InteractionResult.PASS;
            }
            // 活着且未在旁观才能用
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                player.startUsingItem(hand);
            }
            // 拦下这次交互，避免顺手打开尸体的物品栏
            return InteractionResult.CONSUME;
        });
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return CHARGE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // 用拉弓动作表现 1 秒蓄力进度
        return UseAnim.BOW;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (level.isClientSide() || !(user instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)) {
            return super.finishUsingItem(stack, level, user);
        }
        PlayerBodyEntity body = findTargetBody(player);
        if (body == null) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.bone_book.no_corpse")
                            .withStyle(ChatFormatting.RED),
                    true);
            return stack; // 不消耗
        }
        return SkeletonRole.reviveWithBoneBook(player, serverLevel, body, stack);
    }

    /** 准星方向寻找玩家尸体（服务端判定，占卜家的晶球同款写法）。 */
    public static PlayerBodyEntity findTargetBody(ServerPlayer player) {
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player,
                e -> e instanceof PlayerBodyEntity, TARGET_RANGE);
        return hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof PlayerBodyEntity body
                ? body
                : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
