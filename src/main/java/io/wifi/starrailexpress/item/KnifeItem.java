package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.cca.PlayerSkinsComponent;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Random;


public class KnifeItem extends Item implements ItemWithSkin {
    public KnifeItem(Properties settings) {
        super(settings);
    }
    /**
     * (target, killer)
     */
    // public static BiConsumer<ServerPlayer, ServerPlayer> PlayerKilledPlayer;
    public static final ResourceLocation ITEM_ID = SRE.id("knife");
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        user.startUsingItem(hand);
        user.playSound(TMMSounds.ITEM_KNIFE_PREPARE, 1.0f, 1.0f);
        return InteractionResultHolder.consume(itemStack);
    }

    public enum Skin {
        DEFAULT(Colors.LIGHT_GRAY, "Kitchen Knife"),
        CEREMONIAL(0xFFD98C28, "Ceremonial Dagger"),
        PICK(0xFF8D4A51, "Ice Pick"),
        DIAMOND_KNIFE(0xFF4AEDFF, "Diamond Knife"),
        DAGGER(0xFF808080, "Dagger"),
        RAINBOW_KNIFE(0xFFFFFFFF, "Rainbow Knife"),
        FLY_CUTTER(0xFFE0E0E0, "Fly Cutter"),
        STORM_BLADE(0xFF4A90E2, "Storm Blade"),
        DRAGON_BLADE(0xFFFF4444, "Dragon Blade"),
        CHOPPER(0xFF8B4513, "Chopper"),
        NEPTUNE_KNIFE(0xFF1E90FF, "Neptune Knife"),
        COLORFUL_FOLDING_KNIFE(0xFFFF69B4, "Colorful Folding Knife"),
        EDGE_KNIFE(0xFFC0C0C0, "Edge Knife");

        public final int color;
        public final @Nullable String tooltipName;
        public final Random random;

        Skin(int color, @Nullable String tooltipName) {
            this.color = color;
            this.tooltipName = tooltipName;
            this.random = new Random();
        }

        public String getName() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public int getColor() {
            return this.color;
        }

        public static Skin fromString(String name) {
            for (Skin skin : Skin.values()) if (skin.getName().equalsIgnoreCase(name)) return skin;
            return DEFAULT;
        }

        public static Skin getNext(Skin skin) {
            Skin[] values = Skin.values();
            return values[(skin.ordinal() + 1) % values.length];
        }
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        super.inventoryTick(itemStack, level, entity, i, bl);
        if (entity instanceof Player player) {
            if (itemStack.get(SREDataComponentTypes.SKIN) == null) {
                // 使用玩家的CCA组件来获取和设置皮肤
                itemStack.set(SREDataComponentTypes.SKIN, PlayerSkinsComponent.KEY.get( player).getEquippedSkinForItemType(BuiltInRegistries.ITEM.getKey(this).toString()));
            }
        }
    }




    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator()) {
            return;
        }
        if (remainingUseTicks >= this.getUseDuration(stack, user) - 8 || !(user instanceof Player attacker) || !world.isClientSide)
            return;
        StarGameWorldComponent game = StarGameWorldComponent.KEY.get(world);
        final var role = game.getRole(attacker);
        if (role != null){
            if (!role.onUseKnife(attacker)) {
                return;
            }
        }
        HitResult collision = getKnifeTarget(attacker);
        if (collision instanceof EntityHitResult entityHitResult) {
            Entity target = entityHitResult.getEntity();
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            ClientPlayNetworking.send(new KnifeStabPayload(target.getId()));
            CrosshairaddonsCompat.onAttack( target);
        }
    }

    public static HitResult getKnifeTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, entity -> entity instanceof Player player && GameFunctions.isPlayerAliveAndSurvival(player), 4f);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }


    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 110;
    }
}