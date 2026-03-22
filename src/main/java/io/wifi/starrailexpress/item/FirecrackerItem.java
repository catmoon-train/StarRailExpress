package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.entity.FirecrackerEntity;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class FirecrackerItem extends Item implements AdventureUsable {
    public FirecrackerItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        if (context.getClickedFace().equals(Direction.UP)) {
            Player player = context.getPlayer();
            Level world = player.level();
            if (!world.isClientSide) {
                FirecrackerEntity firecracker = TMMEntities.FIRECRACKER.create(world);
                Vec3 spawnPos = context.getClickLocation();

                firecracker.setPos(spawnPos.x(), spawnPos.y(), spawnPos.z());
                firecracker.setYRot(player.getYHeadRot());
                world.addFreshEntity(firecracker);
                if (!player.isCreative()) {
                    if (SRE.REPLAY_MANAGER != null) {
                        SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                    }
                    player.getItemInHand(context.getHand()).shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}