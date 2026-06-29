package org.agmas.noellesroles.game.roles.neutral.gambler;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.Scheduler;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.List;

import static io.wifi.starrailexpress.game.GameUtils.getSpawnPos;
import static io.wifi.starrailexpress.game.GameUtils.roomToPlayer;

public class GamblerRole extends SRERole {

    public GamblerRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /**
     * 每完成一个任务，向职业库中追加一个随机职业。
     */
    @Override
    public void onFinishQuest(Player player, String quest) {
        if (player.level().isClientSide())
            return;
        GamblerPlayerComponent.KEY.get(player).drawNewRole();
    }

    /**
     * 赌徒职业商店：500 金币购买一把一次性手枪。
     */
    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(new ShopEntry(ModItems.ONCE_REVOLVER.getDefaultInstance(), 500, ShopEntry.Type.WEAPON));
        return entries;
    }

    @Override
    public boolean onUseGun(Player player) {
        if (player.level().isClientSide())
            return false;
        if (player instanceof ServerPlayer)
            ConfigWorldComponent.onPlayerUsedSkill((ServerPlayer) player);

        if (player.isShiftKeyDown()) {
            GamblerPlayerComponent gamblerPlayerComponent = GamblerPlayerComponent.KEY.get(player);
            gamblerPlayerComponent.usedAbility = true;

            if (player instanceof ServerPlayer sp) {
                // 掉枪，但不掉手上用的一次性的
                if (sp.getMainHandItem().is(ModItems.ONCE_REVOLVER)) {
                    sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }
                if (sp.hasEffect(ModEffects.SAFE_TIME)) {
                    sp.removeEffect(ModEffects.SAFE_TIME);
                    // 取消自己的安全时间。
                }
                RoleUtils.dropAndClearAllGuns(sp);
            }

            if (gamblerPlayerComponent.selectedRole != null) {
                var role = RoleUtils.getRole(gamblerPlayerComponent.selectedRole);
                if (role == null) {
                    return false;
                }

                // 记录
                SRE.REPLAY_MANAGER.recordPlayerKill(null, player.getUUID(),
                        Noellesroles.bothDeathReason("gamble_self_kill"));

                // 先传送走
                teleport(player);
                // 等待一个tick再改职业

                Scheduler.schedule(() -> {

                    RoleUtils.changeRole(player, role);

                    if (role.canUseKiller()) {
                        SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
                        if (playerShopComponent.balance < 100)
                            playerShopComponent.setBalance(100);
                    } else {
                    }

                    if (player instanceof ServerPlayer serverPlayer) {
                        RoleUtils.sendWelcomeAnnouncement(serverPlayer);
                    }
                }, 1);
                player.level().players().forEach(
                        p -> {
                            p.playNotifySound(NRSounds.GAMBER_DEATH, SoundSource.PLAYERS, 0.5F, 1.3F);
                            p.playNotifySound(SoundEvents.BAT_HURT, SoundSource.PLAYERS, 0.5F, 1.3F);
                        });
            } else {
                GameUtils.killPlayer(player, true, null, Noellesroles.bothDeathReason("gamble_self_kill"), true);
            }

            return false;
        }
        return false;
    }

    private static void teleport(Player player) {
        Vec3 pos = getSpawnPos(AreasWorldComponent.KEY.get(player.level()), roomToPlayer.get(player.getUUID()));
        if (pos != null) {
            player.teleportTo(pos.x(), pos.y() + 1, pos.z());
        }
    }
}
