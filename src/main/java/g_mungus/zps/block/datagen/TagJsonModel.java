package g_mungus.zps.block.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public record TagJsonModel(List<String> values, boolean replace) {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public String toJsonString() {
        return GSON.toJson(this);
    }
}
