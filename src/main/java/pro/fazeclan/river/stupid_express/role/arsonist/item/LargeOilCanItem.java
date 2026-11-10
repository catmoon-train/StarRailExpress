package pro.fazeclan.river.stupid_express.role.arsonist.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistRoleData;
import pro.fazeclan.river.stupid_express.role.arsonist.LargeOilCanEntity;

/** 纵火犯一次性大油罐，复用银翼的移动控制包。 */
public class LargeOilCanItem extends Item {
    public LargeOilCanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var game = SREGameWorldComponent.KEY.get(level);
        if (!game.isRunning() || !game.isRole(player, SERoles.ARSONIST)
                || !GameUtils.isPlayerAliveAndSurvival(player) || !game.isSkillAvailable
                || player.hasEffect(ModEffects.SKILL_BANED) || player.hasEffect(ModEffects.USED_BANED)
                || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer owner)) {
            return InteractionResultHolder.fail(stack);
        }
        ArsonistRoleData data = ArsonistRoleData.of(owner);
        if (data == null || data.arsonMoment || data.activeOilCan != null) {
            return InteractionResultHolder.fail(stack);
        }
        LargeOilCanEntity can = new LargeOilCanEntity(ModEntities.LARGE_OIL_CAN, level);
        can.setOwner(owner);
        // 生成在视线前方：相机留在玩家身上时，油罐才不会被近裁剪面吞掉，玩家能直接看见它飞出去。
        Vec3 eye = owner.getEyePosition();
        Vec3 ahead = owner.getLookAngle().scale(1.1D);
        can.setPos(eye.x + ahead.x, eye.y + ahead.y - 0.15D, eye.z + ahead.z);
        can.setYRot(owner.getYRot());
        can.setXRot(owner.getXRot());
        // 出手速度同样对半砍（原 0.65 / 0.15），与遥控飞行速度保持一致。
        can.setDeltaMovement(owner.getLookAngle().scale(0.325D).add(0, 0.075D, 0));
        if (!level.addFreshEntity(can)) {
            return InteractionResultHolder.fail(stack);
        }
        data.activeOilCan = can.getUUID();
        owner.getCooldowns().addCooldown(this, 60 * 20);
        stack.shrink(1);
        owner.displayClientMessage(Component.translatable("item.stupid_express.large_oil_can.control"), true);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
