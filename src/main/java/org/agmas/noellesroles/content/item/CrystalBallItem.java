package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.innocence.diviner.DivinerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 晶球：占卜家专用道具。右键对准一具尸体可开始 10 秒占卜施法，期间需静止不动。
 * 完成后随机揭示一项凶手线索；50% 概率破碎；60 秒冷却。
 */
public class CrystalBallItem extends Item {

    public CrystalBallItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);
            if (!gw.isRole(sp, ModRoles.DIVINER) || !GameUtils.isPlayerAliveAndSurvival(sp)) {
                return InteractionResultHolder.fail(stack);
            }

            // 射线检测目标（尸体或玩家）
            NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
            HitResult hr = ProjectileUtil.getHitResultOnViewVector(sp,
                    e -> e instanceof PlayerBodyEntity || (e instanceof Player p && p != sp),
                    cfg.divinerRange);
            if (hr instanceof EntityHitResult ehr) {
                Entity target = ehr.getEntity();
                DivinerPlayerComponent comp = DivinerPlayerComponent.KEY.get(sp);
                comp.startChannel(sp, target);
            }
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
