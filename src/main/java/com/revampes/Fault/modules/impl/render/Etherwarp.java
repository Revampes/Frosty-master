package com.revampes.Fault.modules.impl.render;

import com.revampes.Fault.events.impl.ReceivePacketEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.events.impl.SendPacketEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.utility.ItemUtils;
import com.revampes.Fault.utility.LocationUtils;
import com.revampes.Fault.utility.RenderUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.Objects;

public class Etherwarp extends Module {

    private final ButtonSetting render = new ButtonSetting("Show Guess", true);
    private final ColorSetting color = new ColorSetting("Color", new Color(255, 170, 0, 128));
    private final ButtonSetting renderFail = new ButtonSetting("Show when failed", true);
    private final ColorSetting failColor = new ColorSetting("Fail Color", new Color(255, 85, 85, 128));
    private final SelectSetting renderStyle = new SelectSetting("Render Style", 1, new String[]{"Filled", "Outline", "Filled Outline"});
    private final ButtonSetting useServerPosition = new ButtonSetting("Use Server Position", false);
    private final ButtonSetting fullBlock = new ButtonSetting("Full Block", false);
    private final ButtonSetting customSounds = new ButtonSetting("Custom Sounds", false);

    private EtherPos etherPos = null;

    public Etherwarp() {
        super("Etherwarp", category.Render);
        this.registerSetting(render);
        this.registerSetting(color);
        this.registerSetting(renderFail);
        this.registerSetting(failColor);
        this.registerSetting(renderStyle);
        this.registerSetting(useServerPosition);
        this.registerSetting(fullBlock);
        this.registerSetting(customSounds);
    }

    @Override
    public void guiUpdate() {
        this.color.setVisibilityCondition(() -> render.isToggled());
        this.renderFail.setVisibilityCondition(() -> render.isToggled());
        this.failColor.setVisibilityCondition(() -> render.isToggled() && renderFail.isToggled());
        this.renderStyle.setVisibilityCondition(() -> render.isToggled());
        this.useServerPosition.setVisibilityCondition(() -> render.isToggled());
        this.fullBlock.setVisibilityCondition(() -> render.isToggled());
    }

    @EventHandler
    public void onReceive(ReceivePacketEvent event) {
        if (!customSounds.isToggled()) return;
        if (event.getPacket() instanceof PlaySoundS2CPacket packet) {
            if (packet.getSound().value() == SoundEvents.ENTITY_ENDER_DRAGON_HURT && packet.getPitch() >= 0.53f && packet.getPitch() <= 0.54f) {
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, packet.getVolume(), 1.0f);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.currentScreen != null || !render.isToggled() || mc.player == null || mc.world == null) return;

        ItemStack mainHandItem = mc.player.getMainHandStack();
        if (mainHandItem.isEmpty()) return;

        String id = Utils.getCustomDataIId(mainHandItem.getComponents().toString());
        if (id == null || id.isEmpty()) return;
        
        boolean isEtherwarp = id.equals("ETHERWARP_CONDUIT") || id.equals("ASPECT_OF_THE_VOID");
        boolean isAote = id.equals("ASPECT_OF_THE_END");

        if (!isEtherwarp && !(isAote && mc.player.isSneaking())) {
            return;
        }

        if (!mc.player.isSneaking() && !id.equals("ETHERWARP_CONDUIT")) return;

        double distance = 57.0; 
        
        Vec3d position = useServerPosition.isToggled() ? new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()) : new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        etherPos = getEtherPos(position, distance, true);

        if (etherPos.succeeded == false && !renderFail.isToggled()) return;

        Color renderColor = etherPos.succeeded ? color.getColor() : failColor.getColor();
        
        if (etherPos.pos != null) {
            Box box;
            if (fullBlock.isToggled()) {
                box = new Box(etherPos.pos);
            } else {
                BlockState state = mc.world.getBlockState(etherPos.pos);
                if (!state.isAir()) {
                    VoxelShape outlineShape = state.getOutlineShape(mc.world, etherPos.pos);
                    if (outlineShape.isEmpty()) {
                        box = new Box(etherPos.pos);
                    } else {
                        box = outlineShape.getBoundingBox().offset(etherPos.pos);
                    }
                } else {
                    box = new Box(etherPos.pos);
                }
            }

            MatrixStack stack = event.getMatrix();
            String style = renderStyle.getOption();
            
            if (style.equals("Filled") || style.equals("Filled Outline")) {
                RenderUtils.drawBoxFilled(stack, box, renderColor);
            }
            if (style.equals("Outline") || style.equals("Filled Outline")) {
                RenderUtils.drawBox(stack, box, renderColor, 2.0);
            }
        }
    }

    @EventHandler
    public void onSend(SendPacketEvent event) {
        if (!LocationUtils.getCurrentArea().equals("SinglePlayer")) return;
        
        if (event.getPacket() instanceof PlayerInteractItemC2SPacket packet) {
            ItemStack mainHandItem = mc.player.getMainHandStack();
            if (mainHandItem.isEmpty()) return;

            String id = Utils.getCustomDataIId(mainHandItem.getComponents().toString());
            boolean isEtherwarp = id.equals("ETHERWARP_CONDUIT") || id.equals("ASPECT_OF_THE_VOID");

            if (isEtherwarp || (id.equals("ASPECT_OF_THE_END") && mc.player.isSneaking())) {
                if (!mc.player.isSneaking() && !id.equals("ETHERWARP_CONDUIT")) return;

                if (etherPos != null && etherPos.pos != null && etherPos.succeeded) {
                    BlockPos p = etherPos.pos;
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        new Vec3d(p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5), false, mc.player.horizontalCollision
                    ));
                    mc.player.setPos(p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5);
                    mc.player.setVelocity(0, 0, 0);

                    if (customSounds.isToggled()) {
                        mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    } else {
                        mc.player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_HURT, 1.0f, 0.53968257f);
                    }
                }
            }
        }
    }

    public static class EtherPos {
        public boolean succeeded;
        public BlockPos pos;
        
        public EtherPos(boolean succeeded, BlockPos pos) {
            this.succeeded = succeeded;
            this.pos = pos;
        }

        public static final EtherPos NONE = new EtherPos(false, null);
    }

    private EtherPos getEtherPos(Vec3d position, Double distance, boolean etherWarp) {
        if (mc.player == null || mc.world == null) return EtherPos.NONE;
        
        double eyeHeight = mc.player.isSneaking() ? 1.54 : 1.62; 
        Vec3d startPos = position.add(0, eyeHeight, 0);
        Vec3d endPos = startPos.add(mc.player.getRotationVector().multiply(distance));

        HitResult result = mc.world.raycast(new RaycastContext(
                startPos, endPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        ));

        if (result.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) result;
            BlockPos hitPos = blockHit.getBlockPos();
            
            // basic check to see if we can stand on it
            BlockPos head = hitPos.up(2);
            BlockPos feet = hitPos.up(1);
            
            if (mc.world.getBlockState(head).getCollisionShape(mc.world, head).isEmpty()
                && mc.world.getBlockState(feet).getCollisionShape(mc.world, head).isEmpty()) {
                return new EtherPos(true, hitPos);    
            } else {
                return new EtherPos(false, hitPos);
            }
        }

        return new EtherPos(false, BlockPos.ofFloored(endPos));
    }
}

