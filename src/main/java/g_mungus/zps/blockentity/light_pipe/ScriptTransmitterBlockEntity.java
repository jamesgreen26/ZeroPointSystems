package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class ScriptTransmitterBlockEntity extends NetworkTerminalImpl implements Clearable, MenuProvider {

    public static final int DATA_PAGE = 0;
    public static final int NUM_DATA = 1;
    public static final int SLOT_BOOK = 0;
    public static final int NUM_SLOTS = 1;
    private final Container bookAccess = new Container() {
        public int getContainerSize() {
            return 1;
        }

        public boolean isEmpty() {
            return ScriptTransmitterBlockEntity.this.book.isEmpty();
        }

        public ItemStack getItem(int i) {
            return i == 0 ? ScriptTransmitterBlockEntity.this.book : ItemStack.EMPTY;
        }

        public ItemStack removeItem(int i, int j) {
            if (i == 0) {
                ItemStack itemStack = ScriptTransmitterBlockEntity.this.book.split(j);
                if (ScriptTransmitterBlockEntity.this.book.isEmpty()) {
                    ScriptTransmitterBlockEntity.this.onBookItemRemove();
                }

                return itemStack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        public ItemStack removeItemNoUpdate(int i) {
            if (i == 0) {
                ItemStack itemStack = ScriptTransmitterBlockEntity.this.book;
                ScriptTransmitterBlockEntity.this.book = ItemStack.EMPTY;
                ScriptTransmitterBlockEntity.this.onBookItemRemove();
                return itemStack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        public void setItem(int i, ItemStack arg) {
        }

        public int getMaxStackSize() {
            return 1;
        }

        public void setChanged() {
            ScriptTransmitterBlockEntity.this.setChanged();
        }

        public boolean stillValid(Player arg) {
            return Container.stillValidBlockEntity(ScriptTransmitterBlockEntity.this, arg) && ScriptTransmitterBlockEntity.this.hasBook();
        }

        public boolean canPlaceItem(int i, ItemStack arg) {
            return false;
        }

        public void clearContent() {
        }
    };
    private final ContainerData dataAccess = new ContainerData() {
        public int get(int i) {
            return i == 0 ? ScriptTransmitterBlockEntity.this.page : 0;
        }

        public void set(int i, int j) {
            if (i == 0) {
                ScriptTransmitterBlockEntity.this.setPage(j);
            }

        }

        public int getCount() {
            return 1;
        }
    };
    ItemStack book;
    int page;
    private int pageCount;


    public ScriptTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCRIPT_TRANSMITTER.get(), pos, state);
        this.book = ItemStack.EMPTY;
    }


    public ItemStack getBook() {
        return this.book;
    }

    public boolean hasBook() {
        return this.book.is(Items.WRITABLE_BOOK) || this.book.is(Items.WRITTEN_BOOK);
    }

    public void setBook(ItemStack arg) {
        this.setBook(arg, null);
    }

    void onBookItemRemove() {
        this.page = 0;
        this.pageCount = 0;
        LecternBlock.resetBookState(null, this.getLevel(), this.getBlockPos(), this.getBlockState(), false);
    }

    public void setBook(ItemStack arg, @Nullable Player arg2) {
        this.book = this.resolveBook(arg, arg2);
        this.page = 0;
        this.pageCount = WrittenBookItem.getPageCount(this.book);
        this.setChanged();
    }

    void setPage(int i) {
        int j = Mth.clamp(i, 0, this.pageCount - 1);
        if (j != this.page) {
            this.page = j;
            this.setChanged();
            LecternBlock.signalPageChange(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }

    }

    public int getPage() {
        return this.page;
    }

    public int getRedstoneSignal() {
        float f = this.pageCount > 1 ? (float)this.getPage() / ((float)this.pageCount - 1.0F) : 1.0F;
        return Mth.floor(f * 14.0F) + (this.hasBook() ? 1 : 0);
    }

    private ItemStack resolveBook(ItemStack arg, @Nullable Player arg2) {
        if (this.level instanceof ServerLevel && arg.is(Items.WRITTEN_BOOK)) {
            WrittenBookItem.resolveBookComponents(arg, this.createCommandSourceStack(arg2), arg2);
        }

        return arg;
    }

    private CommandSourceStack createCommandSourceStack(@Nullable Player arg) {
        String string;
        Component component;
        if (arg == null) {
            string = "Lectern";
            component = Component.literal("Lectern");
        } else {
            string = arg.getName().getString();
            component = arg.getDisplayName();
        }

        Vec3 vec3 = Vec3.atCenterOf(this.worldPosition);
        return new CommandSourceStack(CommandSource.NULL, vec3, Vec2.ZERO, (ServerLevel)this.level, 2, string, component, this.level.getServer(), arg);
    }

    public boolean onlyOpCanSetNbt() {
        return true;
    }

    public void load(CompoundTag arg) {
        super.load(arg);
        if (arg.contains("Book", 10)) {
            this.book = this.resolveBook(ItemStack.of(arg.getCompound("Book")), (Player)null);
        } else {
            this.book = ItemStack.EMPTY;
        }

        this.pageCount = WrittenBookItem.getPageCount(this.book);
        this.page = Mth.clamp(arg.getInt("Page"), 0, this.pageCount - 1);
    }

    protected void saveAdditional(CompoundTag arg) {
        super.saveAdditional(arg);
        if (!this.getBook().isEmpty()) {
            arg.put("Book", this.getBook().save(new CompoundTag()));
            arg.putInt("Page", this.page);
        }

    }

    public void clearContent() {
        this.setBook(ItemStack.EMPTY);
    }

    public AbstractContainerMenu createMenu(int i, Inventory arg, Player arg2) {
        return new LecternMenu(i, this.bookAccess, this.dataAccess);
    }

    public Component getDisplayName() {
        return Component.literal("Script Transmitter");
    }
}
