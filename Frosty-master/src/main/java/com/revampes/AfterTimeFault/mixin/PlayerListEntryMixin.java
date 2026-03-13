package com.revampes.AfterTimeFault.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.revampes.AfterTimeFault.Revampes;
import com.revampes.AfterTimeFault.modules.ModuleManager;

@Mixin(PlayerListEntry.class)
public class PlayerListEntryMixin {
    @Shadow
    private GameProfile profile;

    @Inject(method = "getSkinTextures", at = @At("TAIL"), cancellable = true)
    private void getSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        SkinTextures original = cir.getReturnValue();
        if (Revampes.mc.player != null && profile.id().equals(Revampes.mc.player.getUuid())) {
            if (ModuleManager.cape.isEnabled()) {
                Identifier capeTexture = ModuleManager.cape.getCurrentCape();

                if (capeTexture != null) {
                    cir.setReturnValue(new SkinTextures(
                            original.body(),
                            new AssetInfo.TextureAssetInfo(capeTexture, capeTexture), new AssetInfo.TextureAssetInfo(capeTexture, capeTexture),
                            original.model(), original.secure()));
                }
            } else {
                cir.setReturnValue(original);
            }
        }
    }
}
