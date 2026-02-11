package g_mungus.zps.commands.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.BiFunction;

public sealed class ScriptMapper<I, O> implements ScriptNode permits ScriptMapper2 {
    private final String displayName;
    private final Class<I> inputType;
    private final Class<O> outputType;
    private final ResourceLocation inputKey;
    private final ResourceLocation outputKey;
    private final BiFunction<I, ScriptContext, O> function;

    public ScriptMapper(
            String displayName,
            Class<I> inputType,
            Class<O> outputType,
            ResourceLocation inputKey,
            ResourceLocation outputKey,
            BiFunction<I, ScriptContext, O> function
    ) {
        this.displayName = displayName;
        this.inputType = inputType;
        this.outputType = outputType;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
        this.function = function;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    public Class<I> inputType() {
        return inputType;
    }

    public Class<O> outputType() {
        return outputType;
    }

    public ResourceLocation inputKey() {
        return inputKey;
    }

    public ResourceLocation outputKey() {
        return outputKey;
    }

    public BiFunction<I, ScriptContext, O> function() {
        return function;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ScriptMapper) obj;
        return Objects.equals(this.displayName, that.displayName) &&
                Objects.equals(this.inputType, that.inputType) &&
                Objects.equals(this.outputType, that.outputType) &&
                Objects.equals(this.inputKey, that.inputKey) &&
                Objects.equals(this.outputKey, that.outputKey) &&
                Objects.equals(this.function, that.function);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, inputType, outputType, inputKey, outputKey, function);
    }

    @Override
    public String toString() {
        return "ScriptMapper[" +
                "displayName=" + displayName + ", " +
                "inputType=" + inputType + ", " +
                "outputType=" + outputType + ", " +
                "inputKey=" + inputKey + ", " +
                "outputKey=" + outputKey + ", " +
                "function=" + function + ']';
    }
}
