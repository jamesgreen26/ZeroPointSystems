package g_mungus.zps.client.model.connected;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.client.model.geometry.IGeometryLoader;

import java.util.List;

public class ConnectedModelLoader implements IGeometryLoader<ConnectedGeometry> {
    public static final ConnectedModelLoader INSTANCE = new ConnectedModelLoader();
    public static final String NAME = "model";

    private ConnectedModelLoader() {}

    @Override
    public ConnectedGeometry read(JsonObject jsonObject, JsonDeserializationContext context) throws JsonParseException {
        String type = GsonHelper.getAsString(jsonObject, "type", "connecting");
        if (!"connecting".equals(type)) {
            throw new JsonParseException("Unsupported zps:model type: " + type);
        }
        List<ConnectionRule> rules = ConnectionRule.parse(
                jsonObject.has("connections") ? GsonHelper.getAsJsonArray(jsonObject, "connections") : null);

        JsonObject stripped = jsonObject.deepCopy();
        stripped.remove("loader");
        stripped.remove("type");
        stripped.remove("connections");

        BlockModel inner = BlockModel.fromString(stripped.toString());
        return new ConnectedGeometry(inner, rules);
    }
}
