package g_mungus.zps.client.ponder.api.custom_screen_in_ponder_scene;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.element.InputWindowElement;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

@ApiStatus.Experimental
public class ScreenSpaceInputWindowElement extends InputWindowElement {

	private final Pointing direction;
	private final ScreenPonderElement screenElement;
	private final BiFunction<Integer, Integer, Vec2> positionFn;
	@Nullable ResourceLocation key;
	@Nullable ScreenElement icon;
	ItemStack item = ItemStack.EMPTY;

	/**
	 * Creates an input element rendered as an overlay on top of a {@link ScreenPonderElement}.
	 * {@code positionFn} receives the screen's current virtual width and height and returns the
	 * position in virtual screen coordinates, ensuring layout-relative positioning at any resolution.
	 */
	public ScreenSpaceInputWindowElement(ScreenPonderElement screenElement, BiFunction<Integer, Integer, Vec2> positionFn, Pointing direction) {
		super(new Vec3(0, 0, 0), direction);
		this.screenElement = screenElement;
		this.positionFn = positionFn;
		this.direction = direction;
	}

	@Override
	public @NotNull InputElementBuilder builder() {
		return new Builder();
	}

	@MethodsReturnNonnullByDefault
	private class Builder implements InputElementBuilder {

		@Override
		public InputElementBuilder withItem(@NotNull ItemStack stack) {
			item = stack;
			return this;
		}

		@Override
		public InputElementBuilder leftClick() {
			icon = PonderGuiTextures.ICON_LMB;
			return this;
		}

		@Override
		public InputElementBuilder scroll() {
			icon = PonderGuiTextures.ICON_SCROLL;
			return this;
		}

		@Override
		public InputElementBuilder rightClick() {
			icon = PonderGuiTextures.ICON_RMB;
			return this;
		}

		@Override
		public @NotNull InputElementBuilder showing(@NotNull ScreenElement icon_) {
			icon = icon_;
			return this;
		}

		@Override
		public InputElementBuilder whileSneaking() {
			key = Ponder.asResource("sneak_and");
			return this;
		}

		@Override
		public InputElementBuilder whileCTRL() {
			key = Ponder.asResource("ctrl_and");
			return this;
		}

	}

	void renderInScreenSpace(GuiGraphics graphics, float partialTicks, float fade) {
		Vec2 pos = positionFn.apply(screenElement.screen.width, screenElement.screen.height);

		Font font = Minecraft.getInstance().font;
		int width = 0;
		int height = 0;

		float xFade = direction == Pointing.RIGHT ? -1 : direction == Pointing.LEFT ? 1 : 0;
		float yFade = direction == Pointing.DOWN ? -1 : direction == Pointing.UP ? 1 : 0;
		xFade *= 10 * (1 - fade);
		yFade *= 10 * (1 - fade);

		boolean hasItem = !item.isEmpty();
		boolean hasText = key != null;
		boolean hasIcon = icon != null;
		int keyWidth = 0;
		String text = hasText ? PonderIndex.getLangAccess().getShared(key) : "";

		if (fade < 1 / 16f)
			return;

		if (hasIcon) {
			width += 24;
			height = 24;
		}

		if (hasText) {
			keyWidth = font.width(text);
			width += keyWidth;
		}

		if (hasItem) {
			width += 24;
			height = 24;
		}

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(pos.x + xFade, pos.y + yFade, 400);

		PonderUI.renderSpeechBox(graphics, 0, 0, width, height, false, direction, true);

		poseStack.translate(0, 0, 100);

		if (hasText)
			graphics.drawString(font, text, 2, (int) ((height - font.lineHeight) / 2f + 2),
								PonderPalette.WHITE.getColorObject().scaleAlpha(fade).getRGB(), false);

		if (hasIcon) {
			poseStack.pushPose();
			poseStack.translate(keyWidth, 0, 0);
			poseStack.scale(1.5f, 1.5f, 1.5f);
			icon.render(graphics, 0, 0);
			poseStack.popPose();
		}

		if (hasItem) {
			poseStack.pushPose();
			poseStack.translate(keyWidth + (hasIcon ? 24 : 0), 0, 0);
			poseStack.scale(1.5f, 1.5f, 1.5f);
			graphics.renderItem(item, 0, 0);
			poseStack.popPose();
		}

		poseStack.popPose();
	}

}
