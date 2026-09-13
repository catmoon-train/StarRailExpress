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

package io.wifi.starrailexpress.client.particle;

import io.wifi.starrailexpress.index.SREDecorationBlocks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 「假太阳」方块使用的持久粒子。
 *
 * <p>
 * 与普通粒子不同，它不会随时间老化/淡出：仅当锚点方块
 * （{@link SREDecorationBlocks#FAKE_SUN}）不存在时（被破坏、区块卸载）才结束自己。
 * 每个方块只生成一个粒子（见 {@code FakeSunBlockEntity#clientTick}），
 * 之后每刻只做一次方块查询，因此几乎没有性能开销。
 */
public class SunParticle extends TextureSheetParticle {
    /** 太阳直径（格），4 格 ≈ 4 个方块宽。 */
    public static final float SUN_SIZE = 4.0F;

    /** 锚点：假太阳方块的位置，用于判断方块是否还在。 */
    private final BlockPos anchor;

    protected SunParticle(ClientLevel level, double x, double y, double z, BlockPos anchor) {
        super(level, x, y, z);
        this.anchor = anchor.immutable();
        this.quadSize = SUN_SIZE;
        this.lifetime = Integer.MAX_VALUE;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.setSize(SUN_SIZE, SUN_SIZE);
    }

    @Override
    public void tick() {
        // 有意不调用 super.tick()：这样粒子不会老化、不会淡出；
        // 只要锚点方块消失（被破坏 / 区块卸载）就结束渲染。
        if (this.level.getBlockState(this.anchor).getBlock() != SREDecorationBlocks.FAKE_SUN) {
            this.remove();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public Factory(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double velocityX, double velocityY, double velocityZ) {
            SunParticle particle = new SunParticle(level, x, y, z, BlockPos.containing(x, y, z));
            particle.pickSprite(this.spriteSet);
            return particle;
        }
    }
}
