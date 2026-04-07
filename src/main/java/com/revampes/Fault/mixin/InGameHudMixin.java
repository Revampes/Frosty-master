package com.revampes.Fault.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.profiler.Profilers;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.revampes.Fault.Revampes;
import com.revampes.Fault.events.impl.Render2DEvent;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.utility.Utils;

import static com.revampes.Fault.Revampes.EVENT_BUS;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow
    public abstract void clear();

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        context.createNewRootLayer();

        Utils.unscaledProjection();

        EVENT_BUS.post(Render2DEvent.get(context, context.getScaledWindowWidth(), context.getScaledWindowWidth(), tickCounter.getTickProgress(true)));

        context.createNewRootLayer();

        Utils.scaledProjection();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderScoreboardSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (ModuleManager.noHudElement.isEnabled() && ModuleManager.noHudElement.scoreboard.isToggled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderScoreboardSidebar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ModuleManager.noHudElement.isEnabled() && ModuleManager.noHudElement.scoreboard.isToggled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
    private void onRenderTitle(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ModuleManager.noHudElement.isEnabled() && ModuleManager.noHudElement.title.isToggled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPortalOverlay(Lnet/minecraft/client/gui/DrawContext;F)V", at = @At("HEAD"), cancellable = true)
    private void onRenderPortalOverlay(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (ModuleManager.antiDebuff.isEnabled() && ModuleManager.antiDebuff.antiPortal.isToggled()) {
            ci.cancel();
        }
    }
}
