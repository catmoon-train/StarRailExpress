package org.agmas.noellesroles.game.command_block.item;

import java.util.List;

import org.agmas.noellesroles.game.command_block.CommandBlockCombat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** 15 耐久、11 点虚拟伤害。右键斩出剑气，间隔 0.2 秒；击杀后 30 秒冷却。 */
public class HimSwordItem extends SwordItem {
    public static final int QI_COOLDOWN = 4;
    private static final Tier LOW_DURABILITY = new Tier() {
        @Override
        public int getUses() {
            return 15;
        }

        @Override
        public float getSpeed() {
            return Tiers.DIAMOND.getSpeed();
        }

        @Override
        public float getAttackDamageBonus() {
            return Tiers.DIAMOND.getAttackDamageBonus();
        }

        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return Tiers.DIAMOND.getIncorrectBlocksForDrops();
        }

        @Override
        public int getEnchantmentValue() {
            return Tiers.DIAMOND.getEnchantmentValue();
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Tiers.DIAMOND.getRepairIngredient();
        }
    };

    public HimSwordItem(Properties properties) {
        super(LOW_DURABILITY, properties);
    }

    public static Properties properties() {
        return new Properties().stacksTo(1).attributes(SwordItem.createAttributes(LOW_DURABILITY, 7, -2.4F));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof ServerPlayer attackerPlayer) || !(target instanceof ServerPlayer victim)) {
            return super.hurtEnemy(stack, target, attacker);
        }
        if (attackerPlayer.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        if (!CommandBlockCombat.dealHimSword(attackerPlayer, victim)) {
            return false;
        }
        stack.hurtAndBreak(1, attackerPlayer, EquipmentSlot.MAINHAND);
        return true;
    }
}
