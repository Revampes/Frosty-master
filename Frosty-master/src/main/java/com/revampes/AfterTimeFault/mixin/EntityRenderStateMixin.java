package com.revampes.AfterTimeFault.mixin;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.revampes.AfterTimeFault.interfaces.IEntityRenderState;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements IEntityRenderState {
    @Unique
    private Entity entity;

    @Override
    public Entity revampes$getEntity() {
        return entity;
    }

    @Override
    public void revampes$setEntity(Entity entity) {
        this.entity = entity;
    }
}

