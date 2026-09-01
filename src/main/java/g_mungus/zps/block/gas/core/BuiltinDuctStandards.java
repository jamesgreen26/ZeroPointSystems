package g_mungus.zps.block.gas.core;

import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;

/**
 * Gas equivalent of {@link BuiltinCableStandards}: two blocks only form an edge when they share a
 * standard.
 */
public class BuiltinDuctStandards {
    public static final String DEFAULT = "zps:gas";
}
