package cl.mauricio.balatromods;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SnapshotStore {
    private static final String PREFS = "balatro_mod_deck_snapshots";
    private static final String KEY = "items";
    private static final int DEFAULT_MAX_SNAPSHOTS = 20;
    private static final String RETENTION_KEY = "retention_limit";

    private final SharedPreferences preferences;

    public SnapshotStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Snapshot create(String label, List<ModEntry> mods) {
        Map<String, Boolean> states = new LinkedHashMap<>();
        for (ModEntry mod : mods) {
            states.put(mod.folderName, mod.hidden);
        }
        Snapshot snapshot = new Snapshot(
                UUID.randomUUID().toString(),
                label,
                System.currentTimeMillis(),
                states
        );
        List<Snapshot> items = new ArrayList<>(list());
        items.add(0, snapshot);
        int limit = retentionLimit();
        if (limit > 0 && items.size() > limit) {
            items = new ArrayList<>(items.subList(0, limit));
        }
        save(items);
        return snapshot;
    }

    public List<Snapshot> list() {
        List<Snapshot> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                JSONObject rawStates = item.getJSONObject("states");
                Map<String, Boolean> states = new LinkedHashMap<>();
                java.util.Iterator<String> keys = rawStates.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    states.put(name, rawStates.optBoolean(name));
                }
                result.add(new Snapshot(
                        item.getString("id"),
                        item.optString("label", "Snapshot"),
                        item.optLong("createdAt"),
                        states
                ));
            }
        } catch (Exception ignored) {
            preferences.edit().remove(KEY).apply();
        }
        return List.copyOf(result);
    }

    public Snapshot latest() {
        List<Snapshot> snapshots = list();
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    public Snapshot find(String id) {
        for (Snapshot snapshot : list()) {
            if (snapshot.id().equals(id)) {
                return snapshot;
            }
        }
        return null;
    }

    public boolean remove(String id) {
        if (id == null || id.isBlank()) return false;
        List<Snapshot> remaining = new ArrayList<>();
        boolean removed = false;
        for (Snapshot snapshot : list()) {
            if (snapshot.id().equals(id)) {
                removed = true;
            } else {
                remaining.add(snapshot);
            }
        }
        if (removed) save(remaining);
        return removed;
    }

    public void setRetentionLimit(int limit) {
        int normalized = Math.max(0, Math.min(limit, 1000));
        preferences.edit().putInt(RETENTION_KEY, normalized).apply();
        if (normalized > 0) {
            List<Snapshot> items = list();
            if (items.size() > normalized) save(new ArrayList<>(items.subList(0, normalized)));
        }
    }

    private int retentionLimit() {
        return preferences.getInt(RETENTION_KEY, DEFAULT_MAX_SNAPSHOTS);
    }

    private void save(List<Snapshot> snapshots) {
        JSONArray array = new JSONArray();
        for (Snapshot snapshot : snapshots) {
            array.put(snapshot.toJson());
        }
        preferences.edit().putString(KEY, array.toString()).apply();
    }

    public record Snapshot(
            String id,
            String label,
            long createdAt,
            Map<String, Boolean> states
    ) {
        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("label", label);
                object.put("createdAtEpoch", createdAt);
                object.put(
                        "createdAt",
                        DateFormat.getDateTimeInstance(
                                DateFormat.SHORT,
                                DateFormat.SHORT,
                                Locale.getDefault()
                        ).format(new Date(createdAt))
                );
                object.put("entries", states.size());
                object.put("states", new JSONObject(states));
            } catch (Exception error) {
                throw new IllegalStateException("Could not serialize snapshot.", error);
            }
            return object;
        }
    }
}
