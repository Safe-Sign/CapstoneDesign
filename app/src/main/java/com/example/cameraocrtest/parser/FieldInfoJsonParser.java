package com.example.cameraocrtest.parser;

import android.content.Context;

import com.example.cameraocrtest.data.FieldInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FieldInfoJsonParser {
    public static List<FieldInfo> loadFromAsset(Context context, String assetFileName) {
        List<FieldInfo> fields = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(assetFileName)))) {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            JSONObject root = new JSONObject(jsonBuilder.toString());
            JSONArray items = root.optJSONArray("fields");
            if (items == null) {
                return fields;
            }

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String key = item.optString("key", "");
                String label = item.optString("label", "");
                boolean sensitive = item.optBoolean("sensitive", true);
                String description = item.optString("description", "");
                if (!label.isEmpty()) {
                    fields.add(new FieldInfo(key, label, sensitive, description));
                }
            }
        } catch (Exception ignored) {
            // If parsing fails, caller falls back to built-in defaults.
        }
        return fields;
    }

    public static Set<String> buildSensitiveTagSet(List<FieldInfo> fields) {
        Set<String> tags = new HashSet<>();
        for (FieldInfo field : fields) {
            if (field.isSensitive()) {
                tags.add(field.getLabel());
            }
        }
        return tags;
    }
}
