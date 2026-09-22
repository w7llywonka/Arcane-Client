package dev.arcaneclient.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("LINES_SNIPPET")
    static RenderPipeline.Snippet arcaneclient$linesSnippet() {
        throw new AssertionError();
    }

    @Accessor("OIT_LINES_SNIPPET")
    static RenderPipeline.Snippet arcaneclient$oitLinesSnippet() {
        throw new AssertionError();
    }

    @Accessor("DEBUG_FILLED_SNIPPET")
    static RenderPipeline.Snippet arcaneclient$debugFilledSnippet() {
        throw new AssertionError();
    }

    @Accessor("OIT_DEBUG_FILLED_SNIPPET")
    static RenderPipeline.Snippet arcaneclient$oitDebugFilledSnippet() {
        throw new AssertionError();
    }

    @Invoker("register")
    static RenderPipeline arcaneclient$register(RenderPipeline pipeline) {
        throw new AssertionError();
    }

    @Invoker("register")
    static OitPipelineSet arcaneclient$registerOit(OitPipelineSet pipelines) {
        throw new AssertionError();
    }
}
