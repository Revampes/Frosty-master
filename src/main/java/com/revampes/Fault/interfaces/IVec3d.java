package com.revampes.Fault.interfaces;

import net.minecraft.util.math.Vec3i;
import org.joml.Vector3d;

public interface IVec3d {
    void revampes$set(double x, double y, double z);

    default void revampes$set(Vec3i vec) {
        revampes$set(vec.getX(), vec.getY(), vec.getZ());
    }

    default void revampes$set(Vector3d vec) {
        revampes$set(vec.x, vec.y, vec.z);
    }

    void revampes$setXZ(double x, double z);

    void revampes$setY(double y);
}
