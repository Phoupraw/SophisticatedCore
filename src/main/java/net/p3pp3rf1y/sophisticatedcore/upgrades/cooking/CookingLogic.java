package net.p3pp3rf1y.sophisticatedcore.upgrades.cooking;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.items.ItemStackHandler;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;
import net.p3pp3rf1y.sophisticatedcore.util.RecipeHelper;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class CookingLogic<T extends AbstractCookingRecipe> {
	private final ItemStack upgrade;
	private final Consumer<ItemStack> saveHandler;

	private ItemStackHandler cookingInventory = null;
	public static final int COOK_INPUT_SLOT = 0;
	public static final int COOK_OUTPUT_SLOT = 2;
	public static final int FUEL_SLOT = 1;
	@Nullable
	private T cookingRecipe = null;
	private boolean cookingRecipeInitialized = false;

	private final float burnTimeModifier;
	private final Predicate<ItemStack> isFuel;
	private final Predicate<ItemStack> isInput;
	private final double cookingSpeedMultiplier;
	private final double fuelEfficiencyMultiplier;
	private final RecipeType<T> recipeType;

	private boolean paused = false;
	private long remainingCookTime = 0;
	private long remainingBurnTime = 0;

	public CookingLogic(ItemStack upgrade, Consumer<ItemStack> saveHandler, CookingUpgradeConfig cookingUpgradeConfig, RecipeType<T> recipeType, float burnTimeModifier) {
		this(upgrade, saveHandler, s -> getBurnTime(s, recipeType, burnTimeModifier) > 0, s -> RecipeHelper.getCookingRecipe(s, recipeType).isPresent(), cookingUpgradeConfig, recipeType, burnTimeModifier);
	}

	public CookingLogic(ItemStack upgrade, Consumer<ItemStack> saveHandler, Predicate<ItemStack> isFuel, Predicate<ItemStack> isInput, CookingUpgradeConfig cookingUpgradeConfig, RecipeType<T> recipeType, float burnTimeModifier) {
		this.upgrade = upgrade;
		this.saveHandler = saveHandler;
		this.isFuel = isFuel;
		this.isInput = isInput;
		cookingSpeedMultiplier = cookingUpgradeConfig.cookingSpeedMultiplier.get();
		fuelEfficiencyMultiplier = cookingUpgradeConfig.fuelEfficiencyMultiplier.get();
		this.recipeType = recipeType;
		this.burnTimeModifier = burnTimeModifier;
	}

	private void save() {
		saveHandler.accept(upgrade);
	}

	public boolean tick(Level level) {
		updateTimes(level);

		AtomicBoolean didSomething = new AtomicBoolean(true);
		if (isBurning(level) || readyToStartCooking()) {
			Optional<T> fr = getCookingRecipe();
			if (fr.isEmpty() && isCooking()) {
				setIsCooking(false);
			}
			fr.ifPresent(recipe -> {
				updateFuel(level, recipe);

				if (isBurning(level) && canSmelt(recipe, level)) {
					updateCookingProgress(level, recipe);
				} else if (!isBurning(level)) {
					didSomething.set(false);
				}
			});
		}

		if (!isBurning(level) && isCooking()) {
			updateCookingCooldown(level);
		} else {
			didSomething.set(false);
		}
		return didSomething.get();
	}

	private void updateTimes(Level world) {
		if (paused) {
			unpause(world);
			return;
		}

		if (isBurning(world)) {
			remainingBurnTime = getBurnTimeFinish() - world.getGameTime();
		} else {
			remainingBurnTime = 0;
		}
		if (isCooking()) {
			remainingCookTime = getCookTimeFinish() - world.getGameTime();
		} else {
			remainingCookTime = 0;
		}
	}

	private void unpause(Level world) {
		paused = false;

		if (remainingBurnTime > 0) {
			setBurnTimeFinish(world.getGameTime() + remainingBurnTime);
		}
		if (remainingCookTime > 0) {
			setCookTimeFinish(world.getGameTime() + remainingCookTime);
			setIsCooking(true);
		}
	}

	public boolean isBurning(Level world) {
		return getBurnTimeFinish() >= world.getGameTime();
	}

	private Optional<T> getCookingRecipe() {
		if (!cookingRecipeInitialized) {
			cookingRecipe = RecipeHelper.getCookingRecipe(getCookInput(), recipeType).orElse(null);
			cookingRecipeInitialized = true;
		}
		return Optional.ofNullable(cookingRecipe);
	}

	private void updateCookingCooldown(Level world) {
		if (getRemainingCookTime(world) + 2 > getCookTimeTotal()) {
			setIsCooking(false);
		} else {
			setCookTimeFinish(world.getGameTime() + Math.min(getRemainingCookTime(world) + 2, getCookTimeTotal()));
		}
	}

	private void updateCookingProgress(Level level, T cookingRecipe) {
		if (isCooking() && finishedCooking(level)) {
			smelt(cookingRecipe, level);
			if (canSmelt(cookingRecipe, level)) {
				setCookTime(level, (int) (cookingRecipe.getCookingTime() * (1 / cookingSpeedMultiplier)));
			} else {
				setIsCooking(false);
			}
		} else if (!isCooking()) {
			setIsCooking(true);
			setCookTime(level, (int) (cookingRecipe.getCookingTime() * (1 / cookingSpeedMultiplier)));
		}
	}

	private boolean finishedCooking(Level world) {
		return getCookTimeFinish() <= world.getGameTime();
	}

	private boolean readyToStartCooking() {
		return !getFuel().isEmpty() && !getCookInput().isEmpty();
	}

	private void smelt(Recipe<?> recipe, Level level) {
		if (!canSmelt(recipe, level)) {
			return;
		}

		ItemStack input = getCookInput();
		ItemStack recipeOutput = recipe.getResultItem(level.registryAccess());
		ItemStack output = getCookOutput();
		if (output.isEmpty()) {
			setCookOutput(recipeOutput.copy());
		} else if (output.getItem() == recipeOutput.getItem()) {
			output.grow(recipeOutput.getCount());
			setCookOutput(output);
		}

		if (input.getItem() == Blocks.WET_SPONGE.asItem() && !getFuel().isEmpty() && getFuel().getItem() == Items.BUCKET) {
			setFuel(new ItemStack(Items.WATER_BUCKET));
		}

		input.shrink(1);
		setCookInput(input);
	}

	public void setCookInput(ItemStack input) {
		cookingInventory.setStackInSlot(COOK_INPUT_SLOT, input);
	}

	private void setCookOutput(ItemStack stack) {
		getCookingInventory().setStackInSlot(COOK_OUTPUT_SLOT, stack);
	}

	private int getRemainingCookTime(Level world) {
		return (int) (getCookTimeFinish() - world.getGameTime());
	}

	private void setCookTime(Level world, int cookTime) {
		setCookTimeFinish(world.getGameTime() + cookTime);
		setCookTimeTotal(cookTime);
	}

	public void pause() {
		paused = true;
		setCookTimeFinish(0);
		setIsCooking(false);
		setBurnTimeFinish(0);
	}

	private void updateFuel(Level level, T cookingRecipe) {
		ItemStack fuel = getFuel();
		if (!isBurning(level) && canSmelt(cookingRecipe, level)) {
			if (getBurnTime(fuel, recipeType, burnTimeModifier) <= 0) {
				return;
			}
			setBurnTime(level, (int) (getBurnTime(fuel, recipeType, burnTimeModifier) * fuelEfficiencyMultiplier / cookingSpeedMultiplier));
			if (isBurning(level)) {
				if (fuel.hasCraftingRemainingItem()) {
					setFuel(fuel.getCraftingRemainingItem());
				} else if (!fuel.isEmpty()) {
					fuel.shrink(1);
					setFuel(fuel);
					if (fuel.isEmpty()) {
						setFuel(fuel.getCraftingRemainingItem());
					}
				}
			}
		}
	}

	private void setBurnTime(Level world, int burnTime) {
		setBurnTimeFinish(world.getGameTime() + burnTime);
		setBurnTimeTotal(burnTime);
	}

	protected boolean canSmelt(Recipe<?> cookingRecipe, Level level) {
		if (getCookInput().isEmpty()) {
			return false;
		}
		ItemStack recipeOutput = cookingRecipe.getResultItem(level.registryAccess());
		if (recipeOutput.isEmpty()) {
			return false;
		} else {
			ItemStack output = getCookOutput();
			if (output.isEmpty()) {
				return true;
			} else if (output.getItem() != recipeOutput.getItem()) {
				return false;
			} else if (output.getCount() + recipeOutput.getCount() <= 64 && output.getCount() + recipeOutput.getCount() <= output.getMaxStackSize()) {
				return true;
			} else {
				return output.getCount() + recipeOutput.getCount() <= recipeOutput.getMaxStackSize();
			}
		}
	}

	private static <T extends AbstractCookingRecipe> int getBurnTime(ItemStack fuel, RecipeType<T> recipeType, float burnTimeModifier) {
		return (int) (ForgeHooks.getBurnTime(fuel, recipeType) * burnTimeModifier);
	}

	public ItemStack getCookOutput() {
		return getCookingInventory().getStackInSlot(COOK_OUTPUT_SLOT);
	}

	public ItemStack getCookInput() {
		return getCookingInventory().getStackInSlot(COOK_INPUT_SLOT);
	}

	public ItemStack getFuel() {
		return getCookingInventory().getStackInSlot(FUEL_SLOT);
	}

	public void setFuel(ItemStack fuel) {
		getCookingInventory().setStackInSlot(FUEL_SLOT, fuel);
	}

	public ItemStackHandler getCookingInventory() {
		if (cookingInventory == null) {
			cookingInventory = new ItemStackHandler(3) {
				@Override
				protected void onContentsChanged(int slot) {
					super.onContentsChanged(slot);
					upgrade.addTagElement("cookingInventory", serializeNBT());
					save();
					if (slot == COOK_INPUT_SLOT) {
						cookingRecipeInitialized = false;
					}
				}

				@Override
				public boolean isItemValid(int slot, ItemStack stack) {
					return switch (slot) {
						case COOK_INPUT_SLOT -> isInput.test(stack);
						case FUEL_SLOT -> isFuel.test(stack);
						default -> true;
					};
				}
			};

			//TODO in the future remove use of this legacy smeltingInventory load as it should no longer be required
			NBTHelper.getCompound(upgrade, "smeltingInventory").ifPresentOrElse(cookingInventory::deserializeNBT,
					() -> NBTHelper.getCompound(upgrade, "cookingInventory").ifPresent(cookingInventory::deserializeNBT));
		}
		return cookingInventory;
	}

	public long getBurnTimeFinish() {
		return NBTHelper.getLong(upgrade, "burnTimeFinish").orElse(0L);
	}

	private void setBurnTimeFinish(long burnTimeFinish) {
		NBTHelper.setLong(upgrade, "burnTimeFinish", burnTimeFinish);
		save();
	}

	public int getBurnTimeTotal() {
		return NBTHelper.getInt(upgrade, "burnTimeTotal").orElse(0);
	}

	private void setBurnTimeTotal(int burnTimeTotal) {
		NBTHelper.setInteger(upgrade, "burnTimeTotal", burnTimeTotal);
		save();
	}

	public long getCookTimeFinish() {
		return NBTHelper.getLong(upgrade, "cookTimeFinish").orElse(-1L);
	}

	private void setCookTimeFinish(long cookTimeFinish) {
		NBTHelper.setLong(upgrade, "cookTimeFinish", cookTimeFinish);
		save();
	}

	public int getCookTimeTotal() {
		return NBTHelper.getInt(upgrade, "cookTimeTotal").orElse(0);
	}

	private void setCookTimeTotal(int cookTimeTotal) {
		NBTHelper.setInteger(upgrade, "cookTimeTotal", cookTimeTotal);
		save();
	}

	public boolean isCooking() {
		return NBTHelper.getBoolean(upgrade, "isCooking").orElse(false);
	}

	private void setIsCooking(boolean isCooking) {
		NBTHelper.setBoolean(upgrade, "isCooking", isCooking);
		save();
	}
}
