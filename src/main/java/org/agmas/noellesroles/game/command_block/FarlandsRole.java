package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.FarlandsRoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 远境：世界边缘的错位。
 * 技能和一名玩家互换位置，之后 5 秒近战打不出手。
 * 裂隙开在身前，1.5 秒后吞掉还站在里面的人，包括自己。
 */
public class FarlandsRole extends NormalRole {
    public static final ResourceLocation DEATH_RIFT = org.agmas.noellesroles.Noellesroles.id("farlands_rift");

    public FarlandsRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.FARLANDS, RoleSkill.skill(
                SRE.id("farlands_swap"),
                "skill.noellesroles.farlands.swap",
                FarlandsRole::swap)
                .cooldownSeconds(28).announceToSelf().showOnHud(true).build());
    }

    public static boolean blocksMelee(ServerPlayer attacker) {
        if (attacker == null || !SREGameWorldComponent.KEY.get(attacker.level()).isRole(attacker, ModRoles.FARLANDS)) {
            return false;
        }
        FarlandsRoleData data = RoleData.getNullable(FarlandsRoleData.class, attacker);
        return data != null && data.settling(attacker.serverLevel().getGameTime());
    }

    private static boolean swap(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        if (self.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        List<ServerPlayer> seen = GazeRays.playersAlong(self, 16.0D, 0.4D);
        if (seen.isEmpty()) {
            self.displayClientMessage(Component.translatable("message.noellesroles.farlands.no_target")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ServerPlayer other = seen.get(0);
        FarlandsRoleData data = RoleData.getNullable(FarlandsRoleData.class, self);
        if (data == null) {
            return false;
        }
        Vec3 here = self.position();
        Vec3 there = other.position();
        self.stopRiding();
        other.stopRiding();
        self.teleportTo(there.x, there.y, there.z);
        other.teleportTo(here.x, here.y, here.z);
        self.resetFallDistance();
        other.resetFallDistance();
        long now = self.serverLevel().getGameTime();
        data.settle(now);
        self.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 4 * 20, 0, false, false, true));
        self.addEffect(ModEffects.of(ModEffects.USED_BANED, 2 * 20, 0, false, false, true));
        other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2 * 20, 0, false, false, true));
        LegendFx.pair(self.serverLevel(), here.add(0.0D, 0.2D, 0.0D), there.add(0.0D, 0.2D, 0.0D), 1.6F, LegendFx.SWAP);
        self.serverLevel().playSound(null, self.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F,
                0.6F);
        self.displayClientMessage(Component.translatable("message.noellesroles.farlands.swapped", other.getName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        other.displayClientMessage(Component.translatable("message.noellesroles.farlands.pulled")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.FARLANDS_RIFT.getDefaultInstance(), 85, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}
