package pro.fazeclan.river.stupid_express.modifier.refugee.cca;

import java.util.function.Consumer;

import org.agmas.harpymodloader.component.WorldModifierComponent;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.SRE;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

public record PlayerStatsBeforeRefugee(Vec3 pos, int money, ListTag inventory, Vec2 rotation, boolean isAlive,
        float mood, int shieldAmount) {
    public static Consumer<ServerPlayer> beforeLoadFunc = null;

    // 期间死亡的其它玩家会复活，玩家物品栏、金币、位置重置到亡命徒复活的时刻
    public static void RegisterDeathEvent() {
        (OnPlayerDeath.EVENT).register((victim, deathReason) -> {
            var level = victim.level();
            var worldModifierComponent = WorldModifierComponent.KEY.get(level);
            if (worldModifierComponent.isModifier(victim.getUUID(), SEModifiers.REFUGEE)) {
                var refugeeComponent = RefugeeComponent.KEY.get(level);
                Vec3 pos = GameUtils.getSpawnPos(AreasWorldComponent.KEY.get(level),
                        GameUtils.roomToPlayer.get(victim.getUUID()));
                if (pos != null) {
                    refugeeComponent.addPendingRevival(victim.getUUID(), pos.x(), pos.y() + 1, pos.z());
                } else {
                    refugeeComponent.addPendingRevival(victim.getUUID(), victim.getX(), victim.getY(), victim.getZ());
                }
            }
        });
    }

    public static void LoadToPlayer(ServerPlayer player, PlayerStatsBeforeRefugee playerStats, SRERole role,
            RefugeeComponent refugeeComponent, WorldModifierComponent worldModifierComponent) {
        if (playerStats == null)
            return;
        if (!playerStats.isAlive())
            return;
        if (beforeLoadFunc != null) {
            beforeLoadFunc.accept(player);
        }
        player.getInventory().clearContent();
        player.getInventory().load(playerStats.inventory());
        StupidRoleUtils.clearAllSatisfiedItems(player, TMMItems.BAT);
        player.setCamera(null);

        SREArmorPlayerComponent bartenderPlayerComponent = SREArmorPlayerComponent.KEY.get(player);
        
        bartenderPlayerComponent.armor = playerStats.shieldAmount;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            SRE.REPLAY_MANAGER.recordPlayerRevival(player.getUUID(), role);
            player.setGameMode(GameType.ADVENTURE);
        }
        player.teleportTo(playerStats.pos().x, playerStats.pos().y, playerStats.pos().z);
        player.setPos(playerStats.pos());
        player.setXRot(playerStats.rotation().x);
        player.setYRot(playerStats.rotation().y);
        TrainVoicePlugin.resetPlayer(player.getUUID());
        var shopComponent = SREPlayerShopComponent.KEY.get(player);
        var moodComponent = SREPlayerMoodComponent.KEY.get(player);
        shopComponent.balance = playerStats.money();
        moodComponent.setMood(playerStats.mood());
        shopComponent.sync();
        moodComponent.sync();
    }

    public static PlayerStatsBeforeRefugee SaveFromPlayer(ServerPlayer player, boolean isAlive) {
        var inventory = player.getInventory();
        ListTag listTag = new ListTag();
        inventory.save(listTag);
        var shopComponent = SREPlayerShopComponent.KEY.get(player);
        var moodComponent = SREPlayerMoodComponent.KEY.get(player);
        int armorAmount = SREArmorPlayerComponent.KEY.get(player).getArmor();
        var playerStats = new PlayerStatsBeforeRefugee(player.position(),
                shopComponent.balance, listTag.copy(), player.getRotationVector(),
                isAlive, moodComponent.getMood(), armorAmount);
        return playerStats;
    }
}
