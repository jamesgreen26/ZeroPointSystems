package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.AssemblerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class AssemblerGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos POS = new BlockPos(3, 1, 3);
    private static final BlockPos REDSTONE_POS = new BlockPos(3, 1, 2);

    /** A powered assembler crafts a shaped recipe from ingredients in the input buffer. */
    @GameTest(template = TEMPLATE)
    public static void craftsShapedWhenPowered(GameTestHelper helper) {
        AssemblerBlockEntity assembler = place(helper);
        assembler.getEnergyStorage(null).receiveEnergy(512, false);
        setCraftingTablePattern(assembler);
        modifiable(assembler.getInputInventory()).setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 4));

        helper.setBlock(REDSTONE_POS, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(25, () -> {
            ItemStack out = assembler.getOutputInventory().getStackInSlot(0);
            if (!ItemStack.isSameItem(out, Items.CRAFTING_TABLE.getDefaultInstance()) || out.getCount() < 1) {
                helper.fail("expected a crafting table in the output buffer, got " + out);
            }
            helper.succeed();
        });
    }

    /** With everything staged but no redstone signal, the assembler must not craft. */
    @GameTest(template = TEMPLATE)
    public static void doesNotCraftWithoutRedstone(GameTestHelper helper) {
        AssemblerBlockEntity assembler = place(helper);
        assembler.getEnergyStorage(null).receiveEnergy(512, false);
        setCraftingTablePattern(assembler);
        modifiable(assembler.getInputInventory()).setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 4));

        helper.runAfterDelay(30, () -> {
            ItemStack out = assembler.getOutputInventory().getStackInSlot(0);
            if (!out.isEmpty()) {
                helper.fail("assembler crafted without a redstone signal: " + out);
            }
            ItemStack input = assembler.getInputInventory().getStackInSlot(0);
            if (input.getCount() != 4) {
                helper.fail("input was consumed without a redstone signal: " + input);
            }
            helper.succeed();
        });
    }

    /** Automation may insert into the input buffer and extract from the output, but not vice versa. */
    @GameTest(template = TEMPLATE)
    public static void automationRespectsBufferDirections(GameTestHelper helper) {
        AssemblerBlockEntity assembler = place(helper);
        IItemHandler automation = assembler.getItemHandler(null);
        int inputSlots = assembler.getInputInventory().getSlots();

        // Insert into an input slot: accepted.
        ItemStack leftover = automation.insertItem(0, new ItemStack(Items.OAK_PLANKS, 4), false);
        if (!leftover.isEmpty()) {
            helper.fail("input buffer rejected insertion, leftover " + leftover);
        }
        // Extract from that same input slot: blocked.
        if (!automation.extractItem(0, 64, false).isEmpty()) {
            helper.fail("automation extracted from the input buffer; expected nothing");
        }
        // Seed the output buffer and confirm automation can pull it, but cannot insert.
        modifiable(assembler.getOutputInventory()).setStackInSlot(0, new ItemStack(Items.CRAFTING_TABLE, 2));
        ItemStack rejected = automation.insertItem(inputSlots, new ItemStack(Items.CRAFTING_TABLE, 1), false);
        if (rejected.getCount() != 1) {
            helper.fail("output buffer accepted insertion; expected rejection, leftover " + rejected);
        }
        ItemStack pulled = automation.extractItem(inputSlots, 64, false);
        if (!ItemStack.isSameItem(pulled, Items.CRAFTING_TABLE.getDefaultInstance()) || pulled.getCount() != 2) {
            helper.fail("automation could not extract from the output buffer, got " + pulled);
        }
        helper.succeed();
    }

    private static void setCraftingTablePattern(AssemblerBlockEntity assembler) {
        // 2x2 of oak planks in the top-left of the 5x5 grid.
        IItemHandlerModifiable pattern = modifiable(assembler.getPatternInventory());
        pattern.setStackInSlot(0, new ItemStack(Items.OAK_PLANKS));
        pattern.setStackInSlot(1, new ItemStack(Items.OAK_PLANKS));
        pattern.setStackInSlot(AssemblerBlockEntity.GRID_WIDTH, new ItemStack(Items.OAK_PLANKS));
        pattern.setStackInSlot(AssemblerBlockEntity.GRID_WIDTH + 1, new ItemStack(Items.OAK_PLANKS));
    }

    private static AssemblerBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.ASSEMBLER.get());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        if (!(blockEntity instanceof AssemblerBlockEntity assembler)) {
            helper.fail("Expected assembler at " + POS + ", got " + blockEntity);
            throw new IllegalStateException("unreachable");
        }
        return assembler;
    }

    private static IItemHandlerModifiable modifiable(IItemHandler handler) {
        return (IItemHandlerModifiable) handler;
    }
}
