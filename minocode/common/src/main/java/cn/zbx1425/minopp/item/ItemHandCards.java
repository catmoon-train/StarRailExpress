package cn.zbx1425.minopp.item;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.block.BlockMinoTable;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.platform.GroupedItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ItemHandCards extends GroupedItem {
    
    public ItemHandCards() {
        super(() -> null, p -> p.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (usedHand != InteractionHand.MAIN_HAND) return super.use(level, player, usedHand);
        BlockPos gamePos = getHandCardGamePos(player);
        if (gamePos != null && level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity) {
            if (tableEntity.game != null && tableEntity.getPlayersList().stream().anyMatch(p -> p.uuid.equals(player.getGameProfile().getId()))) {
                // Card is valid
                return InteractionResultHolder.fail(player.getItemInHand(usedHand));
            }
        }
        // Game table not found, card no longer usable
        player.setItemInHand(usedHand, ItemStack.EMPTY);
        return InteractionResultHolder.consume(player.getItemInHand(usedHand));
    }

    public static CardPlayer getCardPlayer(Player player) {
        return new CardPlayer(player);
    }

    public static BlockPos getHandCardGamePos(Player player) {
        if (!player.getMainHandItem().is(Mino.ITEM_HAND_CARDS.get())) return null;
        CardGameBindingComponent binding = player.getMainHandItem().get(Mino.DATA_COMPONENT_TYPE_CARD_GAME_BINDING.get());
        if (binding == null) return null;
        BlockPos tablePos = binding.tablePos();
        BlockState blockState = player.level().getBlockState(tablePos);
        if (!blockState.is(Mino.BLOCK_MINO_TABLE.get())) return null;
        return BlockMinoTable.getCore(blockState, tablePos);
    }

    public static int getClientHandIndex(Player player) {
        if (player.getMainHandItem().is(Mino.ITEM_HAND_CARDS.get())) {
            return player.getMainHandItem().getOrDefault(Mino.DATA_COMPONENT_TYPE_CLIENT_HAND_INDEX.get(), 0);
        } else {
            return 0;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CardGameBindingComponent binding = stack.get(Mino.DATA_COMPONENT_TYPE_CARD_GAME_BINDING.get());
        if (binding != null) {
            tooltipComponents.add(Component.literal("Table: " + binding.tablePos().toShortString()));
            if (binding.bearerId().equals(Minecraft.getInstance().player.getGameProfile().getId())) {
                tooltipComponents.add(Component.literal("NOT YOUR CARD!").withStyle(ChatFormatting.RED));
            }
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    public record CardGameBindingComponent(BlockPos tablePos, UUID bearerId) {
        // BearerId is to convey holder info into BEWLR

        public static final Codec<CardGameBindingComponent> CODEC = RecordCodecBuilder.create(it -> it.group(
                BlockPos.CODEC.fieldOf("tablePos").orElse(BlockPos.ZERO).forGetter(CardGameBindingComponent::tablePos),
                UUIDUtil.CODEC.fieldOf("bearerId").orElse(Util.NIL_UUID).forGetter(CardGameBindingComponent::bearerId)
        ).apply(it, CardGameBindingComponent::new));

        public static final StreamCodec<ByteBuf, CardGameBindingComponent> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, CardGameBindingComponent::tablePos,
                UUIDUtil.STREAM_CODEC, CardGameBindingComponent::bearerId,
                CardGameBindingComponent::new
        );
    }
}
