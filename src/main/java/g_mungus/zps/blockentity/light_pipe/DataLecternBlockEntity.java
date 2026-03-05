package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

public class DataLecternBlockEntity extends NetworkTerminalImpl implements Clearable, MenuProvider, LightPipeDataSender, BookHolder {

    private final Container bookAccess = new Container() {
        public int getContainerSize() {
            return 1;
        }

        public boolean isEmpty() {
            return DataLecternBlockEntity.this.book.isEmpty();
        }

        public ItemStack getItem(int i) {
            return i == 0 ? DataLecternBlockEntity.this.book : ItemStack.EMPTY;
        }

        public ItemStack removeItem(int i, int j) {
            if (i == 0) {
                ItemStack itemStack = DataLecternBlockEntity.this.book.split(j);
                if (DataLecternBlockEntity.this.book.isEmpty()) {
                    DataLecternBlockEntity.this.onBookItemRemove();
                }

                return itemStack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        public ItemStack removeItemNoUpdate(int i) {
            if (i == 0) {
                ItemStack itemStack = DataLecternBlockEntity.this.book;
                DataLecternBlockEntity.this.book = ItemStack.EMPTY;
                DataLecternBlockEntity.this.onBookItemRemove();
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
            DataLecternBlockEntity.this.setChanged();
        }

        public boolean stillValid(Player arg) {
            return Container.stillValidBlockEntity(DataLecternBlockEntity.this, arg) && DataLecternBlockEntity.this.hasBook();
        }

        public boolean canPlaceItem(int i, ItemStack arg) {
            return false;
        }

        public void clearContent() {
        }
    };
    private final ContainerData dataAccess = new ContainerData() {
        public int get(int i) {
            return i == 0 ? DataLecternBlockEntity.this.page : 0;
        }

        public void set(int i, int j) {
            if (i == 0) {
                DataLecternBlockEntity.this.zps$setPage(j);
            }

        }

        public int getCount() {
            return 1;
        }
    };
    ItemStack book;
    int page;

    public int getPageCount() {
        return pageCount;
    }

    private int pageCount;

    @Override
    public String provideNextDisplayText(int length) {
        String contents = getPageContents();
        return contents.substring(0, Math.min(length, contents.length()));
    }

    private String getPageContents() {
        if (book != null) {
            CompoundTag tag = book.getTag();
            if (tag != null) {
                ListTag pages = tag.getList("pages", 8);
                if (pages.size() > page) {
                    return pages.getString(page);
                }
            }
        }
        return "";
    }

    @Override
    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        super.defineTerminals(terminals, channel);
        if (level != null) updateSignal(level);
    }

    public DataLecternBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_LECTERN.get(), pos, state);
        this.book = ItemStack.EMPTY;
    }


    @Override
    public ItemStack zps$getBook() {
        return getBook();
    }

    @Override
    public int zps$getCurrentPage() {
        return page;
    }

    @Override
    public void zps$onPageWritten() {
        if (level != null) updateSignal(level);
    }

    @Override
    public void zps$onPageAdded() {
        pageCount++;
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

    public void onBookItemRemove() {
        this.page = 0;
        this.pageCount = 0;
        LecternBlock.resetBookState(null, this.getLevel(), this.getBlockPos(), this.getBlockState(), false);
        if (level != null) updateSignal(level);
    }

    public void setBook(ItemStack arg, @Nullable Player arg2) {
        this.book = this.resolveBook(arg, arg2);
        this.page = 0;
        this.pageCount = WrittenBookItem.getPageCount(this.book);
        this.setChanged();
        if (level != null) updateSignal(level);
    }

    @Override
    public void zps$setPage(int i) {
        int j = Mth.clamp(i, 0, this.pageCount - 1);
        if (j != this.page) {
            this.page = j;
            this.setChanged();
            if (level != null) {
                LecternBlock.signalPageChange(level, this.getBlockPos(), this.getBlockState());
                updateSignal(level);
            }
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

    public void load(@NotNull CompoundTag arg) {
        super.load(arg);
        if (arg.contains("Book", 10)) {
            this.book = this.resolveBook(ItemStack.of(arg.getCompound("Book")), null);
        } else {
            this.book = ItemStack.EMPTY;
        }

        this.pageCount = WrittenBookItem.getPageCount(this.book);
        this.page = Mth.clamp(arg.getInt("Page"), 0, this.pageCount - 1);
    }

    protected void saveAdditional(@NotNull CompoundTag arg) {
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
        return Component.literal("Data Lectern");
    }

    public boolean hasNextPage() {
        return getPage() + 1 < getPageCount();
    }

    public void turnPage() {
        if (hasNextPage()) {
            zps$setPage(getPage() + 1);
        }
    }

    @Override
    public void zps$cyclePages() {
        if (hasNextPage()) {
            zps$setPage(getPage() + 1);
        } else {
            zps$setPage(0);
        }
    }

    @Override
    public boolean zps$hasBook() {
        return hasBook();
    }
}
