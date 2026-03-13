package org.agmas.noellesroles.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;

public class SignedPaperItem extends Item {

    private static final double RANGE = 16.0;
    private static final double FOV_COS_THRESHOLD = Math.cos(Math.toRadians(60));

    public SignedPaperItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (level.isClientSide())
            return;
        if (!(entity instanceof Player player))
            return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        List<Player> nearby = level.getEntitiesOfClass(
                Player.class,
                new AABB(
                        eyePos.x - RANGE, eyePos.y - RANGE, eyePos.z - RANGE,
                        eyePos.x + RANGE, eyePos.y + RANGE, eyePos.z + RANGE),
                e -> e != player);

        for (Player target : nearby) {
            Vec3 toTarget = target.getEyePosition().subtract(eyePos);
            if (toTarget.lengthSqr() < 1e-6)
                continue;

            double dot = lookVec.dot(toTarget.normalize());
            if (dot > FOV_COS_THRESHOLD && isStarPlayer(target) && level.getGameTime() % 20 == 0) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,
                        30,
                        0,
                        false,
                        false));
                break;
            }
        }
    }

    public static boolean isStarPlayer(Player player) {
        return StarGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.STAR);
    }
}