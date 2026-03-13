package com.revampes.AfterTimeFault.events.impl;

import net.minecraft.util.math.BlockPos;
import com.revampes.AfterTimeFault.events.Cancellable;

public class BreakBlockEvent extends Cancellable {
    private static final BreakBlockEvent INSTANCE = new BreakBlockEvent();

    public BlockPos blockPos;

    public static BreakBlockEvent get(BlockPos blockPos) {
        INSTANCE.setCancelled(false);
        INSTANCE.blockPos = blockPos;
        return INSTANCE;
    }
}
