package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** 纸景专用不透明层：写入深度、关闭排序，不能与透明 debugQuads 共用绘制批次。 */
final class SettlementPaperRenderType extends RenderType {
    static final RenderType PAPER=create("sre_settlement_paper",DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,65536,false,false,CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(NO_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL).setOutputState(MAIN_TARGET).createCompositeState(false));

    private SettlementPaperRenderType() {
        super("unused",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});
    }
}
