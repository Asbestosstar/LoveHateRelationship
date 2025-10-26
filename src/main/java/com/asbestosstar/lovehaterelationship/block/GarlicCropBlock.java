package com.asbestosstar.lovehaterelationship.block;

import static com.asbestosstar.lovehaterelationship.LoveHateRelationShip.GARLIC;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public class GarlicCropBlock extends CropBlock {
    public GarlicCropBlock() {
        super(BlockBehaviour.Properties
                .of()
                .mapColor(MapColor.PLANT)
                .noCollission()
                .randomTicks()
                .instabreak()
                .sound(SoundType.CROP));
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return GARLIC.get();
    }

    @Override
    public ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit
    ) {
        if (getAge(state) >= getMaxAge()) {
            if (!level.isClientSide) {
                int drop = 1 + level.random.nextInt(3); // 1–3 garlic
                popResource(level, pos, new ItemStack(GARLIC.get(), drop));
                level.setBlock(pos, this.getStateForAge(0), 2); // replant
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.9f, 1.1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }


}
