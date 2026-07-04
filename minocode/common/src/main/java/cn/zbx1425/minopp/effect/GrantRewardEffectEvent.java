package cn.zbx1425.minopp.effect;

import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public record GrantRewardEffectEvent(UUID targetPlayer) implements EffectEvent{

    public static StreamCodec<ByteBuf, GrantRewardEffectEvent> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, GrantRewardEffectEvent::targetPlayer,
            GrantRewardEffectEvent::new
    );

    @Override
    public Type<? extends EffectEvent> type() {
        return EffectEvents.GRANT_REWARD;
    }

    @Override
    public int timeOffset() {
        return 0;
    }

    @Override
    public Optional<UUID> target() {
        return Optional.empty();
    }

    @Override
    public void summonClient(Level level, BlockPos origin, boolean selfPartOfSourceGame) {

    }

    @Override
    public void summonServer(ServerLevel level, BlockPos origin, BlockEntityMinoTable tableEntity) {
        ItemStack stack = tableEntity.award.copy();
        if (stack.isEmpty()) return;
        ServerPlayer player = (ServerPlayer) level.getPlayerByUUID(targetPlayer);
        if (player != null) {
            boolean addSuccessful = player.getInventory().add(stack);
            if (!addSuccessful) {
                ItemEntity itemEntity = player.drop(stack, false);
                if (itemEntity != null) {
                    itemEntity.setNoPickUpDelay();
                    itemEntity.setTarget(player.getUUID());
                }
            }
        }
    }
}
