package io.wifi.starrailexpress.content.item;

import org.agmas.noellesroles.role.touhou.RedHouseRoles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DoorCustomOpenItem;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
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
import net.minecraft.world.level.block.state.BlockState;

public class CrowbarItem extends Item implements AdventureUsable, DoorCustomOpenItem {
    public CrowbarItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockState state = world.getBlockState(context.getClickedPos());
        BlockEntity entity = world.getBlockEntity(context.getClickedPos());
        if (!(entity instanceof SmallDoorBlockEntity))
            entity = world.getBlockEntity(context.getClickedPos().below());
        Player player = context.getPlayer();
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (entity instanceof SmallDoorBlockEntity door && !door.isBlasted() && player != null) {
            world.playSound(null, context.getClickedPos(), TMMSounds.ITEM_CROWBAR_PRY, SoundSource.BLOCKS, 2.5f, 1f);
            player.swing(InteractionHand.MAIN_HAND, true);

            if (!player.isCreative()) {
                if (SRE.REPLAY_MANAGER != null) {
                    SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                }
                if (gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)) {
                    player.getCooldowns().addCooldown(this,
                            GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.CROWBAR, 45 * 20) / 4);
                }
                else if (gameWorldComponent.isRole(player, RedHouseRoles.FURANDORU)) {
                    player.getCooldowns().addCooldown(this,
                            GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.CROWBAR, 45 * 20) / 6);
                }
                else {
                    player.getCooldowns().addCooldown(this,
                            GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.CROWBAR, 45 * 20));
                }
            }
            if(state.getBlock() instanceof SmallDoorBlock sb){
                sb.open(state, world, door, null);
            }
            door.blast();
            // 记录撬门事件（低频关键事件）
            if (!world.isClientSide && SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordDoorPry(player.getUUID(), context.getClickedPos());
            }
        }
        return super.useOn(context);
    }
}
