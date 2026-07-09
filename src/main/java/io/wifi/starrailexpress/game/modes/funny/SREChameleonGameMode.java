package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.compat.chameleon.ChameleonCompat;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.OnGamePlayerRolesConfirm;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 变色龙模式：对 {@code com.mecchachameleon.game} 的适配。
 *
 * <p>
 * 全场只有两个职业、两个阵营——变色龙（平民队）与猎人（击杀者队），双方都穿全套变色龙衣服，
 * 因此都能涂装身体、伪装成方块。胜负、计时、尸体、商店、淘汰全部沿用 SRE 的谋杀模式；
 * 变色龙 mod 只提供伪装与霰弹枪手感，它自己的房间 / 阶段 / 记分板由
 * {@code RoomsGateMixin} 屏蔽。
 * </p>
 *
 * <p>
 * 不在本模式时，{@link ChameleonCompat#isRoomActive()} 恒为 false，变色龙的一切功能
 * （方块伪装、匍匐、{@code /meccha}、{@code /mecchapaint}）都不可用。
 * </p>
 */
public class SREChameleonGameMode extends SREMurderGameMode {

    /** 开局安全时间内封禁猎人行动的时长（tick）。 */
    private static final int KILLER_SAFE_TIME_TICKS = 30 * 20;

    public SREChameleonGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    /**
     * 霰弹枪与“找到躲藏者”的判定入口：只有本模式下、还活着的变色龙才是合法目标。
     *
     * @see io.wifi.starrailexpress.mixin.compat.mecchachameleon.ShotgunItemMixin
     */
    public static boolean isHiderTarget(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!(gameWorldComponent.getGameMode() instanceof SREChameleonGameMode)) {
            return false;
        }
        return !GameUtils.isPlayerEliminated(player)
                && gameWorldComponent.isRole(player, SpecialGameModeRoles.CHAMELEON);
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        Set<UUID> hunters = assignChameleonRoles(serverWorld, gameWorldComponent, players);

        int time = SREConfig.instance().chameleonBaseTime;
        time += players.size() * SREConfig.instance().chameleonTimePerPlayer;
        SREGameTimeComponent.KEY.get(serverWorld).setResetTime(time * 20);
        SREGameTimeComponent.KEY.get(serverWorld).reset();

        // 职业分配完才接管房间：此时猎人名单才确定，霰弹枪的 isSeeker 判定才正确。
        ChameleonCompat.openRoom(players, hunters, SREConfig.instance().chameleonShotgunAmmo,
                SREConfig.instance().chameleonShotCooldown);
    }

    /**
     * 只有两个职业，所以不走谋杀模式那套权重池，直接按击杀者人数切一刀。
     *
     * @return 猎人阵营的玩家 UUID
     */
    private static Set<UUID> assignChameleonRoles(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        int hunterCount = Math.max(1, Math.min(players.size() - 1, RoleCountManager.getKillerCount(players.size())));

        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, new java.util.Random(serverWorld.random.nextLong()));

        Map<Player, SRERole> roleAssignments = new HashMap<>();
        Set<UUID> hunters = new HashSet<>();
        for (int i = 0; i < shuffled.size(); i++) {
            ServerPlayer player = shuffled.get(i);
            if (i < hunterCount) {
                roleAssignments.put(player, SpecialGameModeRoles.CHAMELEON_HUNTER);
                hunters.add(player.getUUID());
            } else {
                roleAssignments.put(player, SpecialGameModeRoles.CHAMELEON);
            }
        }

        OnGamePlayerRolesConfirm.EVENT.invoker().beforeAssignRole(serverWorld, roleAssignments);

        for (Map.Entry<Player, SRERole> entry : roleAssignments.entrySet()) {
            Player player = entry.getKey();
            SRERole role = entry.getValue();
            gameWorldComponent.addRole(player, role, false);
            if (role.canUseKiller()) {
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                if (shop.balance < GameConstants.getMoneyStart()) {
                    shop.setBalance(GameConstants.getMoneyStart());
                }
            }
        }
        gameWorldComponent.syncRoles();

        for (ServerPlayer player : players) {
            SRERole role = gameWorldComponent.getRole(player);
            int roleType = PlayerRoleWeightManager.getRoleType(role);
            PlayerRoleWeightManager.addWeight(player, roleType, 1);
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(role.getIdentifier().toString(), hunterCount,
                            players.size() - hunterCount));
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
            if (roleType == 4) {
                // 安全时间内先把猎人按住，给变色龙涂装和藏身的窗口。
                banKillerDuringSafeTime(player);
            }
        }

        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
        return hunters;
    }

    private static void banKillerDuringSafeTime(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, KILLER_SAFE_TIME_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, KILLER_SAFE_TIME_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, KILLER_SAFE_TIME_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, KILLER_SAFE_TIME_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, KILLER_SAFE_TIME_TICKS, 0, false, true, true));
    }

    /** 死者必须先脱离方块形态，否则尸体和旁观视角会卡在一个方块里。 */
    @Override
    public void killPlayer(Player victim, boolean spawnBody, @Nullable Player killer,
            ResourceLocation deathReason, boolean forceDeath) {
        if (victim instanceof ServerPlayer serverVictim) {
            ChameleonCompat.exitDisguise(serverVictim);
        }
        super.killPlayer(victim, spawnBody, killer, deathReason, forceDeath);
    }

    @Override
    public void stopGame(ServerLevel world) {
        super.stopGame(world);
        ChameleonCompat.closeRoom(world.getServer());
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        ChameleonCompat.closeRoom(serverWorld.getServer());
        super.finalizeGame(serverWorld, gameWorldComponent);
    }
}
