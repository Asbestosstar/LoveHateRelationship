package com.asbestosstar.lovehaterelationship;

import com.asbestosstar.lovehaterelationship.client.model.VampireModel;
import com.asbestosstar.lovehaterelationship.client.renderer.VampireRenderer;
import com.asbestosstar.lovehaterelationship.entity.ModEntities;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = LoveHateRelationShip.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = LoveHateRelationShip.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class LoveHateRelationshipClient {

    public LoveHateRelationshipClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        LoveHateRelationShip.LOGGER.info("HELLO FROM CLIENT SETUP");
        LoveHateRelationShip.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VampireModel.LAYER_LOCATION, VampireModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.VAMPIRE.get(), VampireRenderer::new);
    }
}