package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.cca.SREPlayerNoteComponent;
import io.wifi.starrailexpress.entity.NoteEntity;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.util.AdventureUsable;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class NoteItem extends Item implements AdventureUsable {
    public NoteItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, Player user, InteractionHand hand) {
        return super.use(world, user, hand);
    }

    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) return InteractionResult.PASS;
        SREPlayerNoteComponent component = SREPlayerNoteComponent.KEY.get(player);
        if (!component.written) {
            player.displayClientMessage(Component.literal("我应该先写下点东西").withColor(Mth.hsvToRgb(0F, 1.0F, 0.6F)), true);
            return InteractionResult.PASS;
        }
        Level world = player.level();
        if (world.isClientSide) return InteractionResult.PASS;
        NoteEntity note = TMMEntities.NOTE.create(world);

        if (note == null) return InteractionResult.PASS;

        switch (context.getClickedFace()) {
            case DOWN -> {
                return InteractionResult.PASS;
            }
            case UP -> note.setYRot(player.getYHeadRot());
            case NORTH, SOUTH, WEST, EAST -> note.setYRot(180f + (world.random.nextFloat() - .5f) * 30f);
        }

        Direction side = context.getClickedFace();
        note.setDirection(side);
        note.setLines(component.text);
        Vec3 hitPos = context.getClickLocation().add(context.getClickLocation().subtract(player.getEyePosition()).normalize().scale(-.01f)).subtract(0, note.getBbHeight() / 2f, 0);
        note.setPos(hitPos.x(), hitPos.y(), hitPos.z());
        world.addFreshEntity(note);
        if (!player.isCreative()) {
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            player.getItemInHand(context.getHand()).shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}