package com.asbestosstar.lovehaterelationship.client.renderer;

import com.asbestosstar.lovehaterelationship.LoveHateRelationShip;
import com.asbestosstar.lovehaterelationship.client.model.VampireModel;
import com.asbestosstar.lovehaterelationship.entity.VampireEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class VampireRenderer extends MobRenderer<VampireEntity, VampireModel> {
    private static final String TEXTURE_PATH = "textures/entity/vampire/vampire_%d.png";

    public VampireRenderer(EntityRendererProvider.Context context) {
        super(context, new VampireModel(context.bakeLayer(VampireModel.LAYER_LOCATION)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(VampireEntity entity) {
        int variant = Mth.clamp(entity.getVariant(), 0, 3);
        return LoveHateRelationShip.modLoc(String.format(TEXTURE_PATH, variant));
    }


}