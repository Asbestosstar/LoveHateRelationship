package com.asbestosstar.lovehaterelationship.client.model;

import com.asbestosstar.lovehaterelationship.LoveHateRelationShip;
import com.asbestosstar.lovehaterelationship.entity.VampireEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

public class VampireModel extends HumanoidModel<VampireEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(LoveHateRelationShip.modLoc("vampire"), "main");

    public VampireModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition partdefinition = meshdefinition.getRoot();



        return LayerDefinition.create(meshdefinition, 64, 32);
    }

    @Override
    public void setupAnim(VampireEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

   
        net.minecraft.world.entity.LivingEntity currentTarget = entity.getTarget();
        int relToTarget = (currentTarget != null) ? entity.getRelationshipWith(currentTarget) : 0; // Default to neutral if no target

        if (relToTarget > 300) {
            // Friendly: slight head tilt
            this.head.yRot += Mth.sin(ageInTicks * 0.1f) * 0.1f;
        }
    }
}