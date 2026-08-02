package g_mungus.zps.client.screens.components;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MultiLineEditBox extends AbstractWidget implements Renderable {
	public static final int BACKWARDS = -1;
	public static final int FORWARDS = 1;
	private static final int CURSOR_INSERT_WIDTH = 1;
	private static final int CURSOR_INSERT_COLOR = -3092272;
	private static final String CURSOR_APPEND_CHARACTER = "_";
	public static final int DEFAULT_TEXT_COLOR = 14737632;
	private static final int BORDER_COLOR_FOCUSED = -1;
	private static final int BORDER_COLOR = -6250336;
	private static final int BACKGROUND_COLOR = -16777216;
	private static final int LINE_HEIGHT = 10;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int SCROLLBAR_PADDING = 2;
	private static final int SCROLL_STEP_LINES = 3;
	private static final int SCROLLBAR_TRACK_COLOR = -14540254;
	private static final int SCROLLBAR_HANDLE_COLOR = -6250336;
	private static final int SCROLLBAR_HANDLE_COLOR_FOCUSED = -1;
	private final Font font;
	private String value = "";
	private int maxLength = 32;
	private int frame;
	private boolean bordered = true;
	private boolean canLoseFocus = true;
	private boolean isEditable = true;
	private boolean shiftPressed;
	private int displayPos;
	private int verticalScrollLine;
	private boolean draggingScrollbar;
	private int scrollbarDragOffset;
	private int cursorPos;
	private int highlightPos;
	private int textColor = 14737632;
	private int textColorUneditable = 7368816;
	@Nullable
	private String suggestion;
	@Nullable
	private Consumer<String> responder;
	private Predicate<String> filter = Objects::nonNull;
	private LineFormatter formatter = (string, integer, lineIndex) -> FormattedCharSequence.forward(string, Style.EMPTY);
	@Nullable
	private Component hint;

	public MultiLineEditBox(Font arg, int i, int j, int k, int l, Component arg2) {
		this(arg, i, j, k, l, null, arg2);
	}

	public MultiLineEditBox(Font arg, int i, int j, int k, int l, @Nullable MultiLineEditBox arg2, Component arg3) {
		super(i, j, k, l, arg3);
		this.font = arg;
		if (arg2 != null) {
			this.setValue(arg2.getValue());
		}
	}

	public void setResponder(Consumer<String> consumer) {
		this.responder = consumer;
	}

	public void setFormatter(LineFormatter formatter) {
		this.formatter = formatter;
	}

	@FunctionalInterface
	public interface LineFormatter {
		FormattedCharSequence apply(String visibleText, int displayPos, int lineIndex);
	}

	public void tick() {
		this.frame++;
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		Component component = this.getMessage();
		return Component.translatable("gui.narrate.editBox", component, this.value);
	}

	public void setValue(String string) {
		if (this.filter.test(string)) {
			if (string.length() > this.maxLength) {
				this.value = string.substring(0, this.maxLength);
			} else {
				this.value = string;
			}

			this.moveCursorToEnd();
			this.setHighlightPos(this.cursorPos);
			this.onValueChange(string);
		}
	}

	public String getValue() {
		return this.value;
	}

	public String getHighlighted() {
		int i = Math.min(this.cursorPos, this.highlightPos);
		int j = Math.max(this.cursorPos, this.highlightPos);
		return this.value.substring(i, j);
	}

	public void setFilter(Predicate<String> predicate) {
		this.filter = predicate;
	}

	public void insertText(String string) {
		int i = Math.min(this.cursorPos, this.highlightPos);
		int j = Math.max(this.cursorPos, this.highlightPos);
		int k = this.maxLength - this.value.length() - (i - j);
		String string2 = StringUtil.filterText(string, true); // true = allow '\n'
		int l = string2.length();
		if (k < l) {
			string2 = string2.substring(0, k);
			l = k;
		}

		String string3 = new StringBuilder(this.value).replace(i, j, string2).toString();
		if (this.filter.test(string3)) {
			this.value = string3;
			this.setCursorPosition(i + l);
			this.setHighlightPos(this.cursorPos);
			this.onValueChange(this.value);
		}
	}

	private void onValueChange(String string) {
		if (this.responder != null) {
			this.responder.accept(string);
		}
	}

	private void deleteText(int i) {
		if (Screen.hasControlDown()) {
			this.deleteWords(i);
		} else {
			this.deleteChars(i);
		}
	}

	public void deleteWords(int i) {
		if (!this.value.isEmpty()) {
			if (this.highlightPos != this.cursorPos) {
				this.insertText("");
			} else {
				this.deleteChars(this.getWordPosition(i) - this.cursorPos);
			}
		}
	}

	public void deleteChars(int i) {
		if (!this.value.isEmpty()) {
			if (this.highlightPos != this.cursorPos) {
				this.insertText("");
			} else {
				int j = this.getCursorPos(i);
				int k = Math.min(j, this.cursorPos);
				int l = Math.max(j, this.cursorPos);
				if (k != l) {
					String string = new StringBuilder(this.value).delete(k, l).toString();
					if (this.filter.test(string)) {
						this.value = string;
						this.moveCursorTo(k);
					}
				}
			}
		}
	}

	public int getWordPosition(int i) {
		return this.getWordPosition(i, this.getCursorPosition());
	}

	private int getWordPosition(int i, int j) {
		return this.getWordPosition(i, j, true);
	}

	private int getWordPosition(int i, int j, boolean bl) {
		int k = j;
		boolean bl2 = i < 0;
		int l = Math.abs(i);

		for (int m = 0; m < l; m++) {
			if (!bl2) {
				// Moving forward
				int n = this.value.length();

				// Find next space or newline
				int nextSpace = this.value.indexOf(' ', k);
				int nextNewline = this.value.indexOf('\n', k);

				// Choose the nearest boundary
				if (nextSpace == -1 && nextNewline == -1) {
					k = n;
				} else if (nextSpace == -1) {
					k = nextNewline;
				} else if (nextNewline == -1) {
					k = nextSpace;
				} else {
					k = Math.min(nextSpace, nextNewline);
				}

				// Skip consecutive spaces (but stop at newlines)
				while (bl && k < n && this.value.charAt(k) == ' ') {
					k++;
				}
			} else {
				// Moving backward
				// Skip consecutive spaces (but stop at newlines)
				while (bl && k > 0 && this.value.charAt(k - 1) == ' ') {
					k--;
				}

				// Move to start of word (stop at space or newline)
				while (k > 0 && this.value.charAt(k - 1) != ' ' && this.value.charAt(k - 1) != '\n') {
					k--;
				}
			}
		}

		return k;
	}

	public void moveCursor(int i) {
		this.moveCursorTo(this.getCursorPos(i));
	}

	private int getCursorPos(int i) {
		return Util.offsetByCodepoints(this.value, this.cursorPos, i);
	}

	public void moveCursorTo(int i) {
		this.setCursorPosition(i);
		if (!this.shiftPressed) {
			this.setHighlightPos(this.cursorPos);
		}

		this.onValueChange(this.value);
	}

	public void setCursorPosition(int i) {
		this.cursorPos = Mth.clamp(i, 0, this.value.length());
	}

	public void moveCursorToStart() {
		this.moveCursorTo(0);
	}

	public void moveCursorToEnd() {
		this.moveCursorTo(this.value.length());
	}

	public void moveCursorVertically(int direction) {
		// Split into lines
		String[] lines = this.value.split("\n", -1);

		// Find current line and position within line
		int charCount = 0;
		int currentLine = 0;
		int posInLine = 0;

		for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
			int lineLength = lines[lineIdx].length();
			int lineWithNewline = lineLength + (lineIdx < lines.length - 1 ? 1 : 0);

			if (charCount + lineWithNewline > this.cursorPos) {
				currentLine = lineIdx;
				posInLine = this.cursorPos - charCount;
				break;
			}
			charCount += lineWithNewline;
		}

		// Move to target line
		int targetLine = currentLine + direction;
		if (targetLine < 0 || targetLine >= lines.length) {
			return; // Can't move beyond first/last line
		}

		// Calculate position in target line
		int targetCharCount = 0;
		for (int i = 0; i < targetLine; i++) {
			targetCharCount += lines[i].length() + 1; // +1 for newline
		}

		// Try to maintain horizontal position, but clamp to line length
		int targetPosInLine = Math.min(posInLine, lines[targetLine].length());
		this.moveCursorTo(targetCharCount + targetPosInLine);
	}

	@Override
	public boolean keyPressed(int i, int j, int k) {
		if (!this.canConsumeInput()) {
			return false;
		} else {
			this.shiftPressed = Screen.hasShiftDown();
			if (Screen.isSelectAll(i)) {
				this.moveCursorToEnd();
				this.setHighlightPos(0);
				return true;
			} else if (Screen.isCopy(i)) {
				Minecraft.getInstance().keyboardHandler.setClipboard(this.getHighlighted());
				return true;
			} else if (Screen.isPaste(i)) {
				if (this.isEditable) {
					this.insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
				}

				return true;
			} else if (Screen.isCut(i)) {
				Minecraft.getInstance().keyboardHandler.setClipboard(this.getHighlighted());
				if (this.isEditable) {
					this.insertText("");
				}

				return true;
			} else {
				switch (i) {
					case 259:
						if (this.isEditable) {
							this.shiftPressed = false;
							this.deleteText(-1);
							this.shiftPressed = Screen.hasShiftDown();
						}

						return true;
					case 260:
					case 266:
					case 267:
					default:
						return false;
					case 264:
						// Down arrow
						this.moveCursorVertically(1);
						return true;
					case 265:
						// Up arrow
						this.moveCursorVertically(-1);
						return true;
					case 261:
						if (this.isEditable) {
							this.shiftPressed = false;
							this.deleteText(1);
							this.shiftPressed = Screen.hasShiftDown();
						}

						return true;
					case 262:
						if (Screen.hasControlDown()) {
							this.moveCursorTo(this.getWordPosition(1));
						} else {
							this.moveCursor(1);
						}

						return true;
					case 263:
						if (Screen.hasControlDown()) {
							this.moveCursorTo(this.getWordPosition(-1));
						} else {
							this.moveCursor(-1);
						}

						return true;
					case 268:
						this.moveCursorToStart();
						return true;
					case 269:
						this.moveCursorToEnd();
						return true;
					case 257:
						this.insertText("\n");
						return true;
				}
			}
		}
	}

	public boolean canConsumeInput() {
		return this.isVisible() && this.isFocused() && this.isEditable();
	}

	@Override
	public boolean charTyped(char c, int i) {
		if (!this.canConsumeInput()) {
			return false;
		} else if (StringUtil.isAllowedChatCharacter(c) || c == '\n') {
			if (this.isEditable) {
				this.insertText(Character.toString(c));
			}

			return true;
		} else {
			return false;
		}
	}

	@Override
	public void onClick(double d, double e) {
		int clickX = Mth.floor(d) - this.getX();
		int clickY = Mth.floor(e) - this.getY();
		if (this.bordered) {
			clickX -= 4;
			clickY -= 4;
		}

		// Determine which line was clicked
		int lineIndex = clickY / LINE_HEIGHT;
		String[] lines = this.value.split("\n", -1);

		if (lineIndex >= lines.length) {
			// Clicked below all lines, move to end
			this.moveCursorTo(this.value.length());
			return;
		}

		lineIndex += this.verticalScrollLine;
		lineIndex = Mth.clamp(lineIndex, 0, lines.length - 1);

		// Calculate position up to the clicked line
		int positionUpToLine = 0;
		for (int i = 0; i < lineIndex && i < lines.length; i++) {
			positionUpToLine += lines[i].length() + 1; // +1 for newline
		}

		// Find position within the clicked line (accounting for horizontal scroll)
		String clickedLine = lines[lineIndex];
		int scrollOffset = Math.min(this.displayPos, clickedLine.length());
		String scrolledLine = clickedLine.substring(scrollOffset);
		String visiblePart = this.font.plainSubstrByWidth(scrolledLine, this.getInnerWidth());
		int posInLine = this.font.plainSubstrByWidth(visiblePart, clickX).length() + scrollOffset;

		this.moveCursorTo(positionUpToLine + posInLine);
	}

	@Override
	public boolean mouseClicked(double d, double e, int i) {
		if (this.visible && i == 0 && this.isScrollbarMouseOver(d, e) && this.getMaxScrollLine() > 0) {
			this.setFocused(true);
			ScrollbarMetrics metrics = this.getScrollbarMetrics();
			this.draggingScrollbar = true;
			this.scrollbarDragOffset = Mth.clamp((int)e - metrics.handleTop(), 0, metrics.handleHeight());
			this.updateScrollbarDrag(e);
			return true;
		}

		return super.mouseClicked(d, e, i);
	}

	@Override
	public boolean mouseReleased(double d, double e, int i) {
		if (i == 0 && this.draggingScrollbar) {
			this.draggingScrollbar = false;
			return true;
		}

		return super.mouseReleased(d, e, i);
	}

	@Override
	public boolean mouseDragged(double d, double e, int i, double f, double g) {
		if (this.draggingScrollbar && i == 0) {
			this.updateScrollbarDrag(e);
			return true;
		}

		return super.mouseDragged(d, e, i, f, g);
	}

	@Override
	public boolean mouseScrolled(double d, double e, double scrollX, double scrollY) {
		if (!this.isMouseOver(d, e) || this.getMaxScrollLine() <= 0) {
			return false;
		}

		this.scrollLines(-(int)Math.signum(scrollY) * SCROLL_STEP_LINES);
		return true;
	}

	@Override
	public void playDownSound(SoundManager arg) {
	}

	@Override
	public void renderWidget(GuiGraphics arg, int i, int j, float f) {
		if (this.isVisible()) {
			if (this.isBordered()) {
				int k = this.isFocused() ? -1 : -6250336;
				arg.fill(this.getX() - 1, this.getY() - 1, this.getX() + this.width + 1, this.getY() + this.height + 1, k);
				arg.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, -16777216);
			}

			int textColor = this.isEditable ? this.textColor : this.textColorUneditable;
			int startX = this.bordered ? this.getX() + 4 : this.getX();
			int startY = this.bordered ? this.getY() + 4 : this.getY();
			int maxWidth = this.getInnerWidth();

			String[] lines = this.value.split("\n", -1);
			this.clampVerticalScroll(lines.length);

			// Calculate which line the cursor and highlight are on
			int cursorLine = -1, cursorPosInLine = -1;
			int highlightLine = -1, highlightPosInLine = -1;
			int charCount = 0;

			for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
				int lineLength = lines[lineIdx].length();
				int lineEnd = charCount + lineLength;

				if (cursorLine == -1 && this.cursorPos >= charCount && this.cursorPos <= lineEnd) {
					cursorLine = lineIdx;
					cursorPosInLine = Math.min(this.cursorPos - charCount, lineLength);
				}

				if (highlightLine == -1 && this.highlightPos >= charCount && this.highlightPos <= lineEnd) {
					highlightLine = lineIdx;
					highlightPosInLine = Math.min(this.highlightPos - charCount, lineLength);
				}

				charCount += lineLength + 1; // +1 for newline
			}

			// Render lines
			int y = startY;
			for (int lineIdx = this.verticalScrollLine; lineIdx < lines.length; lineIdx++) {
				if (y + LINE_HEIGHT > this.getY() + this.height) {
					break;
				}

				String line = lines[lineIdx];

				// Apply horizontal scrolling (displayPos)
				int scrollOffset = Math.min(this.displayPos, line.length());
				String scrolledLine = line.substring(scrollOffset);
				String clipped = this.font.plainSubstrByWidth(scrolledLine, maxWidth);

				arg.drawString(
						this.font,
						this.formatter.apply(clipped, this.displayPos, lineIdx),
						startX,
						y,
						textColor
				);

				// Render cursor if on this line
				if (cursorLine == lineIdx && this.isFocused() && this.frame / 6 % 2 == 0) {
					// Cursor position relative to visible portion
					int cursorPosVisible = cursorPosInLine - scrollOffset;
					if (cursorPosVisible >= 0 && cursorPosVisible <= clipped.length()) {
						int cursorX = startX + this.font.width(clipped.substring(0, Math.min(cursorPosVisible, clipped.length())));
						boolean atEnd = this.cursorPos < this.value.length() || this.value.length() >= this.getMaxLength();

						if (atEnd) {
							arg.fill(RenderType.guiOverlay(), cursorX, y - 1, cursorX + 1, y + 10, CURSOR_INSERT_COLOR);
						} else {
							arg.drawString(this.font, CURSOR_APPEND_CHARACTER, cursorX, y, textColor);
						}
					}
				}

				// Render highlight if it overlaps this line
				if (this.cursorPos != this.highlightPos) {
					int minPos = Math.min(this.cursorPos, this.highlightPos);
					int maxPos = Math.max(this.cursorPos, this.highlightPos);

					// Calculate character range for this line
					int lineStartChar = 0;
					for (int k = 0; k < lineIdx; k++) {
						lineStartChar += lines[k].length() + 1;
					}
					int lineEndChar = lineStartChar + lines[lineIdx].length();

					// Check if highlight overlaps this line
					if (maxPos > lineStartChar && minPos < lineEndChar) {
						int highlightStart = Math.max(0, minPos - lineStartChar);
						int highlightEnd = Math.min(lines[lineIdx].length(), maxPos - lineStartChar);

						// Adjust for horizontal scroll
						int highlightStartVisible = highlightStart - scrollOffset;
						int highlightEndVisible = highlightEnd - scrollOffset;

						if (highlightEndVisible > 0 && highlightStartVisible < clipped.length()) {
							highlightStartVisible = Math.max(0, highlightStartVisible);
							highlightEndVisible = Math.min(clipped.length(), highlightEndVisible);

							String beforeHighlight = clipped.substring(0, highlightStartVisible);
							String highlighted = clipped.substring(highlightStartVisible, highlightEndVisible);

							int x1 = startX + this.font.width(beforeHighlight);
							int x2 = x1 + this.font.width(highlighted);

							this.renderHighlight(arg, x1, y - 1, x2, y + 10);
						}
					}
				}

				y += LINE_HEIGHT;
			}

			this.renderScrollbar(arg, lines.length);

			// Render hint if empty and not focused
			if (this.hint != null && this.value.isEmpty() && !this.isFocused()) {
				arg.drawString(this.font, this.hint, startX, startY, textColor);
			}

			// Render suggestion (if any)
			if (this.suggestion != null && this.cursorPos == this.value.length()) {
				int cursorX = startX;
				if (cursorLine >= 0 && cursorLine < lines.length) {
					int safePos = Math.min(cursorPosInLine, lines[cursorLine].length());
					cursorX += this.font.width(lines[cursorLine].substring(0, safePos));
				}
				int suggestY = startY + (cursorLine >= 0 ? (cursorLine - this.verticalScrollLine) * LINE_HEIGHT : 0);
				arg.drawString(this.font, this.suggestion, cursorX, suggestY, -8355712);
			}
		}
	}

	private void renderScrollbar(GuiGraphics graphics, int lineCount) {
		if (this.getMaxScrollLine(lineCount) <= 0) {
			return;
		}

		ScrollbarMetrics metrics = this.getScrollbarMetrics(lineCount);
		graphics.fill(metrics.left(), metrics.trackTop(), metrics.left() + SCROLLBAR_WIDTH, metrics.trackBottom(), SCROLLBAR_TRACK_COLOR);
		graphics.fill(
			metrics.left(),
			metrics.handleTop(),
			metrics.left() + SCROLLBAR_WIDTH,
			metrics.handleTop() + metrics.handleHeight(),
			this.isFocused() ? SCROLLBAR_HANDLE_COLOR_FOCUSED : SCROLLBAR_HANDLE_COLOR
		);
	}

	private void renderHighlight(GuiGraphics arg, int i, int j, int k, int l) {
		if (i < k) {
			int m = i;
			i = k;
			k = m;
		}

		if (j < l) {
			int m = j;
			j = l;
			l = m;
		}

		if (k > this.getX() + this.width) {
			k = this.getX() + this.width;
		}

		if (i > this.getX() + this.width) {
			i = this.getX() + this.width;
		}

		arg.fill(RenderType.guiTextHighlight(), i, j, k, l, -16776961);
	}

	public void setMaxLength(int i) {
		this.maxLength = i;
		if (this.value.length() > i) {
			this.value = this.value.substring(0, i);
			this.onValueChange(this.value);
		}
	}

	private int getMaxLength() {
		return this.maxLength;
	}

	public int getCursorPosition() {
		return this.cursorPos;
	}

	private boolean isBordered() {
		return this.bordered;
	}

	public void setBordered(boolean bl) {
		this.bordered = bl;
	}

	public void setTextColor(int i) {
		this.textColor = i;
	}

	public void setTextColorUneditable(int i) {
		this.textColorUneditable = i;
	}

	@Nullable
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent arg) {
		return this.visible && this.isEditable ? super.nextFocusPath(arg) : null;
	}

	@Override
	public boolean isMouseOver(double d, double e) {
		return this.visible && d >= this.getX() && d < this.getX() + this.width && e >= this.getY() && e < this.getY() + this.height;
	}

	@Override
	public void setFocused(boolean bl) {
		if (this.canLoseFocus || bl) {
			super.setFocused(bl);
			if (bl) {
				this.frame = 0;
			}
		}
	}

	private boolean isEditable() {
		return this.isEditable;
	}

	public void setEditable(boolean bl) {
		this.isEditable = bl;
	}

	public int getInnerWidth() {
		int innerWidth = this.isBordered() ? this.width - 8 : this.width;
		return Math.max(1, innerWidth - this.getScrollbarGutterWidth());
	}

	public int getMaxLines() {
		int availableHeight = this.height - (this.bordered ? 8 : 0);
		return Math.max(1, availableHeight / LINE_HEIGHT);
	}

	public void setHighlightPos(int i) {
		int j = this.value.length();
		this.highlightPos = Mth.clamp(i, 0, j);
		if (this.font != null) {
			// Split text into lines to find which line contains the highlight position
			String[] lines = this.value.split("\n", -1);

			// Find which line the highlight position is on
			int charCount = 0;
			int currentLine = 0;
			int posInLine = 0;

			for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
				int lineLength = lines[lineIdx].length();

				// Check if position is within this line (not including the newline)
				if (this.highlightPos >= charCount && this.highlightPos <= charCount + lineLength) {
					currentLine = lineIdx;
					posInLine = this.highlightPos - charCount;
					break;
				}

				// Move to next line (add line length + 1 for newline, except on last line)
				charCount += lineLength + (lineIdx < lines.length - 1 ? 1 : 0);
			}

			// Adjust horizontal scrolling to ensure the cursor is visible
			if (currentLine < lines.length) {
				String currentLineText = lines[currentLine];
				int innerWidth = this.getInnerWidth();

				// Clamp posInLine to valid range for this line
				posInLine = Math.min(posInLine, currentLineText.length());

				// Calculate what portion of the line is visible
				int scrollOffset = Math.min(this.displayPos, currentLineText.length());
				String visiblePortion = this.font.plainSubstrByWidth(
					currentLineText.substring(scrollOffset), innerWidth);
				int visibleEndPos = scrollOffset + visiblePortion.length();

				// If cursor is beyond visible area, scroll right
				if (posInLine >= visibleEndPos) {
					// Scroll so cursor is at the right edge
					int safePosInLine = Math.min(posInLine, currentLineText.length());
					this.displayPos = safePosInLine - this.font.plainSubstrByWidth(
						currentLineText.substring(0, safePosInLine), innerWidth, true).length();
				}
				// If cursor is before visible area, scroll left
				else if (posInLine < this.displayPos) {
					this.displayPos = posInLine;
				}

				// Clamp displayPos to valid range
				this.displayPos = Mth.clamp(this.displayPos, 0, currentLineText.length());
				this.ensureLineVisible(currentLine);
			}
		}
	}

	public void setCanLoseFocus(boolean bl) {
		this.canLoseFocus = bl;
	}

	public boolean isVisible() {
		return this.visible;
	}

	public void setVisible(boolean bl) {
		this.visible = bl;
	}

	public void setSuggestion(@Nullable String string) {
		this.suggestion = string;
	}

	public int getScreenX(int i) {
		if (i > this.value.length()) {
			return this.getX();
		}

		// Split into lines and find which line contains position i
		String[] lines = this.value.split("\n", -1);
		int charCount = 0;
		int startX = this.bordered ? this.getX() + 4 : this.getX();

		for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
			int lineLength = lines[lineIdx].length();
			int lineEnd = charCount + lineLength;

			if (i >= charCount && i <= lineEnd) {
				// Position is on this line
				int posInLine = i - charCount;
				return startX + this.font.width(lines[lineIdx].substring(0, posInLine));
			}

			charCount += lineLength + 1; // +1 for newline
		}

		return startX;
	}

	public int getScreenY(int i) {
		String[] lines = this.value.split("\n", -1);
		int lineIdx = this.getLineForPosition(Mth.clamp(i, 0, this.value.length()), lines);
		int startY = this.bordered ? this.getY() + 4 : this.getY();
		return startY + (lineIdx - this.verticalScrollLine) * LINE_HEIGHT;
	}

	private int getScrollbarGutterWidth() {
		return this.getMaxScrollLine() > 0 ? SCROLLBAR_WIDTH + SCROLLBAR_PADDING : 0;
	}

	private int getLineForPosition(int position, String[] lines) {
		int charCount = 0;
		for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
			int lineLength = lines[lineIdx].length();
			if (position >= charCount && position <= charCount + lineLength) {
				return lineIdx;
			}
			charCount += lineLength + 1;
		}
		return Math.max(0, lines.length - 1);
	}

	private int getMaxScrollLine() {
		return this.getMaxScrollLine(this.value.split("\n", -1).length);
	}

	private int getMaxScrollLine(int lineCount) {
		return Math.max(0, lineCount - this.getMaxLines());
	}

	private void scrollLines(int lines) {
		this.verticalScrollLine = Mth.clamp(this.verticalScrollLine + lines, 0, this.getMaxScrollLine());
	}

	private void ensureLineVisible(int line) {
		if (line < this.verticalScrollLine) {
			this.verticalScrollLine = line;
		} else {
			int lastVisibleLine = this.verticalScrollLine + this.getMaxLines() - 1;
			if (line > lastVisibleLine) {
				this.verticalScrollLine = line - this.getMaxLines() + 1;
			}
		}
		this.verticalScrollLine = Mth.clamp(this.verticalScrollLine, 0, this.getMaxScrollLine());
	}

	private void clampVerticalScroll(int lineCount) {
		this.verticalScrollLine = Mth.clamp(this.verticalScrollLine, 0, this.getMaxScrollLine(lineCount));
	}

	private boolean isScrollbarMouseOver(double mouseX, double mouseY) {
		if (this.getMaxScrollLine() <= 0) {
			return false;
		}

		ScrollbarMetrics metrics = this.getScrollbarMetrics();
		return mouseX >= metrics.left()
			&& mouseX < metrics.left() + SCROLLBAR_WIDTH
			&& mouseY >= metrics.trackTop()
			&& mouseY < metrics.trackBottom();
	}

	private void updateScrollbarDrag(double mouseY) {
		ScrollbarMetrics metrics = this.getScrollbarMetrics();
		int minTop = metrics.trackTop();
		int maxTop = metrics.trackBottom() - metrics.handleHeight();
		if (maxTop <= minTop) {
			this.verticalScrollLine = 0;
			return;
		}

		int targetTop = Mth.clamp((int)Math.round(mouseY) - this.scrollbarDragOffset, minTop, maxTop);
		float progress = (targetTop - minTop) / (float)(maxTop - minTop);
		this.verticalScrollLine = Mth.clamp(Math.round(progress * this.getMaxScrollLine()), 0, this.getMaxScrollLine());
	}

	private ScrollbarMetrics getScrollbarMetrics() {
		return this.getScrollbarMetrics(this.value.split("\n", -1).length);
	}

	private ScrollbarMetrics getScrollbarMetrics(int lineCount) {
		int trackTop = this.getY() + (this.bordered ? 4 : 0);
		int trackBottom = this.getY() + this.height - (this.bordered ? 4 : 0);
		int trackHeight = Math.max(1, trackBottom - trackTop);
		int visibleLines = this.getMaxLines();
		int handleHeight = Mth.clamp((int)(trackHeight * (visibleLines / (float)Math.max(visibleLines, lineCount))), 8, trackHeight);
		int maxScrollLine = this.getMaxScrollLine(lineCount);
		int handleTop = trackTop;
		if (maxScrollLine > 0 && trackHeight > handleHeight) {
			handleTop += Math.round((trackHeight - handleHeight) * (this.verticalScrollLine / (float)maxScrollLine));
		}
		int left = this.getX() + this.width - (this.bordered ? 4 : 0) - SCROLLBAR_WIDTH;
		return new ScrollbarMetrics(left, trackTop, trackBottom, handleTop, handleHeight);
	}

	private record ScrollbarMetrics(int left, int trackTop, int trackBottom, int handleTop, int handleHeight) {
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput arg) {
		arg.add(NarratedElementType.TITLE, this.createNarrationMessage());
	}

	public void setHint(Component arg) {
		this.hint = arg;
	}
}
