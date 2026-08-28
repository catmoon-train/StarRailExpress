package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.entity.YouluSmokeBallEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/** 野人的魔了形态状态、死亡拦截与烟雾吐息。 */
public final class BarbarianRoleData extends SimpleRoleData {
    public static final ResourceLocation SKILL_ID = Noellesroles.id("barbarian_smoke_breath");

    /** 野人 - 触发魔了形态所需的局内金币数（不消耗） */
    public static final int barbarianTransformGold = 150;

    private static boolean eventsRegistered;

    public BarbarianRoleData(RoleDataContext context) {
        super(context);
    }

    public static void registerEvents() {
        if (eventsRegistered) {
            return;
        }
        eventsRegistered = true;
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (!(victim instanceof ServerPlayer player) || killer == null) {
                return true;
            }
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
            if (!game.isRole(player, ModRoles.BARBARIAN) || !game.isKillerTeam(killer)) {
                return true;
            }
            BarbarianRoleData data = RoleData.getNullable(BarbarianRoleData.class, player);
            if (data == null || data.isBerserk()) {
                return true;
            }
            int threshold = barbarianTransformGold;
            if (SREPlayerShopComponent.KEY.get(player).balance < threshold) {
                return true;
            }
            data.enterBerserk(player);
            return false;
        });
    }

    @Override
    public void init() {
        removeBarbarianKnives();
    }

    @Override
    public void clear() {
        init();
    }

    public boolean isBerserk() {
        return SREPlayerPsychoComponent.KEY.get(player).inPsycho();
    }

    private void enterBerserk(ServerPlayer player) {
        int berserkTicks = NoellesRolesConfig.HANDLER.instance().barbarianBerserkSeconds * 20;
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 1.0f, 0.9f);
        player.displayClientMessage(Component.translatable("message.noellesroles.barbarian.transform")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        SREPlayerPsychoComponent.KEY.get(player).startPsycho_time(berserkTicks, 0, true);
        sync();
    }

    /** 消耗局内金币，在当前位置生成 7 秒的幽露同款球烟。 */
    public boolean useSmokeBreath(ServerPlayer player) {
        if (!isBerserk() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        int cost = NoellesRolesConfig.HANDLER.instance().barbarianSmokeCost;
        if (shop.balance < cost) {
            player.displayClientMessage(Component.translatable("message.noellesroles.insufficient_funds_money", cost)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        shop.addToBalance(-cost);
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        YouluSmokeBallEntity smoke = new YouluSmokeBallEntity(ModEntities.YOULU_SMOKE_BALL, level);
        smoke.setupBarbarianSmoke((float) config.barbarianSmokeRadius, config.barbarianSmokeSeconds * 20);
        smoke.setPos(player.getX(), player.getY() + 0.5, player.getZ());
        level.addFreshEntity(smoke);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 1.0f, 0.7f);
        player.displayClientMessage(Component.translatable("message.noellesroles.barbarian.smoke_breath")
                .withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    private boolean shouldGiveEffect(Holder<MobEffect> effect) {
        if (!player.hasEffect(effect))
            return true;
        if (player.getEffect(effect) == null)
            return true;
        if (player.getEffect(effect).getDuration() <= 50) {
            return true;
        }
        return false;
    }
    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer) || !isBerserk()) {
            return;
        }
        if (player.level().getGameTime() % 40 == 0) {
            if (shouldGiveEffect(MobEffects.MOVEMENT_SPEED)) {
                player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 400, 4, false, false, true));
            }
        }
        // 形态结束是职业代价，必须绕过一切免死/护盾拦截。
    }

    public void removeBarbarianKnives() {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.BARBARIAN_KNIFE)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull net.minecraft.nbt.CompoundTag tag, HolderLookup.Provider registries) {
    }

    @Override
    public void readFromSyncNbt(@NotNull net.minecraft.nbt.CompoundTag tag, HolderLookup.Provider registries) {
    }
}
