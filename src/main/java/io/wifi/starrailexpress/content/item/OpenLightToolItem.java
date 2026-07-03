package io.wifi.starrailexpress.content.item;

import java.util.List;

import org.agmas.noellesroles.game.roles.innocence.attendant.AttendantHandler;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class OpenLightToolItem extends Item {

    private static final int MIN_DISTANCE = 1;
    private static final int MAX_DISTANCE = 16;
    private static final int DEFAULT_DISTANCE = 5;

    public OpenLightToolItem(Properties settings) {
        super(settings);
    }

    public InteractionResult useOn(UseOnContext useOnContext) {
        Player player = useOnContext.getPlayer();
        Vec3 pos = useOnContext.getClickLocation();
        ItemStack it = useOnContext.getItemInHand();

        if (!player.isCreative()) {
            return InteractionResult.PASS;
        }
        if (useOnContext.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (action(it, player, pos)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    public boolean action(ItemStack it, Player player, Vec3 pos) {

        if (!it.has(DataComponents.CUSTOM_DATA)) {
            var tag = new CompoundTag();
            tag.putInt("distance", DEFAULT_DISTANCE);
            it.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        CustomData data = it.getOrDefault(DataComponents.CUSTOM_DATA, null);
        if (data == null) {
            return false;
        }
        var tag = data.copyTag();
        int distance = 5;
        if (tag.contains("distance", Tag.TAG_INT))
            distance = tag.getInt("distance");
        if (player.isCreative()) {
            if (player.isShiftKeyDown()) {
                if (player instanceof ServerPlayer sp) {
                    distance += 1;
                    if (distance > MAX_DISTANCE) {
                        distance = MIN_DISTANCE;
                    }
                    sp.displayClientMessage(
                            Component.translatable("message.starrailexpress.open_light_tool.cahnge_distance", distance),
                            true);

                    tag.putInt("distance", distance);
                    it.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                }
            } else if (player instanceof ServerPlayer sp) {
                AttendantHandler.openLight(sp, pos, distance);
            }
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        ItemStack it = player.getItemInHand(interactionHand);
        if (!player.isCreative()) {
            return InteractionResultHolder.pass(it);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(it, level.isClientSide);
        }
        if (!action(it, player, player.position())) {
            return InteractionResultHolder.fail(it);
        }
        return InteractionResultHolder.success(it);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext tooltipContext, List<Component> tooltip,
            TooltipFlag tooltipFlag) {
        int distance = 5;
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, null);
        if (data != null) {
            var tag = data.copyTag();
            if (tag.contains("distance", Tag.TAG_INT))
                distance = tag.getInt("distance");
        }
        tooltip.add(Component.translatable("item.starrailexpress.open_light_tool.tooltip", distance));
    }
}
