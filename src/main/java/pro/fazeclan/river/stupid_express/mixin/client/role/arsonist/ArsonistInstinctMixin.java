package pro.fazeclan.river.stupid_express.mixin.client.role.arsonist;

import io.wifi.starrailexpress.client.SREClient;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 杀手本能 Mixin
 *
 * 处理以下功能：
 * 1. 跟踪者：杀手透视时显示跟踪者颜色（跟踪者本身是杀手，无需额外处理 isKiller）
 * 2. 爱慕者：类似小丑，能使用本能侦查，也能被杀手本能侦查到
 * 3. 傀儡师：操控假人时可以使用本能，显示渐变色
 * 4. 杀手本能颜色改为渐变色效果
 */
@Mixin(SREClient.class)
public class ArsonistInstinctMixin {
}