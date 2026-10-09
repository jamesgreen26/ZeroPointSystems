package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.munguscript.engine.codec.ArgumentShape;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.neoforged.neoforge.server.command.EnumArgument;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * How the Minecraft and NeoForge argument types scripts use read, for the language file editors
 * read scripts by ({@link ZPSScripts}). They are not ZPS's to describe themselves, and read
 * loosely, as one word, the file would have {@code pos == 0} right and {@code pos == 0 0 0} wrong.
 */
final class ZPSArgumentShapes {
    private static final String ID = "[a-z0-9_.-]+(:[a-z0-9_./-]+)?";
    /** A block coordinate: {@code 5}, {@code ~}, {@code ~-2} or {@code ^1}; a relative one may have a fraction. */
    private static final ArgumentShape BLOCK_COORDINATE =
            new ArgumentShape.Matching("-?\\d+|[~^](-?(\\d+(\\.\\d*)?|\\.\\d+))?", "a coordinate");
    /** A coordinate of a position or vector: as a block's, with a fraction anywhere. */
    private static final ArgumentShape COORDINATE =
            new ArgumentShape.Matching("[~^]?-?(\\d+(\\.\\d*)?|\\.\\d+)|[~^]", "a coordinate");
    private static final ArgumentShape BLOCK_POS =
            new ArgumentShape.Sequence(List.of(BLOCK_COORDINATE, BLOCK_COORDINATE, BLOCK_COORDINATE));
    private static final ArgumentShape VEC3 = new ArgumentShape.Sequence(List.of(COORDINATE, COORDINATE, COORDINATE));
    private static final ArgumentShape DIMENSION = new ArgumentShape.Matching(ID, "a dimension id");
    /** {@code stone}, {@code minecraft:stone[facing=north]} or {@code #logs}, with no spaces. */
    private static final ArgumentShape BLOCK_PREDICATE =
            new ArgumentShape.Matching("#?" + ID + "(\\[[^\\] ]*\\])?(\\{\\S*\\})?", "a block or #tag");
    private static final ArgumentShape ITEM_PREDICATE =
            new ArgumentShape.Matching("\\*|#?" + ID + "(\\[[^\\] ]*\\])?(\\{\\S*\\})?", "an item, #tag or *");

    private ZPSArgumentShapes() {
    }

    /** {@code type}'s shape, or null for one munguscript describes itself. */
    static @Nullable ArgumentShape of(ArgumentType<?> type) {
        if (type instanceof BlockPosArgument) {
            return BLOCK_POS;
        } else if (type instanceof Vec3Argument) {
            return VEC3;
        } else if (type instanceof DimensionArgument) {
            return DIMENSION;
        } else if (type instanceof BlockPredicateArgument) {
            return BLOCK_PREDICATE;
        } else if (type instanceof ItemPredicateArgument) {
            return ITEM_PREDICATE;
        } else if (type instanceof EnumArgument<?> constants) {
            // Its examples are the constants' names, which are what it reads.
            return new ArgumentShape.OneOf(List.copyOf(constants.getExamples()), false);
        }
        return null;
    }
}
