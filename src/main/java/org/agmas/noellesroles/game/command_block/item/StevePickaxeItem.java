package org.agmas.noellesroles.game.command_block.item;

import java.util.List;

import org.agmas.noellesroles.game.command_block.SteveMiner;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Steve 的任务点采矿工具；不使用原版 Tier，避免其构造器覆盖有限耐久。 */
public class StevePickaxeItem extends Item {
    private final int miningTicks;

    public StevePickaxeItem(Properties properties) {
        this(properties, 20);
    }

    public StevePickaxeItem(Properties properties, int miningTicks) {
        super(properties);
        this.miningTicks = miningTicks;
    }

    public int miningTicks() {
        return miningTicks;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return SteveMiner.canMine(player, pos);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.steve_pickaxe.tooltip",
                miningTicks / 20.0F).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.steve_pickaxe.durability",
                Math.max(0, stack.getMaxDamage() - stack.getDamageValue()), stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
