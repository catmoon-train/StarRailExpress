package org.agmas.harpymodloader.modifiers;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class SREModifier {
    private final Random random = new Random();
    public ResourceLocation identifier;
    public int color;
    public ArrayList<SRERole> cannotBeAppliedTo;
    public ArrayList<SRERole> canOnlyBeAppliedTo;
    public boolean killerOnly;
    public boolean civilianOnly;
    public Consumer<ServerPlayer> serverTickEvent = null;
    public Consumer<Player> clientTickEvent = null;
    public int maxCount = -1;
    public int enableChance = 100;
    public int enableNeedPlayerCount = 6;

    public SREModifier setClientGameTickEvent(Consumer<Player> event) {
        this.clientTickEvent = event;
        return this;
    };

    public SREModifier setServerGameTickEvent(Consumer<ServerPlayer> event) {
        this.serverTickEvent = event;
        return this;
    };

    public void autoGameTickEvent(Player player) {
        if (player instanceof ServerPlayer sl) {
            this.serverGameTickEvent(sl);
        } else {
            this.clientGameTickEvent(player);
        }
    }

    public void clientGameTickEvent(Player player) {
        if (clientTickEvent != null)
            clientTickEvent.accept(player);
    }

    public void serverGameTickEvent(ServerPlayer player) {
        if (serverTickEvent != null)
            serverTickEvent.accept(player);
    }

    public SREModifier setMax(int count) {
        maxCount = count;
        return this;
    };

    public SREModifier setEnableNeededPlayerCount(int count) {
        enableNeedPlayerCount = count;
        return this;
    };

    /**
     * 启用概率（%）
     * @param count 
     * @return
     */
    public SREModifier setEnableChance(int cahnce) {
        enableChance = cahnce;
        return this;
    };

    public SREModifier(ResourceLocation identifier, int color, ArrayList<SRERole> cannotBeAppliedTo,
            ArrayList<SRERole> canOnlyBeAppliedTo, boolean killerOnly, boolean civilianOnly) {
        this.identifier = identifier;
        this.color = color;
        this.cannotBeAppliedTo = cannotBeAppliedTo;
        this.canOnlyBeAppliedTo = canOnlyBeAppliedTo;
        this.killerOnly = killerOnly;
        this.civilianOnly = civilianOnly;
    }

    public ResourceLocation identifier() {
        return this.identifier;
    }

    public MutableComponent getName() {
        return getName(false);
    }

    public MutableComponent getName(boolean color) {
        // Log.info(LogCategory.GENERAL,
        // Language.getInstance().hasTranslation("announcement.star.modifier." +
        // identifier().getPath())+"");
        if (!Language.getInstance().has("announcement.star.modifier." + identifier().toLanguageKey())
                && Language.getInstance().has("announcement.star.modifier." + identifier().getPath())) {
            return Component.translatable("announcement.star.modifier." + identifier().getPath());
        }
        final MutableComponent text = Component
                .translatable("announcement.star.modifier." + identifier().toLanguageKey());
        if (color) {
            return text.withColor(color());
        }
        return text;
    }

    public int color() {
        return this.color;
    }

    public ArrayList<SRERole> canOnlyBeAppliedTo() {
        return canOnlyBeAppliedTo;
    }

    public ArrayList<SRERole> cannotBeAppliedTo() {
        return cannotBeAppliedTo;
    }

    public void setCannotBeAppliedTo(ArrayList<SRERole> cannotBeAppliedTo) {
        this.cannotBeAppliedTo = cannotBeAppliedTo;
    }

    public void setCanOnlyBeAppliedTo(ArrayList<SRERole> canOnlyBeAppliedTo) {
        this.canOnlyBeAppliedTo = canOnlyBeAppliedTo;
    }

    
    /**
     * 获取一局里最大可出现此修饰符数量。-1表示不变。
     * 
     * @param gameWorldComponent
     * @param serverLevel
     * @param players
     * @return
     */
    public int getRoundMaxCount(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        if (this.enableChance >= 0) {
            int nchance = random.nextInt(0, 100);
            if (nchance > enableChance) {
                return 0;
            }
        }
        if (this.enableNeedPlayerCount >= 0) {
            int playerCount = players.size();
            if (playerCount < this.enableNeedPlayerCount) {
                return 0;
            }
        }
        return maxCount;
    }
}
