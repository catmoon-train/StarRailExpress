package io.wifi.starrailexpress.mod_whitelist.server.config;

import com.google.gson.*;
import org.jetbrains.annotations.Nullable;


import java.io.*;
import java.util.*;

public interface IConfigHelper {
	static void writeJsonToFile(Writer writer, @Nullable String key, JsonElement json, int tab) throws IOException {
		writer.write("\t".repeat(tab));
		if(key != null) {
			writer.write("\"" + key + "\": ");
		}
		if(json.isJsonObject()) {
			writer.write("{\n");
			boolean first = true;
			for(Map.Entry<String, JsonElement> entry: json.getAsJsonObject().entrySet()) {
				if(first) {
					first = false;
				} else {
					writer.write(",\n");
				}
				writeJsonToFile(writer, entry.getKey(), entry.getValue(), tab + 1);
			}
			writer.write("\n" + "\t".repeat(tab) + "}");
		} else if(json.isJsonArray()) {
			writer.write("[\n");
			boolean first = true;
			for (JsonElement element : json.getAsJsonArray()) {
				if (first) {
					first = false;
				} else {
					writer.write(",\n");
				}
				writeJsonToFile(writer, null, element, tab + 1);
			}
			writer.write("\n" + "\t".repeat(tab) + "]");
		} else if(json.isJsonPrimitive()) {
			JsonPrimitive jsonPrimitive = json.getAsJsonPrimitive();
			if(jsonPrimitive.isBoolean()) {
				writer.write(String.valueOf(jsonPrimitive.getAsBoolean()));
			} else if(jsonPrimitive.isNumber()) {
				writer.write(String.valueOf(jsonPrimitive.getAsNumber().floatValue()));
			} else if(jsonPrimitive.isString()) {
				writer.write('\"' + jsonPrimitive.getAsString() + '\"');
			}
		}
	}
}
