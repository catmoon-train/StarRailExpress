package org.agmas.noellesroles.content.item;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.init.ModItems;
import io.wifi.starrailexpress.game.GameUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BatonHandler {
    private static final Map<UUID, HitRecord> RECORDS = new HashMap<>();
    private static final int KILL_WINDOW_TICKS = 4 * 20; // 4 秒

    public static void register() {
        AttackEntityCallback.EVENT.register(BatonHandler::onEntityDamaged);
    }

    public static InteractionResult onEntityDamaged(Player attacker, Level level, InteractionHand hand, Entity entity,
            EntityHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.PASS;
        if (!(entity instanceof Player victim)) return InteractionResult.PASS;
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(victim)) return InteractionResult.PASS;

        ItemStack main = attacker.getItemInHand(hand);
        if (!main.is(ModItems.BATON)) return InteractionResult.PASS;
        // 如果处于冷却中，阻止攻击
        if (attacker.getCooldowns().isOnCooldown(ModItems.BATON)) {
            return InteractionResult.FAIL;
        }

        UUID aId = attacker.getUUID();
        HitRecord rec = RECORDS.get(aId);
        if (rec == null || !rec.target.equals(victim.getUUID())) {
            rec = new HitRecord(victim.getUUID(), 0, level.getGameTime());
            RECORDS.put(aId, rec);
        }

        // 更新时间/计数
        long now = level.getGameTime();
        if (now - rec.lastTime > KILL_WINDOW_TICKS) {
            rec.count = 0;
        }
        rec.lastTime = now;
        rec.count++;

        // 造成 1 点伤害并击退
        victim.hurt(attacker.damageSources().playerAttack(attacker), 1.0F);
        victim.knockback(0.5F, attacker.getX() - victim.getX(), attacker.getZ() - victim.getZ());
        // 定身 1 秒（使用 Slowness）
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 10));

        // 耐久消耗
        if (!attacker.isCreative()) {
            final InteractionHand usedHand = hand;
            main.hurtAndBreak(1, attacker, usedHand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }

        if (rec.count >= 2) {
            // 击杀
            RECORDS.remove(aId);
            io.wifi.starrailexpress.game.GameUtils.killPlayer(victim, true, attacker, org.agmas.noellesroles.Noellesroles.id("baton_kill"));
            // 冷却
            attacker.getCooldowns().addCooldown(ModItems.BATON, 15 * 20);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.SUCCESS;
    }

    private static class HitRecord {
        public UUID target;
        public int count;
        public long lastTime;

        public HitRecord(UUID t, int c, long lt) {
            this.target = t;
            this.count = c;
            this.lastTime = lt;
        }
    }
}
