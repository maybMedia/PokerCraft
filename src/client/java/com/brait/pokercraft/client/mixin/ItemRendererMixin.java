package com.brait.pokercraft.client.mixin;

import com.brait.pokercraft.registry.ModItems;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @ModifyVariable(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int pokercraft$hostChipLight(int light, ItemStack stack, ModelTransformationMode mode,
                                         boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertices,
                                         int originalLight, int overlay, BakedModel model) {
        return stack.isOf(ModItems.HOST_CHIP) ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
    }
}
