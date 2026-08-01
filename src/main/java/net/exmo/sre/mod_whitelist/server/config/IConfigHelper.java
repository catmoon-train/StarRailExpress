/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.exmo.sre.mod_whitelist.server.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

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
