package net.p3pp3rf1y.sophisticatedcore.upgrades.voiding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedcore.api.ISlotChangeResponseUpgrade;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.IItemHandlerSimpleInserter;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.*;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class VoidUpgradeWrapper extends UpgradeWrapperBase<VoidUpgradeWrapper, VoidUpgradeItem>
		implements IInsertResponseUpgrade, IFilteredUpgrade, ISlotChangeResponseUpgrade, ITickableUpgrade, IOverflowResponseUpgrade, ISlotLimitUpgrade {
	private final FilterLogic filterLogic;
	private final Set<Integer> slotsToVoid = new HashSet<>();
	private boolean shouldVoidOverflow;

	public VoidUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
		super(storageWrapper, upgrade, upgradeSaveHandler);
		filterLogic = new FilterLogic(upgrade, upgradeSaveHandler, upgradeItem.getFilterSlotCount());
		filterLogic.setAllowByDefault(true);
		setShouldVoidOverflowDefaultOrLoadFromNbt(false);
	}

	@Override
	public ItemStack onBeforeInsert(IItemHandlerSimpleInserter inventoryHandler, int slot, ItemStack stack, boolean simulate) {
		if (shouldVoidOverflow && inventoryHandler.getStackInSlot(slot).isEmpty() && (!filterLogic.shouldMatchNbt() || !filterLogic.shouldMatchDurability() || filterLogic.getPrimaryMatch() != PrimaryMatch.ITEM) && filterLogic.matchesFilter(stack)) {
			for (int s = 0; s < inventoryHandler.getSlots(); s++) {
				if (s == slot) {
					continue;
				}
				if (stackMatchesFilterStack(inventoryHandler.getStackInSlot(s), stack)) {
					return ItemStack.EMPTY;
				}
			}
			return stack;
		}

		return !shouldVoidOverflow && filterLogic.matchesFilter(stack) ? ItemStack.EMPTY : stack;
	}

	@Override
	public void onAfterInsert(IItemHandlerSimpleInserter inventoryHandler, int slot) {
		//noop
	}

	@Override
	public FilterLogic getFilterLogic() {
		return filterLogic;
	}

	public void setShouldWorkdInGUI(boolean shouldWorkdInGUI) {
		NBTHelper.setBoolean(upgrade, "shouldWorkInGUI", shouldWorkdInGUI);
		save();
	}

	public boolean shouldWorkInGUI() {
		return NBTHelper.getBoolean(upgrade, "shouldWorkInGUI").orElse(false);
	}

	public void setShouldVoidOverflow(boolean shouldVoidOverflow) {
		if (!shouldVoidOverflow && !upgradeItem.isVoidAnythingEnabled()) {
			return;
		}

		this.shouldVoidOverflow = shouldVoidOverflow;
		NBTHelper.setBoolean(upgrade, "shouldVoidOverflow", shouldVoidOverflow);
		save();
	}

	public void setShouldVoidOverflowDefaultOrLoadFromNbt(boolean shouldVoidOverflowDefault) {
		shouldVoidOverflow = !upgradeItem.isVoidAnythingEnabled() || NBTHelper.getBoolean(upgrade, "shouldVoidOverflow").orElse(shouldVoidOverflowDefault);
	}

	public boolean shouldVoidOverflow() {
		return !upgradeItem.isVoidAnythingEnabled() || shouldVoidOverflow;
	}

	@Override
	public void onSlotChange(IItemHandler inventoryHandler, int slot) {
		if (!shouldWorkInGUI() || shouldVoidOverflow()) {
			return;
		}

		ItemStack slotStack = inventoryHandler.getStackInSlot(slot);
		if (filterLogic.matchesFilter(slotStack)) {
			slotsToVoid.add(slot);
		}
	}

	@Override
	public void tick(@Nullable Entity entity, Level world, BlockPos pos) {
		if (slotsToVoid.isEmpty()) {
			return;
		}

		InventoryHandler storageInventory = storageWrapper.getInventoryHandler();
		for (int slot : slotsToVoid) {
			storageInventory.extractItem(slot, storageInventory.getStackInSlot(slot).getCount(), false);
		}

		slotsToVoid.clear();
	}

	@Override
	public boolean worksInGui() {
		return shouldWorkInGUI();
	}

	@Override
	public ItemStack onOverflow(ItemStack stack) {
		return filterLogic.matchesFilter(stack) ? ItemStack.EMPTY : stack;
	}

	@Override
	public boolean stackMatchesFilter(ItemStack stack) {
		return filterLogic.matchesFilter(stack);
	}

	public boolean isVoidAnythingEnabled() {
		return upgradeItem.isVoidAnythingEnabled();
	}

	@Override
	public int getSlotLimit() {
		return Integer.MAX_VALUE;
	}
}
