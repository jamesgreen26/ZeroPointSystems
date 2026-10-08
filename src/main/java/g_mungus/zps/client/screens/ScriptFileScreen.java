package g_mungus.zps.client.screens;

import g_mungus.zps.ZPSMod;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Client-side save/load picker for the Script Terminal. Scripts live as plain UTF-8 {@code .mungus} files in
 * {@code <instance>/zps_scripts}. The first line may be a settings header such as {@code # 4t IMPULSE}; files
 * without one load the script alone.
 */
public class ScriptFileScreen extends Screen {
    public static final String EXTENSION = ".mungus";
    private static final String FOLDER_NAME = "zps_scripts";
    private static final Pattern VALID_NAME = Pattern.compile("[^/\\\\:*?\"<>|]+");
    private static final Pattern HEADER = Pattern.compile("#\\s*(\\d+)t\\s+(IMPULSE|REPEAT)\\s*", Pattern.CASE_INSENSITIVE);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private static final Component SAVE_TITLE = Component.literal("Save Script");
    private static final Component LOAD_TITLE = Component.literal("Load Script");
    private static final Component SAVE_LABEL = Component.literal("Save");
    private static final Component LOAD_LABEL = Component.literal("Load");
    private static final Component OPEN_FOLDER_LABEL = Component.literal("Open Folder");
    private static final Component NAME_HINT = Component.literal("File name").withStyle(ChatFormatting.DARK_GRAY);
    private static final Component EMPTY_LABEL = Component.literal("No " + EXTENSION + " files in " + FOLDER_NAME);

    public enum Mode { SAVE, LOAD }

    /**
     * A script plus the terminal settings from its header. {@code delay} and {@code repeat} are null when the file
     * has no header.
     */
    public record ScriptFile(String script, @Nullable Integer delay, @Nullable Boolean repeat) {
        public String serialize() {
            if (this.delay == null || this.repeat == null) return this.script;
            return "# " + this.delay + "t " + (this.repeat ? "REPEAT" : "IMPULSE") + "\n" + this.script;
        }

        public static ScriptFile parse(String content) {
            content = content.replace("\r\n", "\n");
            int newline = content.indexOf('\n');
            String firstLine = newline < 0 ? content : content.substring(0, newline);
            Matcher header = HEADER.matcher(firstLine);
            if (!header.matches()) {
                return new ScriptFile(content, null, null);
            }
            String script = newline < 0 ? "" : content.substring(newline + 1);
            Integer delay;
            try {
                delay = Integer.parseInt(header.group(1));
            } catch (NumberFormatException e) {
                delay = null;
            }
            return new ScriptFile(script, delay, header.group(2).equalsIgnoreCase("REPEAT"));
        }
    }

    private final Screen parent;
    private final Mode mode;
    private final ScriptFile script;
    private final int maxLength;
    private final Consumer<ScriptFile> onLoad;

    private FileList fileList;
    private @Nullable EditBox nameEdit;
    private Button actionButton;
    private @Nullable Component error;

    private ScriptFileScreen(Screen parent, Mode mode, ScriptFile script, int maxLength, Consumer<ScriptFile> onLoad) {
        super(mode == Mode.SAVE ? SAVE_TITLE : LOAD_TITLE);
        this.parent = parent;
        this.mode = mode;
        this.script = script;
        this.maxLength = maxLength;
        this.onLoad = onLoad;
    }

    public static ScriptFileScreen save(Screen parent, ScriptFile script) {
        return new ScriptFileScreen(parent, Mode.SAVE, script, 0, s -> {});
    }

    public static ScriptFileScreen load(Screen parent, int maxLength, Consumer<ScriptFile> onLoad) {
        return new ScriptFileScreen(parent, Mode.LOAD, new ScriptFile("", null, null), maxLength, onLoad);
    }

    public static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER_NAME);
    }

    @Override
    protected void init() {
        try {
            Files.createDirectories(folder());
        } catch (IOException e) {
            ZPSMod.LOGGER.warn("Failed to create script folder {}", folder(), e);
        }

        int left = this.width / 2 - 150;
        int buttonY = this.height - 28;
        int listBottom = this.mode == Mode.SAVE ? this.height - 64 : this.height - 38;

        String typedName = this.nameEdit != null ? this.nameEdit.getValue() : "";
        this.fileList = new FileList(this.minecraft, 300, listBottom - 36, 36);
        this.fileList.setX(left);
        this.addRenderableWidget(this.fileList);

        if (this.mode == Mode.SAVE) {
            this.nameEdit = new EditBox(this.font, left, this.height - 56, 300, 20, Component.literal("File name"));
            this.nameEdit.setMaxLength(64);
            this.nameEdit.setHint(NAME_HINT);
            this.nameEdit.setValue(typedName);
            this.nameEdit.setResponder(s -> this.error = null);
            this.addRenderableWidget(this.nameEdit);
            this.setInitialFocus(this.nameEdit);
        } else {
            this.nameEdit = null;
            this.setInitialFocus(this.fileList);
        }

        this.actionButton = this.addRenderableWidget(
                Button.builder(this.mode == Mode.SAVE ? SAVE_LABEL : LOAD_LABEL, b -> this.confirm())
                        .bounds(left, buttonY, 98, 20).build()
        );
        this.addRenderableWidget(
                Button.builder(OPEN_FOLDER_LABEL, b -> Util.getPlatform().openPath(folder()))
                        .bounds(left + 101, buttonY, 98, 20).build()
        );
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
                        .bounds(left + 202, buttonY, 98, 20).build()
        );
        this.updateActionButton();
    }

    @Override
    public void tick() {
        this.updateActionButton();
    }

    private void updateActionButton() {
        this.actionButton.active = this.mode == Mode.SAVE
                ? this.nameEdit != null && !this.nameEdit.getValue().isBlank()
                : this.fileList.getSelected() != null;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        if (this.fileList.children().isEmpty()) {
            graphics.drawCenteredString(this.font, EMPTY_LABEL, this.width / 2, 48, 0x808080);
        }
        if (this.error != null) {
            int y = this.mode == Mode.SAVE ? this.height - 66 - this.font.lineHeight : this.height - 36 - this.font.lineHeight;
            graphics.drawCenteredString(this.font, this.error, this.width / 2, y, 0xFF5555);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && this.actionButton.active) {
            this.confirm();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void confirm() {
        if (this.mode == Mode.SAVE) {
            this.trySave();
        } else {
            FileEntry selected = this.fileList.getSelected();
            if (selected != null) {
                this.load(selected.path);
            }
        }
    }

    private void trySave() {
        if (this.nameEdit == null || this.minecraft == null) return;
        String name = this.nameEdit.getValue().strip();
        if (name.toLowerCase().endsWith(EXTENSION)) {
            name = name.substring(0, name.length() - EXTENSION.length()).strip();
        }
        if (name.isEmpty() || name.startsWith(".") || !VALID_NAME.matcher(name).matches()) {
            this.error = Component.literal("Invalid file name");
            return;
        }

        Path target = folder().resolve(name + EXTENSION);
        if (Files.exists(target)) {
            String displayName = name;
            this.minecraft.setScreen(new ConfirmScreen(
                    overwrite -> {
                        if (overwrite) {
                            this.write(target);
                        } else {
                            this.minecraft.setScreen(this);
                        }
                    },
                    Component.literal("Overwrite " + displayName + EXTENSION + "?"),
                    Component.literal("A script with this name already exists.")
            ));
            return;
        }
        this.write(target);
    }

    private void write(Path target) {
        try {
            Files.writeString(target, this.script.serialize(), StandardCharsets.UTF_8);
            this.onClose();
        } catch (IOException e) {
            ZPSMod.LOGGER.warn("Failed to save script to {}", target, e);
            this.error = Component.literal("Failed to save: " + e.getMessage());
            if (this.minecraft != null) this.minecraft.setScreen(this);
        }
    }

    private void load(Path source) {
        String content;
        try {
            content = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ZPSMod.LOGGER.warn("Failed to load script from {}", source, e);
            this.error = Component.literal("Failed to load: " + e.getMessage());
            return;
        }
        ScriptFile file = ScriptFile.parse(content);
        if (file.script().length() > this.maxLength) {
            this.error = Component.literal("Script is too long (" + file.script().length() + "/" + this.maxLength + " characters)");
            return;
        }
        this.onLoad.accept(file);
        this.onClose();
    }

    private static List<Path> listScripts() {
        try (Stream<Path> files = Files.list(folder())) {
            return files
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(EXTENSION))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private final class FileList extends ObjectSelectionList<FileEntry> {
        private FileList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 14);
            for (Path path : listScripts()) {
                this.addEntry(new FileEntry(path));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 16;
        }

        @Override
        public void setSelected(@Nullable FileEntry entry) {
            super.setSelected(entry);
            ScriptFileScreen.this.error = null;
            if (entry != null && ScriptFileScreen.this.nameEdit != null) {
                ScriptFileScreen.this.nameEdit.setValue(entry.name);
            }
        }
    }

    private final class FileEntry extends ObjectSelectionList.Entry<FileEntry> {
        private final Path path;
        private final String name;
        private final String modified;
        private long lastClickTime;

        private FileEntry(Path path) {
            this.path = path;
            String fileName = path.getFileName().toString();
            this.name = fileName.substring(0, fileName.length() - EXTENSION.length());
            String modified;
            try {
                FileTime time = Files.getLastModifiedTime(path);
                modified = DATE_FORMAT.format(new Date(time.toMillis()));
            } catch (IOException e) {
                modified = "";
            }
            this.modified = modified;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int dateWidth = ScriptFileScreen.this.font.width(this.modified);
            String shown = ScriptFileScreen.this.font.plainSubstrByWidth(this.name, width - dateWidth - 12);
            graphics.drawString(ScriptFileScreen.this.font, shown, left + 2, top + 1, 0xFFFFFF);
            graphics.drawString(ScriptFileScreen.this.font, this.modified, left + width - dateWidth - 4, top + 1, 0x808080);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            ScriptFileScreen.this.fileList.setSelected(this);
            long now = Util.getMillis();
            if (now - this.lastClickTime < 250L) {
                ScriptFileScreen.this.confirm();
            }
            this.lastClickTime = now;
            return true;
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.literal(this.name);
        }
    }
}
