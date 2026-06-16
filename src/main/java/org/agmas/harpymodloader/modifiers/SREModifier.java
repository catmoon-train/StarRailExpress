package org.agmas.harpymodloader.modifiers;

import io.wifi.starrailexpress.api.SREAbstacrtInfoClass;
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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class SREModifier extends SREAbstacrtInfoClass {
    private final Random random = new Random();
    public ResourceLocation identifier;
    public int color;
    public HashSet<SRERole> cannotBeAppliedTo;
    public HashSet<SRERole> canOnlyBeAppliedTo;
    public boolean killerOnly;
    public boolean civilianOnly;
    public boolean notVigilante;
    public Consumer<ServerPlayer> serverTickEvent = null;
    public Consumer<Player> clientTickEvent = null;
    public int maxCount = -1;
    public int enableChance = 100;
    public int enableNeedPlayerCount = 6;
    public int defaultEnableChance = 10000;
    public int defaultNeedPlayerCount = 6;
    public int defaultMaxPlayerCount = -1;
    public boolean isOtherModeRole = false;
    public ArrayList<String> defaultSpawnMaps = new ArrayList<>();

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

    /**
     * 在启用的状态下，最大分配数量。
     * 
     * @param count 最大数量
     * @return
     */
    public SREModifier setMax(int count) {
        maxCount = count;
        return this;
    };


    public SREModifier addDefaultSpawnMaps(String... maps) {
        return this.setDefaultSpawnMaps(maps);
    };

    public SREModifier setDefaultSpawnMaps(String... maps) {
        for (String s : maps) {
            this.defaultSpawnMaps.add(s);
        }
        return this;
    };
    /**
     * 启用需要的玩家数量。
     * 
     * @param count 玩家数量
     * @return
     */
    public SREModifier setEnableNeededPlayerCount(int count) {
        enableNeedPlayerCount = count;
        return this;
    };

    /**
     * 默认启用最大玩家数 -1禁用
     * 
     * @param count
     * @return
     */
    public SREModifier setDefaultMaxPlayerCount(int count) {
        defaultMaxPlayerCount = count;
        return this;
    };

    /**
     * 默认需要玩家数
     * 
     * @param count
     * @return
     */
    public SREModifier setDefaultNeedPlayerCount(int count) {
        defaultNeedPlayerCount = count;
        return this;
    };

    /**
     * 默认启用概率（1/10000）
     * 
     * @param chance
     * @return
     */
    public SREModifier setDefaultEnableChance(int chance) {
        defaultEnableChance = chance;
        return this;
    };

    /**
     * 启用概率（%）
     * 
     * @param chance
     * @return
     */
    public SREModifier setEnableChance(int chance) {
        enableChance = chance;
        return this;
    };

    public SREModifier(ResourceLocation identifier, int color, HashSet<SRERole> cannotBeAppliedTo,
            HashSet<SRERole> canOnlyBeAppliedTo, boolean killerOnly, boolean civilianOnly) {
        this.identifier = identifier;
        this.color = color;
        this.cannotBeAppliedTo = cannotBeAppliedTo;
        this.canOnlyBeAppliedTo = canOnlyBeAppliedTo;
        this.killerOnly = killerOnly;
        this.civilianOnly = civilianOnly;
    }

    public ResourceLocation getIdentifier() {
        return this.identifier;
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

    public HashSet<SRERole> canOnlyBeAppliedTo() {
        return canOnlyBeAppliedTo;
    }

    public HashSet<SRERole> cannotBeAppliedTo() {
        return cannotBeAppliedTo;
    }

    public void setCannotBeAppliedTo(HashSet<SRERole> cannotBeAppliedTo) {
        this.cannotBeAppliedTo = cannotBeAppliedTo;
    }

    public void setCanOnlyBeAppliedTo(HashSet<SRERole> canOnlyBeAppliedTo) {
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

    public SREModifier setCannotAppliedToVigilante(boolean flag) {
        this.notVigilante = flag;
        return this;
    }

    /**
     * 是否是"其它模式"的修饰符（用于U键职业介绍页面的模式筛选）
     * 
     * @return 是否为其他模式修饰符
     */
    public boolean isOtherModeRole() {
        return this.isOtherModeRole;
    }

    /**
     * 设置是否为"其它模式"的修饰符
     * 
     * @param isOtherModeRole 是否为其他模式修饰符
     * @return this
     */
    public SREModifier setOtherModeRole(boolean isOtherModeRole) {
        this.isOtherModeRole = isOtherModeRole;
        return this;
    }
}
