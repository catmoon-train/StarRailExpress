package org.agmas.noellesroles.content.block;

import java.util.List;

import org.agmas.noellesroles.content.block_entity.SREPlushBlockEntity;
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import org.agmas.noellesroles.client.renderer.SREPlushBlockEntityRenderer;

/**
 * 自定义玩家 plush 方块（仅一个方块/物品实现）。
 * <p>
 * 物品上用 {@link DataComponents#PROFILE} 携带玩家名；放置时写入方块实体
 * {@link SREPlushBlockEntity#setOwner}。客户端渲染器据此Profile解析该玩家的皮肤，
 * 把皮肤贴到 plush 形状的模型上（见 {@link SREPlushBlockEntityRenderer} 的自定义分支）。
 * <p>
 * 渲染完全交给方块实体渲染器（{@link RenderShape#ENTITYBLOCK_ANIMATED}），因此外观随玩家皮肤即时变化，
 * 支持热加载——指令重新发放或改名即可换皮肤。
 */
public class CustomPlayerPlushBlock extends SREPlushBlock {
    private static final MapCodec<CustomPlayerPlushBlock> CODEC = simpleCodec(CustomPlayerPlushBlock::new);

    public CustomPlayerPlushBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // applyImplicitComponents 会读取，大概
        // if (!level.isClientSide && stack.has(DataComponents.PROFILE)
        // && level.getBlockEntity(pos) instanceof SREPlushBlockEntity plush) {
        // plush.setOwner(stack.get(DataComponents.PROFILE));
        // }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED; // 完全交给 BER
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (stack.get(SREDataComponentTypes.TEXTURE) != null) {
            var texture = stack.get(SREDataComponentTypes.TEXTURE);
            if (texture != null) {
                tooltip.add(Component.translatable("tooltip.sre.custom_player_plush.texture", texture)
                        .withStyle(ChatFormatting.GRAY));
            }
        } else if (stack.get(DataComponents.PROFILE) != null) {
            ResolvableProfile resolvableProfile = stack.get(DataComponents.PROFILE);
            var name = resolvableProfile.name().orElse(null);
            if (name != null && !name.isBlank()) {
                tooltip.add(Component.translatable("tooltip.sre.custom_player_plush.player", name)
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    public ResourceLocation getCustomSound(Level world, BlockPos pos, BlockState state) {
        if (world.getBlockEntity(pos) instanceof SREPlushBlockEntity sp) {
            return sp.getClickSound();
        }
        return null;
    }
}
