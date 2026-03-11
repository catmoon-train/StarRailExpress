package org.agmas.noellesroles.roles.sheriff;


public class SheriffGunMaintenanceItem {
 //       extends Item {
//    public SheriffGunMaintenanceItem(Properties settings) {
//        super(settings);
//    }
//
//    @Override
//    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
//        if (!world.isClientSide) {
//            // 恢复枪支耐久度并重置冷却时间
//            SheriffPlayerComponent sheriffComponent = SheriffPlayerComponent.KEY.get(user);
//            sheriffComponent.repairGun();
//
//            // 移除物品
//            ItemStack stack = user.getItemInHand(hand);
//            if (!user.isCreative()) {
//                stack.shrink(1);
//            }
//
//            // 给玩家增加150金币
////            io.wifi.starrailexpress.cca.PlayerShopComponent playerShopComponent = io.wifi.starrailexpress.cca.PlayerShopComponent.KEY.get(user);
////            playerShopComponent.setBalance(playerShopComponent.balance + 150);
////            playerShopComponent.sync();
//
//            user.displayClientMessage(Component.literal("枪支已维护完毕！"), true);
//        }
//        return InteractionResultHolder.success(user.getItemInHand(hand));
//    }
//
//    @Override
//    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
//        tooltip.add(Component.literal("右键使用来维护你的手枪"));
//        tooltip.add(Component.literal("恢复手枪耐久度并重置冷却时间"));
//        tooltip.add(Component.literal("使用后获得150金币"));
//    }

}