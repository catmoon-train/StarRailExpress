package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.agmas.noellesroles.content.block_entity.RepairStationBlockEntity;
import net.exmo.sre.repair.state.RepairModeState;

import java.util.List;

public class RepairBoostItem extends Item {
    private final int boost;
    private final String tooltipKey;

    public RepairBoostItem(int boost, String tooltipKey, Properties properties) {
        super(properties);
        this.boost = boost;
        this.tooltipKey = tooltipKey;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        if (!(context.getLevel().getBlockEntity(pos) instanceof RepairStationBlockEntity station)) {
            return InteractionResult.PASS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player) || !RepairModeState.canUseSurvivorUtility(player)) {
            return InteractionResult.FAIL;
        }
        if (station.addProgress(boost)) {
            if (context.getLevel() instanceof ServerLevel level) {
                level.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 0.75F, 1.1F);
            }
            RepairModeState.awardCoins(player, 12, "repair_coin_source.boost");
            int boostPercent = Math.max(1, (int) Math.round(boost * 100.0 / RepairModeState.REPAIR_STATION_MAX_PROGRESS));
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.boosted", boostPercent,
                    station.getProgressPercent()), true);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.coin_reward", 12)
                    .withStyle(ChatFormatting.GOLD), true);
            RepairModeState.addNeutralTaskProgress(player, "collector", 1, RepairModeState.COLLECTOR_TASK_NEEDED);
            if ("mechanic".equals(org.agmas.noellesroles.component.ModComponents.REPAIR_ROLES.get(player).activeRole)) {
                RepairModeState.startSkillCooldown(player, 20 * 28, "mechanic_overload");
            }
            if (!player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
    }
}
