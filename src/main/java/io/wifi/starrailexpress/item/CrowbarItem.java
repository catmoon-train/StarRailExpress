package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CrowbarItem extends Item implements AdventureUsable {
    public CrowbarItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockEntity entity = world.getBlockEntity(context.getClickedPos());
        if (!(entity instanceof DoorBlockEntity)) entity = world.getBlockEntity(context.getClickedPos().below());
        Player player = context.getPlayer();
        if (entity instanceof DoorBlockEntity door && !door.isBlasted() && player != null) {
            if (!player.isCreative()) player.getCooldowns().addCooldown(this, 6000);
            world.playSound(null, context.getClickedPos(), TMMSounds.ITEM_CROWBAR_PRY, SoundSource.BLOCKS, 2.5f, 1f);
            player.swing(InteractionHand.MAIN_HAND, true);

            if (!player.isCreative()) {
                if (SRE.REPLAY_MANAGER != null) {
                    SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                }
                player.getCooldowns().addCooldown(this, GameConstants.ITEM_COOLDOWNS.get(this));
            }

            door.blast();
        }
        return super.useOn(context);
    }
}
