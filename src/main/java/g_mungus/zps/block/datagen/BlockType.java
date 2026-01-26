package g_mungus.zps.block.datagen;

import java.util.Set;

public enum BlockType {
    simple, stairs, slab, pillar, fence, wall;

    public static final Set<String> suffixes = Set.of(
            "_stairs",
            "_slab",
            "_pillar",
            "_fence",
            "_wall"
    );
}
