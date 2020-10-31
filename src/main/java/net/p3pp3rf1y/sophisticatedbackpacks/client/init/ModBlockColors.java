package net.p3pp3rf1y.sophisticatedbackpacks.client.init;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.p3pp3rf1y.sophisticatedbackpacks.blocks.tile.BackpackTileEntity;
import net.p3pp3rf1y.sophisticatedbackpacks.util.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.util.WorldHelper;

import static net.p3pp3rf1y.sophisticatedbackpacks.init.ModBlocks.*;

@OnlyIn(Dist.CLIENT)
public class ModBlockColors {

	private static final int DEFAULT_COLOR = 13394234;

	private ModBlockColors() {}

	public static void init() {
		BlockColors blockColors = Minecraft.getInstance().getBlockColors();

		blockColors.register((state, blockDisplayReader, pos, tintIndex) -> {
			if (tintIndex < 0 || tintIndex > 1) {
				return -1;
			}
			return WorldHelper.getTile(blockDisplayReader, pos, BackpackTileEntity.class)
					.map(te -> tintIndex == 0 ? te.getBackpackWrapper().map(BackpackWrapper::getClothColor).orElse(DEFAULT_COLOR) : te.getBackpackWrapper().map(BackpackWrapper::getBorderColor).orElse(DEFAULT_COLOR)).orElse(DEFAULT_COLOR);
		}, BACKPACK, IRON_BACKPACK, GOLD_BACKPACK, DIAMOND_BACKPACK);
	}
}
