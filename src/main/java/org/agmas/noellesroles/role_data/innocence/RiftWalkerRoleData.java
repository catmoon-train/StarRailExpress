/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 裂隙行者：消耗金币进入裂隙。2 秒前摇期间被杀死则技能不生效；成功后 15 秒隔离/隐身/无敌，并触发破镜重圆崩裂。
 */
public class RiftWalkerRoleData extends SimpleRoleData {

    public static final ResourceLocation SKILL_ID = Noellesroles.id("rift_walker_enter");

    public static final int SKILL_COST = 125;
    public static final int WINDUP_SECONDS = 2;
    public static final int DURATION_SECONDS = 15;
    public static final int COOLDOWN_SECONDS = 90;

    private int windupTicks = 0;
    private int chargedCost = 0;

    public RiftWalkerRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean useSkill(ServerPlayer sp) {
        if (sp.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isSkillAvailable || !gameWorld.isRole(sp, ModRoles.RIFT_WALKER)) {
            return false;
        }
        if (windupTicks > 0 || sp.hasEffect(ModEffects.MIRROR_REUNION)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.rift_walker.already_active")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        int cost = skillCost();
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.insufficient_funds_money", cost)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        shop.addToBalance(-cost);
        chargedCost = cost;

        int windup = windupTicks();
        if (windup <= 0) {
            enterRift(sp);
            return true;
        }

        windupTicks = windup;
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.6f,
                1.4f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.rift_walker.windup")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (windupTicks <= 0) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.RIFT_WALKER)) {
            windupTicks = 0;
            chargedCost = 0;
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp) || sp.isSpectator()) {
            cancelWindup(sp);
            return;
        }
        windupTicks--;
        if (windupTicks == 0) {
            enterRift(sp);
        }
    }

    private void enterRift(ServerPlayer sp) {
        windupTicks = 0;
        chargedCost = 0;
        int duration = durationTicks();
        sp.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, duration, 0, false, false, true));
        sp.addEffect(new MobEffectInstance(ModEffects.PLAYER_ISOLATION, duration, 0, false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 3, false, false, true));
        sp.addEffect(new MobEffectInstance(ModEffects.MIRROR_REUNION, duration, 0, false, false, true));
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f,
                0.7f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.rift_walker.enter")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
    }

    private void cancelWindup(ServerPlayer sp) {
        windupTicks = 0;
        if (chargedCost > 0) {
            SREPlayerShopComponent.KEY.get(sp).addToBalance(chargedCost);
            chargedCost = 0;
        }
        SREAbilityPlayerComponent.KEY.get(sp).setSkillCooldown(SKILL_ID, 0);
        sp.displayClientMessage(Component.translatable("message.noellesroles.rift_walker.cancelled")
                .withStyle(ChatFormatting.RED), true);
    }

    private static int skillCost() {
        int cost = NoellesRolesConfig.instance().riftWalkerSkillCost;
        return cost < 0 ? SKILL_COST : cost;
    }

    private static int windupTicks() {
        int seconds = NoellesRolesConfig.instance().riftWalkerWindupSeconds;
        if (seconds < 0) {
            seconds = WINDUP_SECONDS;
        }
        return GameConstants.getInTicks(0, seconds);
    }

    private static int durationTicks() {
        int seconds = NoellesRolesConfig.instance().riftWalkerDurationSeconds;
        if (seconds <= 0) {
            seconds = DURATION_SECONDS;
        }
        return GameConstants.getInTicks(0, seconds);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("windupTicks", windupTicks);
        tag.putInt("chargedCost", chargedCost);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        windupTicks = tag.getInt("windupTicks");
        chargedCost = tag.getInt("chargedCost");
    }
}
