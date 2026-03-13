package com.revampes.AfterTimeFault.mixin;

import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.revampes.AfterTimeFault.Revampes;
import com.revampes.AfterTimeFault.events.impl.ChunkOcclusionEvent;

@Mixin(ChunkOcclusionDataBuilder.class)
public abstract class ChunkOcclusionDataBuilderMixin {
    @Inject(method = "markClosed", at = @At("HEAD"), cancellable = true)
    private void onMarkClosed(BlockPos pos, CallbackInfo ci) {
        ChunkOcclusionEvent event = Revampes.EVENT_BUS.post(ChunkOcclusionEvent.get());

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
