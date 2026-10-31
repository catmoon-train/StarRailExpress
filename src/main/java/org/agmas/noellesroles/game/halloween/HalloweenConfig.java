package org.agmas.noellesroles.game.halloween;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** 万圣节世界配置；时间均为 tick，距离为格，物品数量为单次给予数量。 */
public final class HalloweenConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(HalloweenConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile HalloweenConfig instance = new HalloweenConfig();

    public int knockCooldownTicks = 300;
    public int knockUndeadTicks = 200;
    public int candyNpcLifetimeTicks = 600;
    public int undeadLifetimeTicks = 1800;
    public int maxEventUndead = 32;
    public int gunCooldownTicks = 200;
    public int gunFreezeTicks = 100;
    public int gunSummonCount = 3;
    public int candyNpcRewardCount = 1;
    public int gunShieldSuppressionTicks = 600;
    public int sawChargeTicks = 10;
    public int heartDelayTicks = 300;
    public int candyDurationTicks = 600;
    public int costumeBagIntervalTicks = 2400;
    public int linkInviteTicks = 300;
    public int npcKillRadius = 8;
    public int pactReviveTicks = 1200;
    public int questTaskCount = 3;
    public int nightStartTicks = 2400;
    public int nightDurationTicks = 1200;
    public int nightIntervalTicks = 3600;
    public int nightDarkThreshold = 5;
    public double linkDistance = 10;
    public double candyNpcChance = 0.2;
    public int maxHideoutsPerPlayer = 3;
    public int maxHideouts = 128;
    public List<Loot> loot = defaultLoot();
    public List<Trade> trades = defaultTrades();

    public static HalloweenConfig get() {
        return instance;
    }

    /** 从世界根目录读取；首次生成默认文件，坏文件保留供修复，并明确记录告警。 */
    public static void reload(MinecraftServer server) {
        HalloweenItems.initialize();
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("halloween.json");
        HalloweenConfig loaded = new HalloweenConfig();
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    loaded = GSON.fromJson(reader, HalloweenConfig.class);
                }
                if (loaded == null) throw new IllegalArgumentException("配置不能为 null");
                loaded.validate(HalloweenConfig::registeredItem, message -> LOGGER.warn("[Halloween] {}: {}", file, message));
            } else {
                Files.createDirectories(file.getParent());
                try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(loaded, writer);
                }
            }
        } catch (Exception exception) {
            LOGGER.warn("[Halloween] 无法加载/保存配置 {}，本次使用全部默认值；原文件不会被覆盖", file, exception);
            loaded = new HalloweenConfig();
        }
        instance = loaded;
        HalloweenCombat.configureGunSummonCount(loaded.gunSummonCount);
        HalloweenVillage.configureCandyReward(loaded.candyNpcRewardCount);
    }

    private static boolean registeredItem(String item) {
        ResourceLocation id = ResourceLocation.tryParse(item);
        return id != null && !id.equals(ResourceLocation.withDefaultNamespace("air"))
                && BuiltInRegistries.ITEM.containsKey(id);
    }

    /** 验证与注册表查询分离，便于不启动 Minecraft 的配置测试。 */
    void validate(Predicate<String> validItem, Consumer<String> warn) {
        HalloweenConfig defaults = new HalloweenConfig();
        for (Field field : HalloweenConfig.class.getFields()) {
            if (field.getType() != int.class) continue;
            try {
                int value = field.getInt(this);
                int min = switch (field.getName()) {
                    case "nightStartTicks", "nightDarkThreshold", "maxEventUndead" -> 0;
                    default -> 1;
                };
                int max = switch (field.getName()) {
                    case "nightDarkThreshold" -> 15;
                    case "npcKillRadius" -> 128;
                    case "gunSummonCount" -> 32;
                    case "candyNpcRewardCount" -> 64;
                    case "maxEventUndead", "maxHideouts" -> 1024;
                    case "maxHideoutsPerPlayer" -> 128;
                    default -> 1728000;
                };
                if (value < min || value > max) {
                    warn.accept(field.getName() + " 超出范围，恢复默认值 " + field.getInt(defaults));
                    field.setInt(this, field.getInt(defaults));
                }
            } catch (IllegalAccessException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        if (!Double.isFinite(linkDistance) || linkDistance <= 0 || linkDistance > 128) {
            warn.accept("linkDistance 无效，恢复默认值 10");
            linkDistance = defaults.linkDistance;
        }
        if (!Double.isFinite(candyNpcChance) || candyNpcChance < 0 || candyNpcChance > 1) {
            warn.accept("candyNpcChance 无效，恢复默认值 0.2");
            candyNpcChance = defaults.candyNpcChance;
        }
        List<Loot> cleanLoot = new ArrayList<>();
        if (loot != null) for (Loot entry : loot) {
            if (entry != null && validId(entry.item, validItem) && entry.count > 0 && entry.count <= 64
                    && entry.weight > 0 && entry.weight <= 1000000) cleanLoot.add(entry);
            else warn.accept("忽略无效 loot 条目（物品 id / count 1..64 / weight 1..1000000）");
        }
        if (cleanLoot.isEmpty()) {
            warn.accept("loot 为空或全部无效，恢复默认物品池");
            cleanLoot = defaultLoot();
        }
        loot = cleanLoot;
        List<Trade> cleanTrades = new ArrayList<>();
        if (trades == null) {
            warn.accept("trades 为 null，恢复默认商店");
            cleanTrades = defaultTrades();
        } else for (Trade entry : trades) {
            if (entry != null && validId(entry.item, validItem)
                    && isVillageTradeAllowed(entry.item)
                    && entry.count > 0 && entry.count <= 64 && entry.pumpkins >= 0 && entry.pumpkins <= 4096
                    && entry.coins >= 0 && entry.coins <= 1000000 && (entry.pumpkins > 0 || entry.coins > 0)) {
                cleanTrades.add(entry);
            } else warn.accept("忽略无效 trades 条目（禁止直接出售魂契和献血锯子、负价格或免费商品）");
        }
        trades = cleanTrades;
    }

    /** 固定 NPC 禁售物品；旧配置和交易请求都必须遵循同一限制。 */
    static boolean isVillageTradeAllowed(String item) {
        return item != null && !item.equals("noellesroles:halloween_soul_contract")
                && !item.equals("noellesroles:halloween_blood_saw");
    }

    private static boolean validId(String id, Predicate<String> validItem) {
        return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") && validItem.test(id);
    }

    /** 抽签区间为 [0,1)，长整型累计避免可配置权重相加溢出。 */
    static Loot rollLoot(List<Loot> pool, double roll) {
        long total = 0;
        for (Loot entry : pool) if (entry.weight > 0) total += entry.weight;
        if (total <= 0) return null;
        long ticket = Math.min(total - 1, (long) (Math.clamp(roll, 0.0, 1.0) * total));
        for (Loot entry : pool) {
            if (entry.weight <= 0) continue;
            ticket -= entry.weight;
            if (ticket < 0) return entry;
        }
        return null;
    }

    private static List<Loot> defaultLoot() {
        return new ArrayList<>(List.of(
                new Loot("noellesroles:halloween_candy", 2, 30),
                new Loot("noellesroles:halloween_pumpkin", 2, 24),
                new Loot("noellesroles:halloween_coin", 1, 18),
                new Loot("minecraft:bread", 2, 14),
                new Loot("minecraft:cooked_beef", 2, 10),
                new Loot("minecraft:golden_carrot", 1, 6),
                new Loot("noellesroles:halloween_heart", 1, 5),
                new Loot("noellesroles:halloween_pumpkin_president", 1, 3),
                new Loot("noellesroles:halloween_pumpkin_helmet", 1, 2),
                new Loot("noellesroles:halloween_pumpkin_chestplate", 1, 2),
                new Loot("noellesroles:halloween_pumpkin_leggings", 1, 2),
                new Loot("noellesroles:halloween_pumpkin_boots", 1, 2),
                new Loot("noellesroles:halloween_bone_sword", 1, 2),
                new Loot("noellesroles:halloween_soul_link", 1, 1),
                new Loot("noellesroles:halloween_fright_gun", 1, 1),
                new Loot("noellesroles:halloween_blood_saw", 1, 1)));
    }

    private static List<Trade> defaultTrades() {
        return new ArrayList<>(List.of(
                new Trade("noellesroles:halloween_candy", 1, 1, 0),
                new Trade("noellesroles:halloween_candy_bag", 1, 3, 0),
                new Trade("noellesroles:halloween_pumpkin_president", 1, 5, 10),
                new Trade("noellesroles:halloween_heart", 1, 8, 0),
                new Trade("noellesroles:halloween_pumpkin_helmet", 1, 3, 5),
                new Trade("noellesroles:halloween_pumpkin_chestplate", 1, 5, 5),
                new Trade("noellesroles:halloween_pumpkin_leggings", 1, 4, 5),
                new Trade("noellesroles:halloween_pumpkin_boots", 1, 3, 5),
                new Trade("noellesroles:halloween_bone_sword", 1, 6, 15),
                new Trade("noellesroles:halloween_soul_link", 1, 8, 20),
                new Trade("noellesroles:halloween_fright_gun", 1, 10, 30)));
    }

    public static class Loot {
        public String item;
        public int count, weight;
        public Loot() {}
        public Loot(String item, int count, int weight) {
            this.item = item;
            this.count = count;
            this.weight = weight;
        }
    }

    public static class Trade {
        public String item;
        public int count, pumpkins, coins;
        public Trade() {}
        public Trade(String item, int count, int pumpkins, int coins) {
            this.item = item;
            this.count = count;
            this.pumpkins = pumpkins;
            this.coins = coins;
        }
    }
}
