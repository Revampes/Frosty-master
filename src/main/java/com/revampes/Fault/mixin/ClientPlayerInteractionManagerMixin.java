package com.revampes.Fault.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.revampes.Fault.Revampes;
import com.revampes.Fault.events.impl.BreakBlockEvent;
import com.revampes.Fault.events.impl.StartBreakingBlockEvent;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.utility.Utils;

import static com.revampes.Fault.Revampes.mc;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void onInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (!Utils.nullCheck() || !ModuleManager.stopPlacement.isEnabled()) {
            return;
        }

        ItemStack stack = player.getStackInHand(hand);

        if (ModuleManager.stopPlacement.isPlaceable(stack.getItem())) {
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, hitResult, 0));
            if (ModuleManager.stopPlacement.swing.isToggled()) {
                mc.player.swingHand(hand);
            }
            cir.setReturnValue(ActionResult.FAIL);
        }
    }

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (Revampes.EVENT_BUS.post(StartBreakingBlockEvent.get(pos, direction)).isCancelled()) {
            cir.cancel();
        }
    }

    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true)
    private void onBreakBlock(BlockPos blockPos, CallbackInfoReturnable<Boolean> cir) {
        if (Revampes.EVENT_BUS.post(BreakBlockEvent.get(blockPos)).isCancelled()) {
            cir.setReturnValue(false);
        }
    }
}
