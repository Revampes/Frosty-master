package com.revampes.Fault.utility;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

public class Layers {
    private static final RenderLayer GLOBAL_QUADS = RenderLayer.of("global_fill", RenderSetup.builder(Pipelines.GLOBAL_QUADS_PIPELINE).build());
    private static final RenderLayer GLOBAL_LINES = RenderLayer.of("global_lines", RenderSetup.builder(Pipelines.GLOBAL_LINES_PIPELINE).build());

    public static RenderLayer getGlobalQuads() {
        return GLOBAL_QUADS;
    }

    public static RenderLayer getGlobalLines(double width) {
        // 1.21.11 line width is pipeline-driven; keep parameter for compatibility.
        return GLOBAL_LINES;
    }
}
