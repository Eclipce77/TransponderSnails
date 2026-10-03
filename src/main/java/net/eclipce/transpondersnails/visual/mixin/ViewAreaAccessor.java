package net.eclipce.transpondersnails.visual.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;

/** ViewArea#getRenderChunkAt is protected in 1.20.1. */
@Mixin(ViewArea.class)
public interface ViewAreaAccessor {

    @Nullable
    @Invoker("getRenderChunkAt")
    ChunkRenderDispatcher.RenderChunk transpondersnails$getRenderChunkAt(BlockPos pos);
}
