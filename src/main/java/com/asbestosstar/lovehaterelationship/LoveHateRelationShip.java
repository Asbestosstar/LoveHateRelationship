package com.asbestosstar.lovehaterelationship;

import com.asbestosstar.lovehaterelationship.block.GarlicCropBlock;
import com.asbestosstar.lovehaterelationship.entity.ModEntities;
import com.asbestosstar.lovehaterelationship.entity.VampireEntity;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;  
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(LoveHateRelationShip.MODID)
public class LoveHateRelationShip {
    public static final String MODID = "lovehaterelationship";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    // Deferred Registers
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = ModEntities.ENTITY_TYPES;

    // Garlic Crop (CropBlock with age property)
    public static final DeferredBlock<Block> GARLIC_PLANT = BLOCKS.register(
            "garlic_plant",
            GarlicCropBlock::new
    );

    // Garlic Item — must be an ItemNameBlockItem so right-clicking places the crop
    public static final DeferredItem<Item> GARLIC = ITEMS.register(
            "garlic",
            () -> new ItemNameBlockItem(
                    GARLIC_PLANT.get(),
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(1)
                            .saturationModifier(0.3f)
                            .build())
            )
    );

    // Vampire Spawn Egg
    public static final DeferredItem<SpawnEggItem> VAMPIRE_SPAWN_EGG = ITEMS.register(
            "vampire_spawn_egg",
            () -> new SpawnEggItem(
                    ModEntities.VAMPIRE.get(),
                    0x3B0A0A,  // Primary colour
                    0x8B0000,  // Secondary colour
                    new Item.Properties()
            )
    );

    // Optional Custom Creative Tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.lovehaterelationship"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> GARLIC.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(GARLIC.get());            // seed+food
                        output.accept(VAMPIRE_SPAWN_EGG.get());
                        // No BlockItem for the crop itself (standard for crops)
                    }).build());
    
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, LoveHateRelationShip.MODID);
    
    public static final Holder<SoundEvent> vampire_creepy_laugh = SOUND_EVENTS.register(
            "vampire_creepy_laugh",
            // Takes in the registry name
            SoundEvent::createVariableRangeEvent
    );
    
    public static final Holder<SoundEvent> vampire_static_electricity = SOUND_EVENTS.register(
            "vampire_static_electricity",
            // Takes in the registry name
            SoundEvent::createVariableRangeEvent
    );
    
    public static final Holder<SoundEvent> vampire_bite = SOUND_EVENTS.register(
            "vampire_bite",
            // Takes in the registry name
            SoundEvent::createVariableRangeEvent
    );
    
    
    
    
    
    
    
    
    

    public LoveHateRelationShip(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus); 


        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::onAttributeCreate);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
 
    }

    public void onAttributeCreate(EntityAttributeCreationEvent event) {
        event.put(ModEntities.VAMPIRE.get(), VampireEntity.createAttributes().build());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(VAMPIRE_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(GARLIC);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
       // LOGGER.info("Love/Hate Relationship mod initialized!");
    }

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        var target = event.getTarget();

        if (!(target instanceof LivingEntity livingTarget) || player.level().isClientSide()) {
            return;
        }

        var nearbyVampires = player.level().getEntitiesOfClass(
                VampireEntity.class,
                player.getBoundingBox().inflate(32.0)
        );

        for (VampireEntity vampire : nearbyVampires) {
            // Check relationship with the *player* who attacked
            if (vampire.getRelationshipWith(player) > 300 && vampire.isAlive()) {
                vampire.setTarget(livingTarget);
            }
        }
    }

    public static ResourceLocation modLoc(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}