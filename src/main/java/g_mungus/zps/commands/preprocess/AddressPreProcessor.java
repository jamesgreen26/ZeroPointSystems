package g_mungus.zps.commands.preprocess;

import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.PreProcessed;
import g_mungus.munguscript.engine.preprocess.PreProcessorToken;
import g_mungus.munguscript.engine.preprocess.Rewriter;
import g_mungus.zps.commands.api.ZPSScriptTypes;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Addresses from a Script Terminal's Address Pad: {@code @name} anywhere coordinates go is
 * replaced with the coordinates the pad stores under that name. An {@code @name} the pad does not
 * have is left as written, for the command to fail on where the player can see it.
 */
public final class AddressPreProcessor implements CommandPreProcessor {
    private final Map<String, BlockPos> addresses;
    private final Pattern pattern;

    public AddressPreProcessor(Map<String, BlockPos> addresses) {
        this.addresses = Map.copyOf(addresses);
        // Longest first, so a name that starts another is not taken for it.
        String names = addresses.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .reduce((a, b) -> a + "|" + b)
                .orElse(null);
        this.pattern = names == null ? null : Pattern.compile("(?<![A-Za-z0-9._])@(" + names + ")(?![A-Za-z0-9._])");
    }

    @Override
    public Prepared prepare(List<String> scriptLines, PreProcessContext context) {
        return new Prepared() {
            @Override
            public Set<Integer> consumedLines() {
                return Set.of();
            }

            @Override
            public List<PreProcessDiagnostic> diagnostics() {
                return List.of();
            }

            @Override
            public PreProcessed process(String command, PreProcessContext context) {
                return AddressPreProcessor.this.process(command);
            }

            @Override
            public Collection<PreProcessorToken> tokens() {
                return addresses.entrySet().stream()
                        .map(entry -> new PreProcessorToken("@" + entry.getKey(), PreProcessorToken.Placement.ARGUMENT,
                                ZPSScriptTypes.BLOCK_POS.key(), coordinates(entry.getValue())))
                        .toList();
            }
        };
    }

    private PreProcessed process(String command) {
        if (pattern == null) {
            return PreProcessed.unchanged(command);
        }
        Matcher matcher = pattern.matcher(command);
        Rewriter rewriter = new Rewriter(command);
        int kept = 0;
        while (matcher.find()) {
            rewriter.keep(kept, matcher.start())
                    .replace(matcher.start(), matcher.end(), coordinates(addresses.get(matcher.group(1))));
            kept = matcher.end();
        }
        return PreProcessed.of(rewriter.keep(kept, command.length()).build());
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
