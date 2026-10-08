package g_mungus.zps.commands.api_impl;

import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.engine.host.RunState;
import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.node.ScriptContext;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.ScriptTarget;
import g_mungus.zps.commands.api.TargetApplicability;
import g_mungus.zps.commands.api.ZPSScriptContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Runs scripts on the server. The run state and the block a command is aimed at travel on the
 * source as a {@link ZPSScriptCommandSource}; a node meant for some targets is meant for the run
 * when the block it is aimed at is one of them.
 */
final class ZPSScriptHost implements ScriptHost<CommandSourceStack> {

    @Override
    public @Nullable RunState runState(CommandSourceStack source) {
        return source.source instanceof ZPSScriptCommandSource script ? script.runState() : null;
    }

    @Override
    public CommandSourceStack withRunState(CommandSourceStack source, RunState state) {
        ZPSScriptCommandSource script = source.source instanceof ZPSScriptCommandSource existing
                ? existing
                : new ZPSScriptCommandSource(source.source, BlockPos.containing(source.getPosition()), null);
        return source.withSource(script.withRunState(state));
    }

    @Override
    public Object hostContext(CommandSourceStack source) {
        return new ZPSScriptContext(target(source), source.getLevel(), source);
    }

    @Override
    public Match match(Applicability applicability, ScriptContext context) {
        if (!(applicability instanceof TargetApplicability target)) {
            return Match.UNRESTRICTED;
        }
        ZPSScriptContext zps = ZPSScriptContext.of(context);
        return target.appliesTo(zps.level(), ScriptTarget.at(zps.level(), zps.pos())) ? Match.EXPLICIT : Match.NONE;
    }

    @Override
    public String defaultNamespace() {
        return ZPSMod.MOD_ID;
    }

    static BlockPos target(CommandSourceStack source) {
        return source.source instanceof ZPSScriptCommandSource script
                ? script.target()
                : BlockPos.containing(source.getPosition());
    }
}
