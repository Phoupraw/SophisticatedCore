package net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.inventory.Slot;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeSettingsTab;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.Button;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ButtonDefinition;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.TextureBlitData;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.TranslationHelper;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.UV;

import static net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper.*;

public class JukeboxUpgradeTab extends UpgradeSettingsTab<JukeboxUpgradeContainer> {
	private static final TextureBlitData PLAY_FOREGROUND = new TextureBlitData(ICONS, new Position(1, 1), Dimension.SQUARE_256, new UV(16, 64), Dimension.SQUARE_16);
	private static final ButtonDefinition PLAY = new ButtonDefinition(Dimension.SQUARE_16, DEFAULT_BUTTON_BACKGROUND, DEFAULT_BUTTON_HOVERED_BACKGROUND, PLAY_FOREGROUND,
			new TranslatableComponent(TranslationHelper.INSTANCE.translUpgradeButton("play")));
	private static final TextureBlitData STOP_FOREGROUND = new TextureBlitData(ICONS, new Position(1, 1), Dimension.SQUARE_256, new UV(0, 64), Dimension.SQUARE_16);
	private static final ButtonDefinition STOP = new ButtonDefinition(Dimension.SQUARE_16, DEFAULT_BUTTON_BACKGROUND, DEFAULT_BUTTON_HOVERED_BACKGROUND, STOP_FOREGROUND,
			new TranslatableComponent(TranslationHelper.INSTANCE.translUpgradeButton("stop")));

	public JukeboxUpgradeTab(JukeboxUpgradeContainer upgradeContainer, Position position, StorageScreenBase<?> screen) {
		super(upgradeContainer, position, screen, TranslationHelper.INSTANCE.translUpgrade("jukebox"), TranslationHelper.INSTANCE.translUpgradeTooltip("jukebox"));

		addHideableChild(new Button(new Position(x + 3, y + 44), STOP, button -> {
			if (button == 0) {
				getContainer().stop();
			}
		}));
		addHideableChild(new Button(new Position(x + 21, y + 44), PLAY, button -> {
			if (button == 0) {
				getContainer().play();
			}
		}));
	}

	@Override
	protected void renderBg(PoseStack matrixStack, Minecraft minecraft, int mouseX, int mouseY) {
		super.renderBg(matrixStack, minecraft, mouseX, mouseY);
		if (getContainer().isOpen()) {
			GuiHelper.renderSlotsBackground(matrixStack, x + 3, y + 24, 1, 1);
		}
	}

	@Override
	protected void moveSlotsToTab() {
		Slot discSlot = getContainer().getSlots().get(0);
		discSlot.x = x - screen.getGuiLeft() + 4;
		discSlot.y = y - screen.getGuiTop() + 25;
	}
}
