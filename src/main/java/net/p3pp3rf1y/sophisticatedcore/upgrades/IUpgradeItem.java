package net.p3pp3rf1y.sophisticatedcore.upgrades;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.TranslationHelper;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeSlotChangeResult;
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public interface IUpgradeItem<T extends IUpgradeWrapper> {
	UpgradeType<T> getType();

	default UpgradeSlotChangeResult canAddUpgradeTo(IStorageWrapper storageWrapper, ItemStack upgradeStack, boolean firstLevelStorage, boolean isClientSide) {
		UpgradeSlotChangeResult result = checkUpgradePerStorageTypeLimit(storageWrapper);

		if (!result.isSuccessful()) {
			return result;
		}

		result = checkForConflictingUpgrades(storageWrapper, getUpgradeConflicts(), -1);
		if (!result.isSuccessful()) {
			return result;
		}

		result = checkThisForConflictsWithExistingUpgrades(upgradeStack, storageWrapper, -1);
		if (!result.isSuccessful()) {
			return result;
		}

		return checkExtraInsertConditions(upgradeStack, storageWrapper, isClientSide, null);
	}

	default UpgradeSlotChangeResult checkThisForConflictsWithExistingUpgrades(ItemStack upgradeStack, IStorageWrapper storageWrapper, int excludeUpgradeSlot) {
		AtomicReference<UpgradeSlotChangeResult> result = new AtomicReference<>(new UpgradeSlotChangeResult.Success());
		InventoryHelper.iterate(storageWrapper.getUpgradeHandler(), (slot, stack) -> {
			if (slot != excludeUpgradeSlot && stack.getItem() instanceof IUpgradeItem<?> upgradeItem) {
				for (UpgradeConflictDefinition conflictDefinition : upgradeItem.getUpgradeConflicts()) {
					//only checking for single item conflicts here would need to be expanded to support multiple,
					// but there isn't a case like that at the moment because the other conflict check (the one where item inserted checks items that exist) covers all multiple item cases
					if (conflictDefinition.maxConflictingAllowed() == 0 && conflictDefinition.isConflictingItem.test(upgradeStack.getItem())) {
						result.set(new UpgradeSlotChangeResult.Fail(conflictDefinition.otherBeingAddedErrorMessage, Set.of(slot), Set.of(), Set.of()));
						return;
					}
				}
			}
		}, () -> !result.get().isSuccessful());
		return result.get();
	}

	private UpgradeSlotChangeResult checkForConflictingUpgrades(IStorageWrapper storageWrapper, List<UpgradeConflictDefinition> upgradeConflicts, int excludeUpgradeSlot) {
		for (UpgradeConflictDefinition conflictDefinition : upgradeConflicts) {
			AtomicInteger conflictingCount = new AtomicInteger(0);
			Set<Integer> conflictingSlots = new HashSet<>();
			InventoryHelper.iterate(storageWrapper.getUpgradeHandler(), (slot, stack) -> {
				if (slot != excludeUpgradeSlot && !stack.isEmpty() && conflictDefinition.isConflictingItem.test(stack.getItem())) {
					conflictingCount.incrementAndGet();
					conflictingSlots.add(slot);
				}
			});

			if (conflictingCount.get() > conflictDefinition.maxConflictingAllowed) {
				return new UpgradeSlotChangeResult.Fail(conflictDefinition.errorMessage, conflictingSlots, Set.of(), Set.of());
			}
		}
		return new UpgradeSlotChangeResult.Success();
	}

	List<UpgradeConflictDefinition> getUpgradeConflicts();

	private UpgradeSlotChangeResult checkUpgradePerStorageTypeLimit(IStorageWrapper storageWrapper) {
		int upgradesPerStorage = getUpgradesPerStorage(storageWrapper.getStorageType());
		int upgradesInGroupPerStorage = getUpgradesInGroupPerStorage(storageWrapper.getStorageType());

		if (upgradesPerStorage == Integer.MAX_VALUE && upgradesInGroupPerStorage == Integer.MAX_VALUE) {
			return new UpgradeSlotChangeResult.Success();
		}

		if (upgradesPerStorage == 0) {
			return new UpgradeSlotChangeResult.Fail(TranslationHelper.INSTANCE.translError("add.upgrade_not_allowed", getName(), storageWrapper.getDisplayName()), Set.of(), Set.of(), Set.of());
		} else if (upgradesInGroupPerStorage == 0) {
			return new UpgradeSlotChangeResult.Fail(TranslationHelper.INSTANCE.translError("add.upgrade_not_allowed", Component.translatable(getUpgradeGroup().translName()), storageWrapper.getDisplayName()), Set.of(), Set.of(), Set.of());
		}

		Set<Integer> slotsWithUpgrade = new HashSet<>();
		InventoryHelper.iterate(storageWrapper.getUpgradeHandler(), (slot, stack) -> {
			if (stack.getItem() == this) {
				slotsWithUpgrade.add(slot);
			}
		});

		if (slotsWithUpgrade.size() >= upgradesPerStorage) {
			return new UpgradeSlotChangeResult.Fail(TranslationHelper.INSTANCE.translError("add.only_x_upgrades_allowed", upgradesPerStorage, getName(), storageWrapper.getDisplayName(), upgradesPerStorage), slotsWithUpgrade, Set.of(), Set.of());
		}

		Set<Integer> slotsWithUgradeGroup = new HashSet<>();
		InventoryHelper.iterate(storageWrapper.getUpgradeHandler(), (slot, stack) -> {
			if (stack.getItem() instanceof IUpgradeItem<?> upgradeItem && upgradeItem.getUpgradeGroup() == getUpgradeGroup()) {
				slotsWithUgradeGroup.add(slot);
			}
		});

		if (slotsWithUgradeGroup.size() >= upgradesInGroupPerStorage) {
			return new UpgradeSlotChangeResult.Fail(TranslationHelper.INSTANCE.translError("add.only_x_upgrades_allowed", upgradesInGroupPerStorage, Component.translatable(getUpgradeGroup().translName()), storageWrapper.getDisplayName()), slotsWithUgradeGroup, Set.of(), Set.of());
		}

		return new UpgradeSlotChangeResult.Success();
	}

	default UpgradeSlotChangeResult canRemoveUpgradeFrom(IStorageWrapper storageWrapper, boolean isClientSide, Player player) {
		return canRemoveUpgradeFrom(storageWrapper, isClientSide);
	}

	default UpgradeSlotChangeResult canRemoveUpgradeFrom(IStorageWrapper storageWrapper, boolean isClientSide) {
		return new UpgradeSlotChangeResult.Success();
	}

	default UpgradeSlotChangeResult canSwapUpgradeFor(ItemStack upgradeStackToPut, int upgradeSlot, IStorageWrapper storageWrapper, boolean isClientSide) {
		if (upgradeStackToPut.getItem() == this) {
			return new UpgradeSlotChangeResult.Success();
		}

		if (upgradeStackToPut.getItem() instanceof IUpgradeItem<?> upgradeToPut) {
			int upgradesPerStorage = upgradeToPut.getUpgradesPerStorage(storageWrapper.getStorageType());
			int upgradesInGroupPerStorage = upgradeToPut.getUpgradesInGroupPerStorage(storageWrapper.getStorageType());

			if (upgradesPerStorage < upgradesInGroupPerStorage) {
				UpgradeSlotChangeResult result = upgradeToPut.checkUpgradePerStorageTypeLimit(storageWrapper);
				if (!result.isSuccessful()) {
					return result;
				}
			} else {
				if (upgradeToPut.getUpgradeGroup() != getUpgradeGroup()) {
					UpgradeSlotChangeResult result = upgradeToPut.checkUpgradePerStorageTypeLimit(storageWrapper);
					if (!result.isSuccessful()) {
						return result;
					}
				}
			}

			UpgradeSlotChangeResult result = checkForConflictingUpgrades(storageWrapper, upgradeToPut.getUpgradeConflicts(), upgradeSlot);
			if (!result.isSuccessful()) {
				return result;
			}
			return upgradeToPut.checkExtraInsertConditions(upgradeStackToPut, storageWrapper, isClientSide, this);
		}

		return new UpgradeSlotChangeResult.Success();
	}

	default UpgradeSlotChangeResult checkExtraInsertConditions(ItemStack upgradeStack, IStorageWrapper storageWrapper, boolean isClientSide, @Nullable IUpgradeItem<?> upgradeInSlot) {
		return new UpgradeSlotChangeResult.Success();
	}

	default int getInventoryColumnsTaken() {
		return 0;
	}

	default ItemStack getCleanedUpgradeStack(ItemStack upgradeStack) {
		return upgradeStack;
	}

	int getUpgradesPerStorage(String storageType);

	int getUpgradesInGroupPerStorage(String storageType);

	default UpgradeGroup getUpgradeGroup() {
		return UpgradeGroup.NONE;
	}

	Component getName();

	record UpgradeConflictDefinition(Predicate<Item> isConflictingItem, int maxConflictingAllowed,
									 Component errorMessage, Component otherBeingAddedErrorMessage) {
		public UpgradeConflictDefinition(Predicate<Item> isConflictingItem, int maxConflictingAllowed, Component errorMessage) {
			this(isConflictingItem, maxConflictingAllowed, errorMessage, errorMessage);
		}
	}
}
