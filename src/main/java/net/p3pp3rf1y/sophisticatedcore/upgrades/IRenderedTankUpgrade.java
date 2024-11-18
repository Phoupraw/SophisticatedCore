package net.p3pp3rf1y.sophisticatedcore.upgrades;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Consumer;

public interface IRenderedTankUpgrade {
	void setTankRenderInfoUpdateCallback(Consumer<TankRenderInfo> updateTankRenderInfoCallback);

	void forceUpdateTankRenderInfo();

	class TankRenderInfo {
		private static final String FLUID_TAG = "fluid";
		private static final String FILL_RATIO_TAG = "fillRatio";

		public TankRenderInfo() {
			this(null, 0);
		}

		public TankRenderInfo(@Nullable FluidStack fluidStack, float fillRatio) {
			this.fluidStack = fluidStack;
			this.fillRatio = fillRatio;
		}

		@Nullable
		private FluidStack fluidStack;
		private float fillRatio;

		public CompoundTag serialize() {
			CompoundTag ret = new CompoundTag();
			if (fluidStack != null) {
				ret.put(FLUID_TAG, fluidStack.writeToNBT(new CompoundTag()));
				ret.putFloat(FILL_RATIO_TAG, fillRatio);
			}
			return ret;
		}

		public static TankRenderInfo deserialize(CompoundTag tag) {
			if (tag.contains(FLUID_TAG)) {
				FluidStack contents = FluidStack.loadFluidStackFromNBT(tag.getCompound(FLUID_TAG));
				if (!contents.isEmpty()) {
					return new TankRenderInfo(contents, tag.getFloat(FILL_RATIO_TAG));
				}
			}

			return new TankRenderInfo();
		}

		public void setFluid(FluidStack fluidStack) {
			this.fluidStack = fluidStack.copy();
		}

		public Optional<FluidStack> getFluid() {
			return Optional.ofNullable(fluidStack);
		}

		public void setFillRatio(float fillRatio) {
			this.fillRatio = fillRatio;
		}

		public float getFillRatio() {
			return fillRatio;
		}
	}
}
