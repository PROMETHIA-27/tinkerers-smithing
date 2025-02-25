package folk.sisby.tinkerers_smithing.data;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Pair;
import net.minecraft.util.profiler.Profiler;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class MultiJsonDataLoader extends SinglePreparationResourceReloader<Map<Identifier, Collection<Pair<JsonElement, String>>>> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String FILE_SUFFIX = ".json";
	private final Gson gson;
	private final String dataType;

	public MultiJsonDataLoader(Gson gson, String dataType) {
		this.gson = gson;
		this.dataType = dataType;
	}

	@Override
	protected Map<Identifier, Collection<Pair<JsonElement, String>>> prepare(ResourceManager manager, Profiler profiler) {
		Map<Identifier, Collection<Pair<JsonElement, String>>> outMap = Maps.newHashMap();

		for (Map.Entry<Identifier, List<Resource>> entry : manager.findAllResources(this.dataType, id -> id.getPath().endsWith(".json")).entrySet()) {
			Identifier fileId = entry.getKey();
			Identifier id = new Identifier(fileId.getNamespace(), fileId.getPath().substring(dataType.length() + 1, fileId.getPath().length() - FILE_SUFFIX.length()));

			for (Resource resource : entry.getValue()) {
				try {
					try (Reader reader = resource.getReader()) {
						JsonElement jsonContents = JsonHelper.deserialize(this.gson, reader, JsonElement.class);
						outMap.computeIfAbsent(id, k -> new ArrayList<>()).add(new Pair<>(jsonContents, resource.getResourcePackName()));
					}
				} catch (IllegalArgumentException | IOException | JsonParseException e) {
					LOGGER.error("Couldn't parse data file {} from {}", id, fileId, e);
				}
			}
		}

		// Apply load conditions
		Iterator<Map.Entry<Identifier, Collection<Pair<JsonElement, String>>>> it = outMap.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Identifier, Collection<Pair<JsonElement, String>>> entry = it.next();
			Iterator<Pair<JsonElement, String>> it2 = entry.getValue().iterator();
			while (it2.hasNext()) {
				JsonElement resourceData = it2.next().getLeft();
				if (resourceData.isJsonObject()) {
					JsonObject obj = resourceData.getAsJsonObject();
					if (obj.has(ResourceConditions.CONDITIONS_KEY) && !ResourceConditions.objectMatchesConditions(obj)) {
						it2.remove();
					}
				}
			}
			if (entry.getValue().isEmpty()) it.remove();
		}

		return outMap;
	}
}
