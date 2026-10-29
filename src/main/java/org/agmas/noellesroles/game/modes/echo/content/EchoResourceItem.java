package org.agmas.noellesroles.game.modes.echo.content;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.cards.EchoCardRegistry;
import org.agmas.noellesroles.game.modes.echo.inventory.*;
import org.agmas.noellesroles.game.modes.echo.state.EchoRoleData;
import java.util.List;

/** 无实例身份的只读展示栈。绝不从栈、名称或组件反序列化合法资源。 */
public final class EchoResourceItem extends Item {
    public static Runnable clientUse = () -> { };
    private static final List<String> CARD_MODELS = EchoCardRegistry.all().stream().map(c -> c.id()).sorted().toList();
    public EchoResourceItem(Properties properties) { super(properties); }
    public static boolean active(Player player) {
        var data = RoleData.getNullable(EchoRoleData.class, player);
        return data != null && data.matchId() != null
                && io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level()).getGameMode() instanceof EchoGameMode;
    }
    @Override public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !active(player) || EchoHotbarSlots.resourceSlot(player.getInventory().selected) < 0)
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        if (level.isClientSide) clientUse.run();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    public static ItemStack project(EchoInventory.Slot slot) {
        if (slot.empty()) return ItemStack.EMPTY;
        var stack = new ItemStack(EchoContent.RESOURCE, Math.max(1, slot.items().size()));
        int model = 4;
        Component name = Component.translatable("echo_hotbar.reserved");
        if (!slot.items().isEmpty()) {
            var resource = slot.items().getFirst();
            if (resource.kind() == EchoResourceInstance.Kind.PACK) {
                model = 1 + resource.quality().ordinal();
                name = Component.translatable("echo_hotbar.pack." + resource.quality().name().toLowerCase(java.util.Locale.ROOT));
            } else {
                model = 100 + CARD_MODELS.indexOf(resource.definitionId());
                name = Component.translatable(EchoCardRegistry.require(resource.definitionId()).nameKey());
            }
        }
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(model));
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }
    /** 只从已挂载的服务端唯一库存投影；所有容器同时广播，不依赖五 tick 修复。 */
    public static void sync(ServerPlayer player) {
        var data = RoleData.getNullable(EchoRoleData.class, player);
        if (data == null || data.inventory() == null || !active(player)) return;
        var slots = data.inventory().snapshot().slots();
        boolean changed = false;
        for (int n = 0; n < EchoHotbarSlots.RESOURCES; n++) {
            var next = project(slots.get(n));
            int at = EchoHotbarSlots.inventorySlot(n);
            if (!ItemStack.matches(player.getInventory().getItem(at), next)) {
                player.getInventory().setItem(at, next); changed = true;
            }
        }
        if (changed) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        }
    }
}
