package g_mungus.zps.commands.sync;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import g_mungus.munguscript.engine.codec.HostCodec;
import g_mungus.munguscript.language.node.Applicability;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.TargetApplicability;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;

/**
 * Writes the parts of the script tree that are Minecraft's: argument types, through the same
 * serialisers the vanilla command packet uses, and which targets a node is for, each kind of
 * {@link TargetApplicability} through its own codec.
 */
public final class ZPSHostCodec implements HostCodec {
    /** Written in place of an argument type no serialiser is registered for. */
    private static final String UNKNOWN = "";

    private final CommandBuildContext buildContext;
    private final Map<ResourceLocation, TargetApplicability.Type<?>> applicabilities;

    /**
     * @param buildContext    what argument types that read registries are made with when read
     * @param applicabilities the kinds of applicability that can be read, by id
     */
    public ZPSHostCodec(CommandBuildContext buildContext, Map<ResourceLocation, TargetApplicability.Type<?>> applicabilities) {
        this.buildContext = buildContext;
        this.applicabilities = Map.copyOf(applicabilities);
    }

    @Override
    public void writeArgumentType(DataOutput out, ArgumentType<?> type) throws IOException {
        if (!ArgumentTypeInfos.isClassRecognized(type.getClass())) {
            ZPSMod.LOGGER.warn("Script argument type {} has no serialiser; clients will read it as a word",
                    type.getClass().getName());
            out.writeUTF(UNKNOWN);
            return;
        }
        writeTemplate(out, ArgumentTypeInfos.unpack(type));
    }

    private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void writeTemplate(
            DataOutput out, ArgumentTypeInfo.Template<A> template) throws IOException {
        @SuppressWarnings("unchecked")
        ArgumentTypeInfo<A, T> info = (ArgumentTypeInfo<A, T>) template.type();
        ResourceLocation id = BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(info);
        if (id == null) {
            throw new IOException("Argument type info " + info + " is not registered");
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            info.serializeToNetwork((T) template, buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            out.writeUTF(id.toString());
            out.writeInt(bytes.length);
            out.write(bytes);
        } finally {
            buffer.release();
        }
    }

    @Override
    public ArgumentType<?> readArgumentType(DataInput in) throws IOException {
        String id = in.readUTF();
        if (id.equals(UNKNOWN)) {
            return StringArgumentType.word();
        }
        ArgumentTypeInfo<?, ?> info = BuiltInRegistries.COMMAND_ARGUMENT_TYPE.get(ResourceLocation.parse(id));
        if (info == null) {
            throw new IOException("Unknown argument type " + id);
        }
        int length = in.readInt();
        if (length < 0) {
            throw new IOException("Negative length for argument type " + id);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            return info.deserializeFromNetwork(buffer).instantiate(buildContext);
        } finally {
            buffer.release();
        }
    }

    @Override
    public void writeApplicability(DataOutput out, Applicability applicability) throws IOException {
        if (!(applicability instanceof TargetApplicability target)) {
            throw new IOException("No way to send applicability " + applicability);
        }
        writeApplicability(out, target.type(), target);
    }

    private static <T extends TargetApplicability> void writeApplicability(DataOutput out, TargetApplicability.Type<T> type,
                                                                           TargetApplicability applicability) throws IOException {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            @SuppressWarnings("unchecked")
            T typed = (T) applicability;
            type.codec().encode(buffer, typed);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            out.writeUTF(type.id().toString());
            out.writeInt(bytes.length);
            out.write(bytes);
        } finally {
            buffer.release();
        }
    }

    @Override
    public Applicability readApplicability(DataInput in) throws IOException {
        String id = in.readUTF();
        TargetApplicability.Type<?> type = applicabilities.get(ResourceLocation.parse(id));
        if (type == null) {
            throw new IOException("Unknown applicability " + id + "; is a mod on the server missing here?");
        }
        int length = in.readInt();
        if (length < 0) {
            throw new IOException("Negative length for applicability " + id);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            return type.codec().decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
