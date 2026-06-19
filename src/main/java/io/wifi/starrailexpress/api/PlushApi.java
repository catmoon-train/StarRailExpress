package io.wifi.starrailexpress.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 皮肤 → 玩偶（plush）映射 API。
 * <p>
 * 约定：皮肤名 {@code X} 对应方块物品 {@code noellesroles:X_plush}。本类<b>不</b>新建任何模型或贴图，
 * 只复用仓库中已经存在的 plush 方块（见 {@code SREFumoBlocks}）；若注册表中没有对应 plush，则视为不可用。
 * <p>
 * 该 API 直接读取物品注册表，因此<b>新增 plush 无需改动本类</b>——只要按
 * {@code <name>_plush} 命名注册，就会被自动识别。
 */
public final class PlushApi {

    /** plush 物品所在命名空间（plush 方块在 {@code noellesroles} 命名空间下注册）。 */
    public static final String PLUSH_NAMESPACE = Noellesroles.MOD_ID;

    /** plush 注册名统一后缀。 */
    public static final String PLUSH_SUFFIX = "_plush";

    private PlushApi() {
    }

    /**
     * 把皮肤名规整为 plush 注册路径。接受 {@code "marisa"} 或 {@code "marisa_plush"}，
     * 统一转小写、去首尾空白并补齐 {@link #PLUSH_SUFFIX} 后缀。
     *
     * @return 规整后的注册路径；输入为空白时返回 {@code null}
     */
    private static String toPlushPath(String skinName) {
        String name = skinName.toLowerCase(Locale.ROOT).trim();
        if (name.isEmpty()) {
            return null;
        }
        return name.endsWith(PLUSH_SUFFIX) ? name : name + PLUSH_SUFFIX;
    }

    /**
     * 根据皮肤名取对应的 plush 物品。
     *
     * @param skinName 皮肤/角色名，如 {@code "marisa"}（也接受已带后缀的 {@code "marisa_plush"}）
     * @return 对应 plush 物品；仓库中没有同名 plush 时返回 {@link Optional#empty()}
     */
    public static Optional<Item> getPlushForSkin(String skinName) {
        if (skinName == null) {
            return Optional.empty();
        }
        String path = toPlushPath(skinName);
        if (path == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(PLUSH_NAMESPACE, path));
    }

    /**
     * 仓库中是否存在该皮肤名对应的 plush。
     */
    public static boolean hasPlush(String skinName) {
        return getPlushForSkin(skinName).isPresent();
    }

    /**
     * 创建一个对应皮肤的 plush 物品堆栈（数量 1）。
     *
     * @return 对应 plush 的 {@link ItemStack}；没有对应 plush 时返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack createPlushStack(String skinName) {
        return getPlushForSkin(skinName).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /**
     * 列出当前已注册的全部 plush 对应的皮肤名（去掉 {@link #PLUSH_SUFFIX} 后缀），按字典序排序。
     * <p>
     * 直接遍历物品注册表，新增 plush 会自动出现在结果中。
     */
    public static List<String> availableSkinNames() {
        List<String> names = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (id.getNamespace().equals(PLUSH_NAMESPACE) && id.getPath().endsWith(PLUSH_SUFFIX)) {
                String path = id.getPath();
                names.add(path.substring(0, path.length() - PLUSH_SUFFIX.length()));
            }
        }
        names.sort(null);
        return names;
    }
}
