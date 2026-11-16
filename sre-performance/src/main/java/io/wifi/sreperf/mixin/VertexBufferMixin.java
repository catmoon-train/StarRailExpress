package io.wifi.sreperf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import io.wifi.sreperf.CaptureController;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(VertexBuffer.class)
abstract class VertexBufferMixin {
    @WrapMethod(method = "upload")
    private void sreperf$upload(MeshData mesh, Operation<Void> original) {
        long token = CaptureController.begin("vertex_upload");
        long bytes = 0;
        if (token != 0) {
            bytes = mesh.vertexBuffer().remaining();
            var indices = mesh.indexBuffer();
            if (indices != null) bytes += indices.remaining();
        }
        try { original.call(mesh); }
        finally { CaptureController.end("vertex_upload", token, bytes); }
    }
}
