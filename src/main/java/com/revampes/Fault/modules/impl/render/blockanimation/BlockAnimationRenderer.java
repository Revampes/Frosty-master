package com.revampes.Fault.modules.impl.render.blockanimation;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import com.revampes.Fault.utility.BlockAnimationUtils;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;
import net.minecraft.entity.player.PlayerEntity;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.mixin.HeldItemRendererAccessor;

public final class BlockAnimationRenderer {
    private BlockAnimationRenderer() {}

    public enum RenderResult {
        PASS,
        INTERRUPT
    }

    public static RenderResult renderFirstPerson(HeldItemRenderer itemInHandRenderer, 
    Hand interactionHand, 
    PlayerEntity player, 
    Arm arm,
    ItemStack itemStack,
    MatrixStack poseStack,
    OrderedRenderCommandQueue submitNodeCollector,
    int combinedLight,
    float partialTicks,
    float swingProgress,
    float equipProgress) {
        
        if (!ModuleManager.blockAnimation.isEnabled()) return RenderResult.PASS;
        if (!BlockAnimationUtils.isPlayerBlockingWithSword(player)) return RenderResult.PASS;

        poseStack.push();

        boolean mainHand = interactionHand == Hand.MAIN_HAND;
        Arm handSide = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean isHandSideRight = handSide == Arm.RIGHT;

        float actualSwingProgress = swingProgress;
        if (BlockAnimation.isSwinging() && BlockAnimation.getSwingHand() == interactionHand) {
            actualSwingProgress = BlockAnimation.getSwingProgress(partialTicks);
        }

        HeldItemRendererAccessor accessor = (HeldItemRendererAccessor) itemInHandRenderer;
        // Apply equip offset first as is standard in yarn's renderFirstPersonItem for swords
        accessor.revampes$applyEquipOffset(poseStack, handSide, equipProgress);
        // Then apply our manual swing offset
        accessor.revampes$applySwingOffset(poseStack, handSide, actualSwingProgress);

        applyFirstPersonBlockTransform(poseStack, handSide);

        itemInHandRenderer.renderItem(player, itemStack, isHandSideRight ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, poseStack, submitNodeCollector, combinedLight);
        
        poseStack.pop();

        return RenderResult.INTERRUPT;
    }

    public static void applyFirstPersonBlockTransform(MatrixStack matrixStack, Arm hand) {
        applyBlockingTransformOnly(matrixStack, hand);
    }

    public static void applyBlockingTransformOnly(MatrixStack matrixStack, Arm hand) { 
        int direction = hand == Arm.RIGHT ? 1 : -1;
        matrixStack.translate(direction * -0.14142136F, 0.08F, 0.14142136F);
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * 13.365F));
        matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(direction * 78.05F));
    }
}
