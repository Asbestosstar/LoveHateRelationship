package com.asbestosstar.lovehaterelationship.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
	private ModTags() {
	}

	public static final class Blocks {
		// Specific “garlic crop” tags
		public static final TagKey<Block> NEOFORGE_GARLIC_CROP = tag("neoforge", "crops/garlic");
		public static final TagKey<Block> C_GARLIC_CROP = tag("c", "crops/garlic");

		// Generic “any crop” tags (optional but handy as a fallback)
		public static final TagKey<Block> NEOFORGE_ANY_CROP = tag("neoforge", "crops");
		public static final TagKey<Block> C_ANY_CROP = tag("c", "crops");
		public static final TagKey<Block> MC_ANY_CROP = tag("minecraft", "crops");

		private static TagKey<Block> tag(String namespace, String path) {
			return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, path));
		}
	}
}
