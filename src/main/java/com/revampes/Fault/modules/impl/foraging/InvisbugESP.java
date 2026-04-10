package com.revampes.Fault.modules.impl.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.particle.DamageParticle;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.revampes.Fault.events.impl.AddParticleEvent;
import net.minecraft.particle.ParticleTypes;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.utility.RenderUtils;
import com.revampes.Fault.utility.Utils;
import com.revampes.Fault.utility.LocationUtils;

import java.awt.Color;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InvisbugESP extends Module {

   private static Color color = Color.RED;
   private static final Vec3d camp = new Vec3d(-627.0D, 117.0D, 49.0D);
   private static Map<Box, Long> currentInvisbug = new ConcurrentHashMap<>();

   public InvisbugESP() {
      super("InvisbugESP", category.Foraging);
   }

   @Override
   public void onEnable() {
       super.onEnable();
       currentInvisbug.clear();
   }

   @EventHandler
   public void onParticle(AddParticleEvent event) {
      if (mc.player != null && mc.world != null) {
         if (event.particle instanceof DamageParticle) {
            Box box = event.particle.getBoundingBox();

            // Unconditionally add for testing
            currentInvisbug.put(box, System.currentTimeMillis());
         }
      }
   }

   @EventHandler
   public void onRender(Render3DEvent event) {
      if (!Utils.nullCheck()) return;

      String currentArea = LocationUtils.getCurrentArea();
      if (!currentArea.equals("Galatea")) return;

      Iterator<Map.Entry<Box, Long>> iterator = currentInvisbug.entrySet().iterator();
      long now = System.currentTimeMillis();

      while (iterator.hasNext()) {
         Map.Entry<Box, Long> entry = iterator.next();
         
         // Remove if it has been stored for more than 1.5 seconds (1500ms)
         if (now - entry.getValue() >= 1500L) {
             iterator.remove();
             continue;
         }

         Box box = entry.getKey();
         // Expand the box slightly because particle hitboxes are tiny (from 0.2 to 0.8 block radius practically)
         RenderUtils.drawBox(event.getMatrix(), box.expand(0.3, 0.3, 0.3), color, 2.0f);
         RenderUtils.drawBoxFilled(event.getMatrix(), box.expand(0.3, 0.3, 0.3), new Color(color.getRed(), color.getGreen(), color.getBlue(), 100));
      }
   }
}
