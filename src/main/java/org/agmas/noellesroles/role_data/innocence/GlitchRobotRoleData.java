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
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public class GlitchRobotRoleData extends SimpleRoleData {



    public int glitchTimer = 0;

    public GlitchRobotRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        this.glitchTimer = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.GLITCH_ROBOT)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        // 故障计时器
        glitchTimer++;
        if (glitchTimer >= 600) { // 30秒
            glitchTimer = 0;
            // 缓慢 10 (Amplifier 9), 3.5秒 (70 ticks)
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 9, false, false, true));
        }
    }

    /**
     * 被击倒时调用，生成缓慢效果云
     */
    public static void onKnockOut(Player victim) {
        if (victim instanceof ServerPlayer sp) {
            ConfigWorldComponent.onPlayerUsedSkill( sp);
            // 创建半径为4的缓慢2效果云，持续5秒（100 ticks）
            // var command = "execute at @s run summon area_effect_cloud ~ ~ ~
            // {Radius:4,Duration:100,RadiusOnUse:0f,RadiusPerTick:0f,WaitTime:0,potion_contents:{custom_effects:[{id:\"slowness\",amplifier:1,duration:100,ambient:false,show_icon:false,show_particles:false}]},custom_particle:{type:\"dust\",color:15924992,scale:1}}";
            // try {
            // sp.getServer().getCommands().performPrefixedCommand(sp.createCommandSourceStack(),
            // command);
            // } catch (Exception e) {
            // LoggerFactory.getLogger(GlitchRobotRoleData.class).warn(
            // "Failed to execute : " + command + ", error: " + e.getMessage());
            // }
            AreaEffectCloud cloud = new AreaEffectCloud(sp.level(), sp.getX(), sp.getY(),
                    sp.getZ());

            cloud.setRadius(6.0F);
            cloud.setDuration(100); // 5秒
            cloud.setRadiusOnUse(0.0F);
            cloud.setRadiusPerTick(0.0F);
            cloud.setWaitTime(0);
            cloud.setParticle(ParticleTypes.EFFECT);
            cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2,
                    false, false, true));
            sp.level().addFreshEntity(cloud);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.glitchTimer = tag.getInt("glitchTimer");
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("glitchTimer", this.glitchTimer);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }



    @Override
    public void clientTick() {
        var gameComp = SREGameWorldComponent.KEY.maybeGet(player.level()).orElse(null);
        if (gameComp == null || !gameComp.isRole(player, ModRoles.GLITCH_ROBOT)) {
            return;
        }
        if (!player.getSlot(103).get().is(ModItems.NIGHT_VISION_GLASSES))
            player.removeEffect(MobEffects.NIGHT_VISION);
    }

    

}
