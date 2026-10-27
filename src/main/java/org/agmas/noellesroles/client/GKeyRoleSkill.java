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

package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.screen.BroadcasterScreen;
import org.agmas.noellesroles.client.screen.GodfatherRecruitScreen;
import org.agmas.noellesroles.client.screen.TelegrapherScreen;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.AbilityC2SPacket;
import org.agmas.noellesroles.packet.AbilityWithTargetC2SPacket;
import org.agmas.noellesroles.packet.VeteranDashC2SPacket;
import org.agmas.noellesroles.packet.VultureEatC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.role.touhou.THMountainRoles;
import org.agmas.noellesroles.role_data.neutral.GodfatherRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.HashMap;
import java.util.Map;

public final class GKeyRoleSkill {
    private static final Map<ResourceLocation, GKeyRoleSkill> REGISTERED_SKILLS = new HashMap<>();

    private final boolean beforeRhapsody;
    private final Handler handler;

    static {
        registerDefaults();
    }

    private GKeyRoleSkill(boolean beforeRhapsody, Handler handler) {
        this.beforeRhapsody = beforeRhapsody;
        this.handler = handler;
    }

    public static void register(SRERole role, boolean beforeRhapsody, Handler handler) {
        if (role == null || handler == null) {
            return;
        }
        REGISTERED_SKILLS.put(role.identifier(), new GKeyRoleSkill(beforeRhapsody, handler));
    }

    public static boolean trigger(Minecraft client, SREGameWorldComponent gameWorldComponent, boolean beforeRhapsody) {
        if (client.player == null) {
            return false;
        }
        SRERole role = gameWorldComponent.getRole(client.player);
        if (role == null) {
            return false;
        }
        GKeyRoleSkill skill = REGISTERED_SKILLS.get(role.identifier());
        if (skill == null || skill.beforeRhapsody != beforeRhapsody) {
            return false;
        }
        return skill.handler.handle(client, gameWorldComponent);
    }

    private static void registerDefaults() {
        register(ModRoles.BOMBER, true, (client, gameWorld) -> {
            ClientPlayNetworking.send(new AbilityC2SPacket());
            return true;
        });
        register(ModRoles.FORTUNETELLER, true, (client, gameWorld) -> {
            var hitResult = client.hitResult;
            if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof Player targetPlayer) {
                    ClientPlayNetworking.send(new AbilityWithTargetC2SPacket(targetPlayer));
                }
            } else {
                client.player.displayClientMessage(Component.translatable("hud.fortuneteller.target_miss"), true);
            }
            return true;
        });
        register(ModRoles.NIAN_SHOU, true, (client, gameWorld) -> {
            var hitResult = client.hitResult;
            if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof Player targetPlayer) {
                    ClientPlayNetworking.send(new AbilityWithTargetC2SPacket(targetPlayer));
                }
            } else {
                client.player.displayClientMessage(
                        Component.translatable("message.noellesroles.nianshou.no_target")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return true;
        });
        register(ModRoles.GLITCH_ROBOT, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) {
                return true;
            }
            if (!client.player.getSlot(103).get().is(ModItems.NIGHT_VISION_GLASSES)) {
                client.player.displayClientMessage(
                        Component.translatable("info.glitch_robot.noglasses_on_head").withStyle(ChatFormatting.RED),
                        true);
                return true;
            }
            if (!RoleUtils.isPlayerHasFreeSlot(client.player)) {
                client.player.displayClientMessage(
                        Component.translatable("message.hotbar.full").withStyle(ChatFormatting.RED),
                        true);
                return true;
            }
            ClientPlayNetworking.send(new AbilityC2SPacket());
            return true;
        });
        register(BounsRoles.CREEPER, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) {
                return true;
            }
            ClientPlayNetworking.send(new org.agmas.noellesroles.packet.CreeperAbilityC2SPacket());
            return true;
        });
        register(ModRoles.VETERAN, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) {
                return true;
            }
            ClientPlayNetworking.send(new VeteranDashC2SPacket());
            return true;
        });
        // 交换者：按技能键与正前方目标瞬移交换位置
        register(ModRoles.SWAPPER, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) {
                return true;
            }
            ClientPlayNetworking.send(new org.agmas.noellesroles.packet.SwapperFrontSwapC2SPacket());
            return true;
        });
        register(THMountainRoles.NITORI, false, (client, gameWorld) -> {
            if (NoellesrolesClient.targetPlayer == null) {
                return true;
            }
            ClientPlayNetworking.send(new AbilityWithTargetC2SPacket(NoellesrolesClient.targetPlayer));
            return true;
        });
        register(ModRoles.VULTURE, false, (client, gameWorld) -> {
            if (NoellesrolesClient.targetBody == null) {
                return true;
            }
            ClientPlayNetworking.send(new VultureEatC2SPacket(NoellesrolesClient.targetBody.getUUID()));
            return true;
        });
        register(ModRoles.PELICAN, true, (client, gameWorld) -> {
            // 蹲下释放，否则对鼠标准星目标吞噬
            if (client.player.isShiftKeyDown()) {
                ClientPlayNetworking.send(new AbilityC2SPacket());
                return true;
            }
            var hitResult = client.hitResult;
            if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof Player targetPlayer) {
                    ClientPlayNetworking.send(new AbilityWithTargetC2SPacket(targetPlayer));
                    return true;
                }
            }
            client.player.displayClientMessage(
                    Component.translatable("message.noellesroles.pelican.no_target").withStyle(ChatFormatting.RED),
                    true);
            return true;
        });
        register(ModRoles.GODFATHER, true, (client, gameWorld) -> {
            var comp = RoleData.getNullable(GodfatherRoleData.class, client.player);
            if (comp != null && client.player != null && client.player.level() != null) {
                if (!GameUtils.isPlayerAliveAndSurvival(client.player)) {
                    return true;
                }
                long now = SRE.getTicksFromGameStart();
                if (comp.recruitCooldownUntil > 0 && now < comp.recruitCooldownUntil) {
                    long remaining = (comp.recruitCooldownUntil - now) / 20 + 1;
                    client.player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.noellesroles.godfather.cooldown",
                                    remaining),
                            true);
                    return true;
                }
            }
            // 只有在招募范围内成功瞄准到目标玩家时才打开发招募 UI；未瞄准到玩家不打开
            Player target = findRecruitTarget(client);
            if (target == null) {
                client.player.displayClientMessage(
                        Component.translatable("message.noellesroles.godfather.no_target").withStyle(ChatFormatting.RED),
                        true);
                return true;
            }
            client.execute(() -> client.setScreen(new GodfatherRecruitScreen(target.getUUID())));
            return true;
        });
        register(ModRoles.BROADCASTER, false, (client, gameWorld) -> {
            if (!NoellesrolesClient.isPlayerInAdventureMode(client.player)) {
                return true;
            }
            client.execute(() -> client.setScreen(new BroadcasterScreen()));
            return true;
        });

        register(BounsRoles.TELEGRAPHER, false, (client, gameWorld) -> {
            if (!NoellesrolesClient.isPlayerInAdventureMode(client.player)) {
                return true;
            }
            client.execute(() -> client.setScreen(new TelegrapherScreen()));
            return true;
        });

        // 疫使：瞄准玩家时按技能键感染目标
        register(ModRoles.INFECTED, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) {
                return true;
            }
            var hitResult = client.hitResult;
            if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof Player targetPlayer) {
                    ClientPlayNetworking.send(new AbilityWithTargetC2SPacket(targetPlayer));
                }
            } else {
                client.player.displayClientMessage(Component.translatable("hud.infected.target_miss"), true);
            }
            return true;
        });

        // 咒术师：技能已迁移至统一技能系统（窃取/蚀咒/领域），走默认 G 键管线自动携带准星目标
        // 嬉命人：技能已迁移至统一技能系统（变身派对 / 调色盘），走默认 G 键管线，此处不再注册

        // 窃皮者：按技能键偷皮
        register(ModRoles.SKINCRAWLER, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player))
                return true;
            ClientPlayNetworking.send(new org.agmas.noellesroles.packet.SkincrawlerC2SPacket());
            return true;
        });
    }

    @FunctionalInterface
    public interface Handler {
        boolean handle(Minecraft client, SREGameWorldComponent gameWorldComponent);
    }

    /**
     * 教父招募瞄准判定：沿视线（准星指向）在 {@link GodfatherRoleData#RECRUIT_RANGE} 格内
     * 找到第一个可瞄准的玩家，且视线不能被方块挡住；找不到返回 {@code null}。
     */
    private static Player findRecruitTarget(Minecraft client) {
        Player self = client.player;
        if (self == null || self.level() == null) {
            return null;
        }
        double range = GodfatherRoleData.RECRUIT_RANGE;
        Vec3 eye = self.getEyePosition(1.0F);
        Vec3 look = self.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        AABB searchBox = self.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(self, eye, end, searchBox,
                entity -> entity instanceof Player && !entity.isSpectator(), range * range);
        if (hit == null || !(hit.getEntity() instanceof Player target)) {
            return null;
        }
        // 视线遮挡判定：被方块挡住不算瞄准到（与炸弹递交的判定一致）
        BlockHitResult blockHit = self.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, self));
        if (blockHit.getType() == HitResult.Type.BLOCK
                && eye.distanceTo(blockHit.getLocation()) < eye.distanceTo(hit.getLocation())) {
            return null;
        }
        return target;
    }
}
