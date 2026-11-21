package com.cinelan.tv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.ArrayList;

/** Playback uses a local stream backed by SMBJ. */
public final class PlayerActivity extends Activity {
    private LibVLC libVlc;
    private SmbStream stream;
    private MediaPlayer player;
    private VLCVideoLayout surface;
    private TextView hint;
    private boolean paused;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (!AppSession.SMB.hasCredentials()) {
            Toast.makeText(this, "Сначала войдите в SMB", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        FrameLayout root = new FrameLayout(this);
        surface = new VLCVideoLayout(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        hint = new TextView(this);
        hint.setTextColor(Color.WHITE);
        hint.setBackgroundColor(0x99000000);
        hint.setTextSize(16);
        hint.setPadding(24, 14, 24, 14);
        hint.setText("OK: пауза / воспроизведение   •   ←/→: ±10 сек   •   ↑: дорожки   •   Назад: закрыть");
        FrameLayout.LayoutParams h = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(hint, h);
        setContentView(root);
        try {
            ArrayList<String> options = new ArrayList<>();
            options.add("--network-caching=1500");
            options.add("--no-stats");
            libVlc = new LibVLC(this, options);
            player = new MediaPlayer(libVlc);
            player.attachViews(surface, null, false, false);
            String share = getIntent().getStringExtra("share");
            String path = getIntent().getStringExtra("path");
            String name = getIntent().getStringExtra("title");
            if (share == null || path == null) throw new IllegalArgumentException("No SMB path");
            SmbRepository.Entry entry = new SmbRepository.Entry(share, path, name, false);
            stream = AppSession.SMB.openStream(entry);
            Media media = new Media(libVlc, android.net.Uri.parse(stream.url()));
            media.setHWDecoderEnabled(true, false);
            player.setMedia(media);
            media.release();
            player.setEventListener(e -> {
                if (e.type == MediaPlayer.Event.EncounteredError) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Не удалось открыть SMB-видео. Проверь доступ и формат.", Toast.LENGTH_LONG).show();
                        hint.setVisibility(View.VISIBLE);
                    });
                } else if (e.type == MediaPlayer.Event.Playing) {
                    runOnUiThread(() -> hint.setVisibility(View.GONE));
                }
            });
            player.play();
        } catch (Exception ex) {
            Toast.makeText(this, "Ошибка плеера: " + ex.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (player == null) return super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (player.isPlaying()) { player.pause(); paused = true; }
            else { player.play(); paused = false; }
            hint.setVisibility(View.VISIBLE);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            showTracks();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            long length = player.getLength();
            long next = Math.max(0, player.getTime() + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -10000 : 10000));
            if (length > 0) next = Math.min(length - 1, next);
            player.setTime(next);
            hint.setVisibility(View.VISIBLE);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    private void showTracks() {
        new android.app.AlertDialog.Builder(this)
                .setItems(new String[]{"Звуковая дорожка", "Субтитры"}, (dialog, which) -> {
                    MediaPlayer.TrackDescription[] tracks = which == 0 ? player.getAudioTracks() : player.getSpuTracks();
                    if (tracks == null || tracks.length == 0) {
                        Toast.makeText(this, "Дорожки недоступны", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] names = new String[tracks.length];
                    for (int i = 0; i < tracks.length; i++) names[i] = tracks[i].name;
                    new android.app.AlertDialog.Builder(this).setTitle(which == 0 ? "Звуковая дорожка" : "Субтитры")
                            .setItems(names, (d, index) -> {
                                if (which == 0) player.setAudioTrack(tracks[index].id);
                                else player.setSpuTrack(tracks[index].id);
                            }).show();
                }).show();
    }

    @Override protected void onStop() {
        if (player != null) player.stop();
        super.onStop();
    }
    @Override protected void onDestroy() {
        if (player != null) {
            player.setEventListener(null);
            player.detachViews();
            player.release();
            player = null;
        }
        if (libVlc != null) { libVlc.release(); libVlc = null; }
        if (stream != null) { stream.close(); stream = null; }
        super.onDestroy();
    }
}
