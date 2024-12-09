package net.p3pp3rf1y.sophisticatedcore.init;

import net.minecraftforge.fml.ModList;
import net.p3pp3rf1y.sophisticatedcore.SophisticatedCore;
import net.p3pp3rf1y.sophisticatedcore.compat.CompatModIds;
import net.p3pp3rf1y.sophisticatedcore.compat.ICompat;
import net.p3pp3rf1y.sophisticatedcore.compat.craftingtweaks.CraftingTweaksCompat;
import net.p3pp3rf1y.sophisticatedcore.compat.curios.CuriosCompat;
import net.p3pp3rf1y.sophisticatedcore.compat.inventorysorter.InventorySorterCompat;
import net.p3pp3rf1y.sophisticatedcore.compat.itemborders.ItemBordersCompat;
import net.p3pp3rf1y.sophisticatedcore.compat.jei.JeiCompat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class ModCompat {
	private ModCompat() {}

	private static final Map<String, Supplier<Callable<ICompat>>> compatFactories = new HashMap<>();

	static {
		compatFactories.put(CompatModIds.JEI, () -> JeiCompat::new);
		compatFactories.put(CompatModIds.CRAFTING_TWEAKS, () -> CraftingTweaksCompat::new);
		compatFactories.put(CompatModIds.INVENTORY_SORTER, () -> InventorySorterCompat::new);
		compatFactories.put(CompatModIds.ITEM_BORDERS, () -> ItemBordersCompat::new);
		compatFactories.put(CompatModIds.CURIOS, () -> CuriosCompat::new);
		//compatFactories.put(CompatModIds.QUARK, () -> QuarkCompat::new); //TODO readd quark compat
	}

	public static void initCompats() {
		for (Map.Entry<String, Supplier<Callable<ICompat>>> entry : compatFactories.entrySet()) {
			if (ModList.get().isLoaded(entry.getKey())) {
				try {
					entry.getValue().get().call().setup();
				}
				catch (Exception e) {
					SophisticatedCore.LOGGER.error("Error instantiating compatibility ", e);
				}
			}
		}
	}
}
