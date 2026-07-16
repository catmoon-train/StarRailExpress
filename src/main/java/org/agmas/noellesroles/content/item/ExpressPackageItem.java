package org.agmas.noellesroles.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * 快递包裹 - 可放入邮箱，右键打开1格存储空间（使用箱子菜单限制1行1列）
 */
public class ExpressPackageItem extends Item {

    public ExpressPackageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity) {
            return InteractionResult.PASS;
        }
        // 右键其他方块也打开包裹
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer sp) {
            openPackageScreen(sp, context.getItemInHand());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            openPackageScreen(sp, stack);
        }
        return InteractionResultHolder.success(stack);
    }

    private void openPackageScreen(ServerPlayer player, ItemStack packageStack) {
        var contents = ExpressPackageContainer.create(packageStack);
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ChestMenu(
                        net.minecraft.world.inventory.MenuType.GENERIC_9x1, id, inv, contents, 1),
                Component.translatable("container.noellesroles.express_package")));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds_express_package"));
        CompoundTag contents = getContents(stack);
        if (!contents.isEmpty()) {
            ItemStack inner = ItemStack.parseOptional(context.registries(), contents);
            if (!inner.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.noellesroles.express_package_contains",
                        inner.getHoverName().getString(), inner.getCount()));
            }
        }
    }

    /** 设置包裹内的物品 */
    public static void setContent(ItemStack stack, ItemStack content) {
        CompoundTag tag = new CompoundTag();
        if (!content.isEmpty()) {
            tag = content.saveOptional();
        }
        CompoundTag data = new CompoundTag();
        data.put("PackageContent", tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    /** 获取包裹内的物品 */
    public static CompoundTag getContents(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            return customData.copyTag().getCompound("PackageContent");
        }
        return new CompoundTag();
    }

    /** 提取包裹内物品 */
    public static ItemStack extractContent(ItemStack stack, ServerLevel level) {
        CompoundTag tag = getContents(stack);
        if (!tag.isEmpty()) {
            ItemStack content = ItemStack.parseOptional(level.registryAccess(), tag);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
            return content;
        }
        return ItemStack.EMPTY;
    }
}
