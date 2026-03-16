package com.revampes.Fault.modules.impl.kuudra;

import net.minecraft.util.math.BlockPos;

public enum Supply {
    Triangle(new BlockPos(-67, 77, -122), new BlockPos(-94, 78, -106)),
    X(new BlockPos(-142, 77, -151), new BlockPos(-106, 78, -112)),
    Equals(new BlockPos(-65, 76, -87), new BlockPos(-98, 78, -99)),
    Slash(new BlockPos(-113, 77, -68), new BlockPos(-106, 78, -99)),
    Shop(new BlockPos(-81, 76, -143), new BlockPos(-98, 78, -112)),
    xCannon(new BlockPos(-143, 76, -125), new BlockPos(-110, 78, -106)),
    Square(new BlockPos(-143, 76, -80), new BlockPos(0, 0, 0)),
    None(new BlockPos(0, 0, 0), new BlockPos(0, 0, 0));

    private final BlockPos pickUpSpot;
    private final BlockPos dropOffSpot;
    private boolean active = true;

    Supply(BlockPos pickUpSpot, BlockPos dropOffSpot) {
        this.pickUpSpot = pickUpSpot;
        this.dropOffSpot = dropOffSpot;
    }

    public BlockPos getPickUpSpot() {
        return pickUpSpot;
    }

    public BlockPos getDropOffSpot() {
        return dropOffSpot;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}