package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.role_data.vigilante.DictatorRoleData;

import java.util.List;

/**
 * 裁决之剑 —— 独裁者专属一次性道具。
 *
 * <p>
 * 手持右键尸体 → 打开「选择死因」界面（可搜索，参考推理师罗盘）→
 * 选完死因后打开「选择凶手」玩家头像界面 → 提交后由服务端校验：
 * <ul>
 * <li>死因与凶手全部正确 → 凶手位置劈下一道闪电，凶手死亡（死因「裁断」）；</li>
 * <li>凶手已死亡 → actionbar 提示，不触发闪电和死亡；</li>
 * <li>无论正确与否，裁决之剑都会消耗。</li>
 * </ul>
 *
 * <p>
 * 右键尸体时服务端会把该尸体记进 {@link DictatorRoleData#pendingBodyUuid}，
 * 提交（{@code DictatorJudgeC2SPacket}）时用它做权威校验。
 */
public class JudgmentSwordItem extends Item {

    /** 纯客户端回调：由客户端在初始化时赋值（对准尸体才打开选择界面） */
    public static Runnable openScreenCallback = null;

    public JudgmentSwordItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide()) {
            if (openScreenCallback != null) {
                openScreenCallback.run();
            }
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }
        PlayerBodyEntity body = findTargetBody(serverPlayer);
        if (body == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.dictator.no_body").withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }
        DictatorRoleData data = RoleData.getNullable(DictatorRoleData.class, serverPlayer);
        if (data != null) {
            data.pendingBodyUuid = body.getUUID();
        }
        return InteractionResultHolder.success(stack);
    }

    /** 准星方向寻找玩家尸体（与占卜家晶球同款射线检测）。 */
    public static PlayerBodyEntity findTargetBody(Player player) {
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player,
                e -> e instanceof PlayerBodyEntity, 3.5D);
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
