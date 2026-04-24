package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.block_entity.CameraBlockEntity;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * 绳索
 * <p>
 * - 2点耐久
 * - 右键：将前方直线距离12格内你瞄准的玩家拉到自己身前
 * - 每次右键后进入3秒冷却，成功拉取且非创造模式时进入5秒冷却并消耗1点耐久
 * </p>
 */
public class CameraShearsItem extends Item implements AdventureUsable {
    private static final int USE_COOLDOWN = 30 * 20; // 成功且非创造模式5秒

    public CameraShearsItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(this))
            return InteractionResult.PASS;
        BlockPos clickedPos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(clickedPos);
        if (!(be instanceof CameraBlockEntity)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        stack.hurtAndBreak(1, player, context.getHand().equals(InteractionHand.MAIN_HAND)?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND);
        if(stack.getDamageValue()<stack.getMaxDamage()){
            if(!player.isCreative()){
                player.getCooldowns().addCooldown(this, USE_COOLDOWN);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.noellesroles.camera_shears.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
