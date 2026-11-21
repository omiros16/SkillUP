package com.omiros.skillup;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** All tasks live in one small JSON file; settings in SharedPreferences. */
final class Store {
    private static final String FILE = "skillup.json";

    private Store() {
    }

    static List<Engine.Task> load(Context c) {
        File file = new File(c.getFilesDir(), FILE);
        AtomicFile f = new AtomicFile(file);
        if (!file.exists()) return new ArrayList<>();
        try {
            return parse(new String(f.readFully(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Never overwrite a file we cannot read: set it aside and start empty.
            file.renameTo(new File(c.getFilesDir(), "skillup-unreadable-" + System.currentTimeMillis() + ".json"));
            return new ArrayList<>();
        }
    }

    static boolean save(Context c, List<Engine.Task> tasks) {
        AtomicFile f = new AtomicFile(new File(c.getFilesDir(), FILE));
        FileOutputStream out = null;
        try {
            out = f.startWrite();
            out.write(toJson(tasks).getBytes(StandardCharsets.UTF_8));
            f.finishWrite(out);
            return true;
        } catch (IOException | JSONException e) {
            if (out != null) f.failWrite(out);
            return false;
        }
    }

    static String toJson(List<Engine.Task> tasks) throws JSONException {
        JSONArray arr = new JSONArray();
        for (Engine.Task t : tasks) {
            JSONArray scheds = new JSONArray();
            for (Engine.Sched s : t.scheds) {
                scheds.put(new JSONObject().put("from", s.from.toString()).put("days", s.days).put("times", s.times));
            }
            JSONObject marks = new JSONObject();
            for (java.util.Map.Entry<LocalDate, Character> e : t.marks.entrySet()) {
                marks.put(e.getKey().toString(), String.valueOf(e.getValue()));
            }
            arr.put(new JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("min", t.minimum)
                    .put("scheds", scheds)
                    .put("marks", marks));
        }
        return new JSONObject().put("app", "SkillUP").put("version", 1).put("tasks", arr).toString();
    }

    /** Also reads imported backups, so everything is checked; throws on anything unexpected. */
    static List<Engine.Task> parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!"SkillUP".equals(root.optString("app"))) throw new JSONException("not a SkillUP file");
        JSONArray arr = root.getJSONArray("tasks");
        List<Engine.Task> tasks = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Engine.Task t = new Engine.Task();
            t.id = o.getLong("id");
            t.name = o.getString("name").trim();
            t.minimum = o.optString("min", "").trim();
            if (t.name.isEmpty()) throw new JSONException("empty name");

            JSONArray scheds = o.getJSONArray("scheds");
            if (scheds.length() == 0) throw new JSONException("no schedule");
            for (int k = 0; k < scheds.length(); k++) {
                JSONObject s = scheds.getJSONObject(k);
                int days = s.getInt("days"), times = s.getInt("times");
                boolean valid = times > 0 ? times <= 7 : times == 0 && days > 0 && days <= 0x7F;
                if (!valid) throw new JSONException("bad schedule");
                Engine.Sched sc = new Engine.Sched(LocalDate.parse(s.getString("from")), days, times);
                if (!t.scheds.isEmpty() && !sc.from.isAfter(t.current().from)) throw new JSONException("unsorted");
                t.scheds.add(sc);
            }

            JSONObject marks = o.getJSONObject("marks");
            for (Iterator<String> it = marks.keys(); it.hasNext(); ) {
                String key = it.next();
                String v = marks.getString(key);
                if (!v.equals("D") && !v.equals("B") && !v.equals("S")) throw new JSONException("bad mark");
                t.marks.put(LocalDate.parse(key), v.charAt(0));
            }
            tasks.add(t);
        }
        return tasks;
    }

    // ---------------------------------------------------------------- settings

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean remindOn(Context c) {
        return prefs(c).getBoolean("remindOn", true);
    }

    /** Minutes after midnight. */
    static int remindAt(Context c) {
        return prefs(c).getInt("remindAt", 21 * 60);
    }

    static void setReminder(Context c, boolean on, int at) {
        prefs(c).edit().putBoolean("remindOn", on).putInt("remindAt", at).apply();
    }

    static boolean askedPermission(Context c) {
        return prefs(c).getBoolean("askedPermission", false);
    }

    static void setAskedPermission(Context c) {
        prefs(c).edit().putBoolean("askedPermission", true).apply();
    }
}
