package g_mungus.zps.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public class AddressPadItem extends Item {
    private static final String POSITIONS_TAG = "positions";
    public static final int MAX_ENTRIES = 15;
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9._]+");

    public AddressPadItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    AddressPadClientHooks.openAddressPadScreen(context.getHand(), context.getClickedPos()));
        }

        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    AddressPadClientHooks.openAddressPadListScreen(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void putNamedPosition(ItemStack stack, String name, BlockPos pos, boolean enforceMaxEntries) {
        CompoundTag positions = getPositions(stack);
        removeEntryByPosition(positions, pos);
        if (enforceMaxEntries && positions.getAllKeys().size() >= MAX_ENTRIES) {
            return;
        }

        positions.putLong(name, pos.asLong());
        stack.getOrCreateTag().put(POSITIONS_TAG, positions);
    }

    public static void removeNamedPosition(ItemStack stack, String name) {
        CompoundTag positions = getPositions(stack);
        positions.remove(name);
        stack.getOrCreateTag().put(POSITIONS_TAG, positions);
    }

    public static CompoundTag getPositions(ItemStack stack) {
        return stack.getOrCreateTag().getCompound(POSITIONS_TAG);
    }

    public static boolean hasSpaceFor(ItemStack stack, BlockPos pos) {
        CompoundTag positions = getPositions(stack);
        if (containsPosition(positions, pos)) return true;
        return positions.getAllKeys().size() < MAX_ENTRIES;
    }

    public static List<Entry> getSortedEntries(ItemStack stack) {
        CompoundTag positions = getPositions(stack);
        List<Entry> result = new ArrayList<>();
        for (String name : positions.getAllKeys()) {
            if (!positions.contains(name, Tag.TAG_LONG)) continue;
            result.add(new Entry(name, BlockPos.of(positions.getLong(name))));
        }
        result.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static boolean isValidName(String name) {
        return name != null && !name.isEmpty() && VALID_NAME.matcher(name).matches();
    }

    private static boolean containsPosition(CompoundTag positions, BlockPos pos) {
        long target = pos.asLong();
        for (String key : positions.getAllKeys()) {
            if (!positions.contains(key, Tag.TAG_LONG)) continue;
            if (positions.getLong(key) == target) return true;
        }
        return false;
    }

    private static void removeEntryByPosition(CompoundTag positions, BlockPos pos) {
        String keyToRemove = null;
        long target = pos.asLong();
        for (String key : positions.getAllKeys()) {
            if (!positions.contains(key, Tag.TAG_LONG)) continue;
            if (positions.getLong(key) == target) {
                keyToRemove = key;
                break;
            }
        }
        if (keyToRemove != null) positions.remove(keyToRemove);
    }

    public record Entry(String name, BlockPos pos) {}
}
