package com.example.mediatest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import org.xmlpull.v1.XmlPullParser;
import java.io.File;
import java.io.FileWriter;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView cover;
    private TextView tvTitle, tvArtist, tvInfo, tvLog;
    private ProgressBar bar;
    private MediaController current;
    private String appsList = "";
    private final List<MediaController> sessions = new ArrayList<MediaController>();
    private int sel = -1; // -1 = авто
    private TextView tvStatus;
    private Button btnSel;
    private long ownPos = 0;
    private String lastTitle = "";
    private long updAtChange = -1;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView text(int size, int color) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private Button btn(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(l);
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(16), dp(16), dp(16), dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);

        cover = new ImageView(this);
        cover.setBackgroundColor(0xFF2C2C2E);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        top.addView(cover, new LinearLayout.LayoutParams(dp(180), dp(180)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), 0, 0, 0);

        tvTitle = text(22, Color.WHITE);
        tvArtist = text(16, 0xFF8E8E93);
        tvInfo = text(13, 0xFF8E8E93);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);

        LinearLayout ctr = new LinearLayout(this);
        ctr.setOrientation(LinearLayout.HORIZONTAL);
        ctr.addView(btn("|<  Назад", new View.OnClickListener() {
            public void onClick(View v) { doPrev(); }
        }));
        ctr.addView(btn("Пуск / Пауза", new View.OnClickListener() {
            public void onClick(View v) {
                doPlayPause();
            }
        }));
        ctr.addView(btn("Вперёд  >|", new View.OnClickListener() {
            public void onClick(View v) { doNext(); }
        }));

        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        keys.addView(btn("Клавиша: назад", new View.OnClickListener() {
            public void onClick(View v) { sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "клавиша назад"); }
        }));
        keys.addView(btn("Клавиша: пуск/пауза", new View.OnClickListener() {
            public void onClick(View v) { sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "клавиша пуск/пауза"); }
        }));
        keys.addView(btn("Клавиша: вперёд", new View.OnClickListener() {
            public void onClick(View v) { sendKey(KeyEvent.KEYCODE_MEDIA_NEXT, "клавиша вперёд"); }
        }));

        col.addView(tvTitle);
        col.addView(tvArtist);
        col.addView(tvInfo);
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(ctr);
        col.addView(keys);
        top.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(btn("1. Дать доступ к уведомлениям", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        }));
        row.addView(btn("2. Найти приложения музыки/BT", new View.OnClickListener() {
            public void onClick(View v) { findApps(); }
        }));
        btnSel = btn("Сессия: авто", new View.OnClickListener() {
            public void onClick(View v) {
                sel++;
                if (sel >= sessions.size()) sel = -1;
                refresh();
            }
        });
        row.addView(btnSel);
        root.addView(row);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(btn("3. TW: компоненты com.tw.bt", new View.OnClickListener() {
            public void onClick(View v) { appsList = "\n" + dumpManifest("com.tw.bt"); refresh(); }
        }));
        row2.addView(btn("4. TW: поиск TWUtil", new View.OnClickListener() {
            public void onClick(View v) { appsList = "\n" + probeTw(); refresh(); }
        }));
        row2.addView(btn("5. TW: классы com.tw.bt", new View.OnClickListener() {
            public void onClick(View v) {
                appsList = "\nСканирую классы com.tw.bt, подождите...\n";
                refresh();
                new Thread(new Runnable() {
                    public void run() {
                        final String r = dumpClasses("com.tw.bt");
                        handler.post(new Runnable() {
                            public void run() { appsList = "\n" + r; refresh(); }
                        });
                    }
                }).start();
            }
        }));
        root.addView(row2);
        tvStatus = text(14, 0xFFFFD60A);
        root.addView(tvStatus);

        ScrollView sv = new ScrollView(this);
        tvLog = text(13, Color.WHITE);
        sv.addView(tvLog);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void status(String m) {
        tvStatus.setText(m);
    }

    private void doPrev() {
        sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "назад");
    }

    private void doNext() {
        sendKey(KeyEvent.KEYCODE_MEDIA_NEXT, "вперёд");
    }

    private void doPlayPause() {
        sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "пуск/пауза");
    }

    private int indexOf(MediaController c) {
        for (int i = 0; i < sessions.size(); i++) if (sessions.get(i).equals(c)) return i;
        return -1;
    }

    private void sendKey(int code, String name) {
        long t = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0);
        KeyEvent up = new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0);
        if (current != null) {
            // напрямую в выбранную сессию (не зависит от того, кого система считает главным)
            boolean r1 = current.dispatchMediaButtonEvent(down);
            boolean r2 = current.dispatchMediaButtonEvent(up);
            status("Отправлено " + name + " -> сессия " + indexOf(current) + " (принято: " + r1 + "/" + r2 + ")");
        } else {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.dispatchMediaKeyEvent(down);
            am.dispatchMediaKeyEvent(up);
            status("Отправлено " + name + " через AudioManager (сессии нет)");
        }
    }

    private String dumpManifest(String pkg) {
        StringBuilder sb = new StringBuilder("Манифест " + pkg + ":\n");
        String ns = "http://schemas.android.com/apk/res/android";
        try {
            Resources r = getPackageManager().getResourcesForApplication(pkg);
            XmlResourceParser p = r.getAssets().openXmlResourceParser("AndroidManifest.xml");
            int ev;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev != XmlPullParser.START_TAG) continue;
                String n = p.getName();
                if (n.equals("service") || n.equals("receiver")) {
                    String nm = p.getAttributeValue(ns, "name");
                    String ex = p.getAttributeValue(ns, "exported");
                    sb.append(n.substring(0, 1).toUpperCase()).append(": ").append(nm)
                      .append(ex == null ? "" : " exp=" + ex).append("\n");
                } else if (n.equals("action")) {
                    sb.append("    ").append(p.getAttributeValue(ns, "name")).append("\n");
                } else if (n.equals("uses-permission")) {
                    String pn = p.getAttributeValue(ns, "name");
                    if (pn != null && (pn.contains("tw") || pn.contains("MEDIA") || pn.contains("BLUETOOTH")))
                        sb.append("perm: ").append(pn).append("\n");
                }
            }
        } catch (Throwable e) {
            sb.append("Ошибка: ").append(e).append("\n");
        }
        return sb.toString();
    }

    private String probeTw() {
        StringBuilder sb = new StringBuilder("Поиск TWUtil:\n");
        String[] names = {"android.tw.john.TWUtil", "com.tw.john.TWUtil", "android.tw.TWUtil", "com.android.tw.TWUtil"};
        for (String nm : names) {
            try {
                Class<?> c = Class.forName(nm);
                sb.append("НАЙДЕН ").append(nm).append("\n");
                for (Method m : c.getDeclaredMethods()) {
                    if (!Modifier.isPublic(m.getModifiers())) continue;
                    sb.append("  ").append(m.getName()).append("(");
                    Class<?>[] pt = m.getParameterTypes();
                    for (int i = 0; i < pt.length; i++) sb.append(i > 0 ? "," : "").append(pt[i].getSimpleName());
                    sb.append(")\n");
                }
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) {
                        try { sb.append("  ").append(f.getName()).append("=").append(f.getInt(null)).append("\n"); } catch (Throwable ignore) {}
                    }
                }
            } catch (Throwable e) {
                sb.append("нет ").append(nm).append("\n");
            }
        }
        String[] dirs = {"/system/framework", "/system/lib", "/system/lib64", "/system/priv-app", "/system/app"};
        for (String d : dirs) {
            try {
                String[] fl = new File(d).list();
                if (fl == null) continue;
                for (String f : fl) {
                    String l = f.toLowerCase();
                    if (l.contains("tw") && !l.contains("twitter")) sb.append(d).append("/").append(f).append("\n");
                }
            } catch (Throwable ignore) {}
        }
        PackageManager pm = getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (ai.packageName.startsWith("com.tw.") && !ai.packageName.contains(".theme.")) sb.append("pkg ").append(ai.packageName).append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    private String dumpClasses(String pkg) {
        StringBuilder screen = new StringBuilder();
        StringBuilder full = new StringBuilder();
        int n = 0, shown = 0;
        try {
            Context c = createPackageContext(pkg, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            ClassLoader cl = c.getClassLoader();
            String apk = getPackageManager().getApplicationInfo(pkg, 0).sourceDir;
            dalvik.system.DexFile df = new dalvik.system.DexFile(apk);
            Pattern p = Pattern.compile("(?i)next|prev|play|pause|music|a2dp|avrcp|track|song|media|cmd");
            Enumeration<String> en = df.entries();
            while (en.hasMoreElements()) {
                String cn = en.nextElement();
                if (!cn.startsWith("com.tw.")) continue;
                n++;
                String sn = cn.substring(cn.lastIndexOf('.') + 1);
                full.append(cn).append("\n");
                try {
                    Class<?> k = Class.forName(cn, false, cl);
                    for (Field f : k.getDeclaredFields()) {
                        String line;
                        if (Modifier.isStatic(f.getModifiers()) && (f.getType() == int.class || f.getType() == String.class)) {
                            f.setAccessible(true);
                            line = "F " + f.getName() + "=" + f.get(null);
                        } else {
                            line = "f " + f.getName();
                        }
                        full.append("  ").append(line).append("\n");
                        if (shown < 90 && p.matcher(f.getName()).find()) {
                            screen.append(sn).append(": ").append(line).append("\n");
                            shown++;
                        }
                    }
                    for (Method m : k.getDeclaredMethods()) {
                        full.append("  m ").append(m.getName()).append("\n");
                        if (shown < 90 && p.matcher(m.getName()).find()) {
                            screen.append(sn).append(": m ").append(m.getName()).append("\n");
                            shown++;
                        }
                    }
                } catch (Throwable t) {
                    full.append("  ERR ").append(t.getClass().getSimpleName()).append("\n");
                }
            }
        } catch (Throwable e) {
            return "Ошибка сканирования: " + e + "\n";
        }
        String path = "не удалось сохранить";
        try {
            File out = new File(getExternalFilesDir(null), "twbt_dump.txt");
            FileWriter w = new FileWriter(out);
            w.write(full.toString());
            w.close();
            path = out.getAbsolutePath();
        } catch (Throwable ignore) {}
        return "Классов com.tw.: " + n + "\nПолный файл: " + path + "\n" + screen;
    }

    private boolean hasAccess() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private boolean isPlaying(MediaController c) {
        PlaybackState ps = c.getPlaybackState();
        return ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
    }

    private void findApps() {
        StringBuilder sb = new StringBuilder();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            String name = String.valueOf(pm.getApplicationLabel(ai));
            String all = (ai.packageName + " " + name).toLowerCase();
            if (all.contains("music") || all.contains("bluetooth") || all.contains("bt")
                    || all.contains("a2dp") || all.contains("player") || all.contains("media")) {
                sb.append(name).append("  [").append(ai.packageName).append("]\n");
            }
        }
        appsList = "\nПриложения музыки/BT:\n" + sb;
        refresh();
    }

    private void refresh() {
        StringBuilder log = new StringBuilder();
        boolean access = hasAccess();
        log.append("Доступ к уведомлениям: ").append(access ? "ДА" : "НЕТ (нажмите кнопку 1 и включите «Проверка музыки»)").append("\n");
        MediaController best = null;
        if (access) {
            try {
                MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
                List<MediaController> list = msm.getActiveSessions(new ComponentName(this, NotificationService.class));
                log.append("Активных медиасессий: ").append(list.size()).append("\n");
                sessions.clear();
                sessions.addAll(list);
                if (sel >= sessions.size()) sel = -1;
                int bestScore = -1;
                for (int i = 0; i < list.size(); i++) {
                    MediaController c = list.get(i);
                    PlaybackState ps = c.getPlaybackState();
                    MediaMetadata m = c.getMetadata();
                    boolean hasTitle = m != null && (m.getString(MediaMetadata.METADATA_KEY_TITLE) != null
                            || m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) != null);
                    long act = ps == null ? 0 : ps.getActions();
                    log.append(" #").append(i).append(" ").append(c.getPackageName())
                       .append(" сост=").append(ps == null ? "нет" : String.valueOf(ps.getState()))
                       .append(" назв=").append(hasTitle ? "да" : "нет")
                       .append(" next=").append((act & PlaybackState.ACTION_SKIP_TO_NEXT) != 0 ? "да" : "нет")
                       .append(" prev=").append((act & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0 ? "да" : "нет")
                       .append(" pp=").append((act & (PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE)) != 0 ? "да" : "нет")
                       .append("\n");
                    int score = (isPlaying(c) ? 4 : 0) + (hasTitle ? 2 : 0)
                            + ((act & PlaybackState.ACTION_SKIP_TO_NEXT) != 0 ? 1 : 0);
                    if (score > bestScore) { bestScore = score; best = c; }
                }
                if (sel >= 0 && sel < sessions.size()) best = sessions.get(sel);
                btnSel.setText(sel < 0 ? "Сессия: авто" : "Сессия: #" + sel);
                if (list.isEmpty()) {
                    log.append("Сессий нет: плеер магнитолы не отдаёт управление системе. Запустите музыку по Bluetooth и подождите.\n");
                }
            } catch (SecurityException e) {
                log.append("Нет доступа: ").append(e.getMessage()).append("\n");
            }
        }
        current = best;
        if (best != null) {
            MediaMetadata md = best.getMetadata();
            String title = null, artist = null;
            Bitmap art = null;
            long dur = 0;
            if (md != null) {
                title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                if (title == null) title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null) artist = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            }
            tvTitle.setText(title == null ? "(название не передано)" : title);
            tvArtist.setText(artist == null ? "(исполнитель не передан)" : artist);
            cover.setImageBitmap(art);
            PlaybackState ps = best.getPlaybackState();
            long pos = 0;
            String lp = "";
            String curTitle = title == null ? "" : title;
            if (!curTitle.equals(lastTitle)) {
                // трек сменился: позицию от плеера не верим, пока он не обновит состояние
                updAtChange = (!lastTitle.isEmpty() && ps != null) ? ps.getLastPositionUpdateTime() : -1;
                lastTitle = curTitle;
                ownPos = 0;
            }
            boolean reliable = false;
            if (ps != null) {
                long raw = ps.getPosition();
                long upd = ps.getLastPositionUpdateTime();
                long now = SystemClock.elapsedRealtime();
                lp = " поз=" + raw + " upd=" + upd + " v=" + ps.getPlaybackSpeed();
                if (raw >= 0 && upd > 0 && upd <= now && upd != updAtChange && !(dur > 0 && raw >= dur - 1500 && ps.getState() == PlaybackState.STATE_PLAYING)) {
                    pos = raw;
                    if (ps.getState() == PlaybackState.STATE_PLAYING) {
                        float sp = ps.getPlaybackSpeed();
                        pos += (long) ((now - upd) * (sp == 0 ? 1f : sp));
                    }
                    reliable = true;
                }
            }
            if (!reliable) {
                // плеер не отдаёт позицию -> считаем сами, пока играет
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) ownPos += 1000;
                pos = ownPos;
            } else {
                ownPos = pos;
            }
            if (dur > 0) bar.setProgress((int) Math.max(0, Math.min(1000, pos * 1000 / dur)));
            else bar.setProgress(0);
            lp += reliable ? " (от плеера)" : " (свой счётчик)";
            long ps_s = pos / 1000;
            lp += "  время=" + (ps_s / 60) + ":" + String.format("%02d", ps_s % 60);
            tvInfo.setText("Источник: " + best.getPackageName()
                    + "   обложка: " + (art != null ? "есть" : "нет")
                    + "   длительность: " + (dur > 0 ? (dur / 1000) + " с" : "нет") + lp);
        } else {
            tvTitle.setText("Нет данных");
            tvArtist.setText("");
            tvInfo.setText("");
            cover.setImageBitmap(null);
            bar.setProgress(0);
        }
        tvLog.setText(log.toString() + appsList);
    }
}
