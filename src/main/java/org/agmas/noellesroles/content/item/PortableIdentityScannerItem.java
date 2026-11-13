package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.scanner.IdentityScannerService;

import java.util.List;

/** 免费且不消耗的近距离尸体扫描工具，冷却按玩家记录以防换物品绕过。 */
public final class PortableIdentityScannerItem extends Item {
    public PortableIdentityScannerItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) IdentityScannerService.scanPortable(serverPlayer);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) IdentityScannerService.scanPortable(serverPlayer);
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
    /** 在职业允许丢弃和白名单之前执行；拒绝后纠正客户端移走物品的预测。 */
    public static InteractionResult beforeDrop(Player player, ItemStack stack) {
        if (!blocksDrop(player, stack)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer owner) {
            owner.containerMenu.sendAllDataToRemote();
            if (owner.containerMenu != owner.inventoryMenu) owner.inventoryMenu.sendAllDataToRemote();
        }
        return InteractionResult.FAIL;
    }
    /** 通用于所有持有者；服务端同时查询局内期限，不能借换物品、重连或暂停绕过。 */
    public static boolean blocksDrop(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof PortableIdentityScannerItem) || player.isCreative()
                || !SREGameWorldComponent.KEY.get(player.level()).isRunning()
                || !GameUtils.isPlayerAliveAndSurvival(player)) return false;
        if (!player.getCooldowns().isOnCooldown(stack.getItem())
                && !(player instanceof ServerPlayer owner && IdentityScannerService.portableCoolingDown(owner))) return false;
        player.displayClientMessage(Component.translatable("message.noellesroles.identity_scanner.drop_while_cooling"), true);
        return true;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.portable_identity_scanner.desc"));
    }
}
