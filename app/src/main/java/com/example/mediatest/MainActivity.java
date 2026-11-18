package com.example.mediatest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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

import java.util.List;

public class MainActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView cover;
    private TextView tvTitle, tvArtist, tvInfo, tvLog;
    private ProgressBar bar;
    private MediaController current;
    private String appsList = "";

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
            public void onClick(View v) { if (current != null) current.getTransportControls().skipToPrevious(); }
        }));
        ctr.addView(btn("Пуск / Пауза", new View.OnClickListener() {
            public void onClick(View v) {
                if (current == null) return;
                PlaybackState ps = current.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) current.getTransportControls().pause();
                else current.getTransportControls().play();
            }
        }));
        ctr.addView(btn("Вперёд  >|", new View.OnClickListener() {
            public void onClick(View v) { if (current != null) current.getTransportControls().skipToNext(); }
        }));

        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        keys.addView(btn("Клавиша: назад", new View.OnClickListener() {
            public void onClick(View v) { sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS); }
        }));
        keys.addView(btn("Клавиша: пуск/пауза", new View.OnClickListener() {
            public void onClick(View v) { sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); }
        }));
        keys.addView(btn("Клавиша: вперёд", new View.OnClickListener() {
            public void onClick(View v) { sendKey(KeyEvent.KEYCODE_MEDIA_NEXT); }
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
        root.addView(row);

        ScrollView sv = new ScrollView(this);
        tvLog = text(13, Color.WHITE);
        sv.addView(tvLog);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void sendKey(int code) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
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
                for (MediaController c : list) {
                    PlaybackState ps = c.getPlaybackState();
                    log.append(" - ").append(c.getPackageName())
                       .append("  состояние=").append(ps == null ? "нет" : String.valueOf(ps.getState())).append("\n");
                    if (best == null) best = c;
                    if (isPlaying(c) && !isPlaying(best)) best = c;
                }
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
            if (ps != null) {
                pos = ps.getPosition();
                if (ps.getState() == PlaybackState.STATE_PLAYING) {
                    pos += (long) ((SystemClock.elapsedRealtime() - ps.getLastPositionUpdateTime()) * ps.getPlaybackSpeed());
                }
            }
            if (dur > 0) bar.setProgress((int) Math.min(1000, pos * 1000 / dur));
            else bar.setProgress(0);
            tvInfo.setText("Источник: " + best.getPackageName()
                    + "   обложка: " + (art != null ? "есть" : "нет")
                    + "   длительность: " + (dur > 0 ? (dur / 1000) + " с" : "нет"));
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
