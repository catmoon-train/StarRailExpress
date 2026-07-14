package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 绳索：右键在<b>当前位置</b>放一条向上延伸的<b>临时可攀爬绳索</b>（缠怨藤柱，最多
 * {@link SixtySecondsBalance#ROPE_HEIGHT} 格、遇非空气截断），{@link SixtySecondsBalance#ROPE_DURATION_TICKS 30 秒}
 * 后自动消失（仅清除仍是藤的格子，不动玩家后放的方块）。消耗一根。快速上墙/上屋顶用——注意 30 秒后没了要另想下法。
 */
public class SixtySecondsRopeItem extends Item {

    /** 一条已放置的绳索：所在维度 + 各段位置 + 到期时间。 */
    private record Rope(ResourceKey<Level> dimension, List<BlockPos> segments, long expireTick) {
    }

    private static final List<Rope> ROPES = new ArrayList<>();

    public SixtySecondsRopeItem(Properties properties) {
        super(properties);
    }

    /** 模组初始化时注册一次：到期清除绳索段。 */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SixtySecondsRopeItem::tick);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.consume(stack);
        }
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        // 从玩家所在格向上放置缠怨藤（可攀爬），遇非空气截断
        BlockPos base = serverPlayer.blockPosition();
        List<BlockPos> segments = new ArrayList<>();
        for (int i = 0; i < SixtySecondsBalance.ROPE_HEIGHT; i++) {
            BlockPos pos = base.above(i);
            if (!serverLevel.getBlockState(pos).isAir()) {
                break;
            }
            serverLevel.setBlock(pos, Blocks.TWISTING_VINES.defaultBlockState(), Block.UPDATE_ALL);
            segments.add(pos.immutable());
        }
        if (segments.isEmpty()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.rope_blocked"), true);
            return InteractionResultHolder.consume(stack);
        }
        ROPES.add(new Rope(serverLevel.dimension(), segments,
                serverLevel.getGameTime() + SixtySecondsBalance.ROPE_DURATION_TICKS));
        if (!serverPlayer.isCreative()) {
            stack.shrink(1);
        }
        serverLevel.playSound(null, base, SoundEvents.WEEPING_VINES_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
        serverPlayer.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.rope_placed",
                segments.size(), SixtySecondsBalance.ROPE_DURATION_TICKS / 20), true);
        return InteractionResultHolder.consume(stack);
    }

    private static void tick(ServerLevel level) {
        if (ROPES.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Rope> it = ROPES.iterator(); it.hasNext();) {
            Rope rope = it.next();
            if (!rope.dimension().equals(level.dimension()) || now < rope.expireTick()) {
                continue;
            }
            // 从上往下清除仍是藤的格子（玩家后放的其他方块不动）
            for (int i = rope.segments().size() - 1; i >= 0; i--) {
                BlockPos pos = rope.segments().get(i);
                if (level.getBlockState(pos).is(Blocks.TWISTING_VINES)
                        || level.getBlockState(pos).is(Blocks.TWISTING_VINES_PLANT)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            level.playSound(null, rope.segments().get(0),
                    SoundEvents.WEEPING_VINES_BREAK, SoundSource.PLAYERS, 0.8F, 0.9F);
            it.remove();
        }
    }

    public static void reset() {
        ROPES.clear();
    }
}
