package g_mungus.zps.commands.preprocess;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.PreProcessed;
import g_mungus.munguscript.engine.preprocess.PreProcessorToken;
import g_mungus.munguscript.engine.preprocess.Rewriter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Relative ({@code ~ ~1 ~}) and local ({@code ^ ^ ^1}) coordinates, made absolute from where a
 * Script Terminal stands and faces. The terminal sends its commands down a pipe to Serial Buses
 * elsewhere, so relative coordinates have to mean the terminal's position before they leave it.
 */
public final class CoordinatePreProcessor implements CommandPreProcessor {
    private static final Pattern WORD = Pattern.compile("[^() ]+");

    private final CommandSourceStack origin;

    /** @param origin where relative coordinates are measured from, and which way local ones point */
    public CoordinatePreProcessor(CommandSourceStack origin) {
        this.origin = origin;
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
                return CoordinatePreProcessor.this.process(command);
            }

            @Override
            public Collection<PreProcessorToken> tokens() {
                return List.of();
            }
        };
    }

    private PreProcessed process(String command) {
        List<int[]> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(command);
        while (matcher.find()) {
            words.add(new int[]{matcher.start(), matcher.end()});
        }
        Rewriter rewriter = new Rewriter(command);
        int kept = 0;
        for (int i = 0; i + 2 < words.size(); i++) {
            int start = words.get(i)[0];
            int end = words.get(i + 2)[1];
            String w0 = command.substring(start, words.get(i)[1]);
            String w1 = command.substring(words.get(i + 1)[0], words.get(i + 1)[1]);
            String w2 = command.substring(words.get(i + 2)[0], end);
            boolean relative = w0.startsWith("~") && w1.startsWith("~") && w2.startsWith("~");
            boolean local = w0.startsWith("^") && w1.startsWith("^") && w2.startsWith("^");
            if (!relative && !local) {
                continue;
            }
            try {
                BlockPos pos = BlockPosArgument.blockPos()
                        .parse(new StringReader(w0 + " " + w1 + " " + w2))
                        .getBlockPos(origin);
                rewriter.keep(kept, start)
                        .replace(start, words.get(i)[1], Integer.toString(pos.getX()))
                        .keep(words.get(i)[1], words.get(i + 1)[0])
                        .replace(words.get(i + 1)[0], words.get(i + 1)[1], Integer.toString(pos.getY()))
                        .keep(words.get(i + 1)[1], words.get(i + 2)[0])
                        .replace(words.get(i + 2)[0], end, Integer.toString(pos.getZ()));
                kept = end;
                i += 2;
            } catch (CommandSyntaxException ignored) {
                // Not coordinates after all; left as written.
            }
        }
        return PreProcessed.of(rewriter.keep(kept, command.length()).build());
    }
}
