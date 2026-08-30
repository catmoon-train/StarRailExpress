package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.LeftClickHurtable;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.content.entity.LockEntityManager;

/** 鸟兽兽的尼泊尔军刀：继承普通刀的攻击，额外复用撬棍开门逻辑。 */
public class NiaoshoushouKnifeItem extends KnifeItem implements LeftClickHurtable, AdventureUsable {
    public NiaoshoushouKnifeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        SRE.LOGGER.info("Used ON block");
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockPos lowerPos = context.getClickedPos();
        BlockEntity entity = level.getBlockEntity(lowerPos);
        if (!(entity instanceof SmallDoorBlockEntity)) {
            lowerPos = lowerPos.below();
            entity = level.getBlockEntity(lowerPos);
            if (!(entity instanceof SmallDoorBlockEntity)) {
                return InteractionResult.PASS;
            }
        }
        if (!(entity instanceof SmallDoorBlockEntity door) || door.isBlasted()) {
            return InteractionResult.FAIL;
        }

        if (LockEntityManager.getInstance().getNearByLockPos(lowerPos.above(), level) != null) {
            return InteractionResult.PASS;
        }

        BlockState state = level.getBlockState(lowerPos);
        if (!(state.getBlock() instanceof SmallDoorBlock doorBlock)) {
            return InteractionResult.PASS;
        }

        level.playSound(null, context.getClickedPos(), TMMSounds.ITEM_CROWBAR_PRY,
                SoundSource.BLOCKS, 2.5F, 1.0F);
        player.swing(context.getHand(), true);
        if (!level.isClientSide && !player.isCreative()) {
            context.getItemInHand().hurtAndBreak(1, player,
                    context.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        if (!level.isClientSide) {
            doorBlock.open(state, level, door, lowerPos);
            door.blast();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
