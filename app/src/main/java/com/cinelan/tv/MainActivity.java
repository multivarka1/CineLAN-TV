package com.cinelan.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** D-pad friendly functional MVP: SMB sign-in -> folders -> LibVLC. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(11, 18, 32);
    private static final int CARD = Color.rgb(27, 39, 58);
    private static final int MUTED = Color.rgb(164, 184, 204);
    private static final int ACCENT = Color.rgb(100, 210, 255);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout body;
    private TextView status;
    private String share = null;
    private String path = "";
    private int requestId = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(BG);
        showLogin();
    }

    private int dp(int value) { return (int)(getResources().getDisplayMetrics().density * value + .5f); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView label(String txt, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(txt); t.setTextColor(Color.WHITE); t.setTextSize(sp);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setPadding(0, dp(3), 0, dp(6));
        return t;
    }
    private LinearLayout panel() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(22), dp(40), dp(24));
        root.setBackgroundColor(BG);
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true); sc.addView(root);
        setContentView(sc);
        body = root;
        body.addView(label("CINELAN  /  TV", 28, true));
        TextView description = label("Локальный кинотеатр · SMB 10.0.11.1", 14, false);
        description.setTextColor(MUTED); body.addView(description);
        status = label("", 14, false); status.setTextColor(ACCENT);
        LinearLayout.LayoutParams s = new LinearLayout.LayoutParams(-1, -2);
        s.topMargin = dp(6); body.addView(status, s);
        return root;
    }
    private Button action(String text, Runnable handler) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text); b.setTextSize(17);
        b.setTextColor(Color.WHITE);
        b.setBackground(background(CARD, 12));
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(background(hasFocus ? 0xFF176B91 : CARD, 12));
            v.setScaleX(hasFocus ? 1.015f : 1f);
            v.setScaleY(hasFocus ? 1.015f : 1f);
        });
        b.setOnClickListener(v -> handler.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.topMargin = dp(8);
        body.addView(b, lp);
        return b;
    }
    private EditText field(String hint, boolean password) {
        EditText edit = new EditText(this);
        edit.setSingleLine(); edit.setTextColor(Color.WHITE); edit.setHintTextColor(MUTED);
        edit.setHint(hint); edit.setTextSize(18); edit.setPadding(dp(15), dp(8), dp(15), dp(8));
        edit.setBackground(background(CARD, 12));
        if (password) edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(10); body.addView(edit, lp);
        return edit;
    }
    private void showLogin() {
        panel();
        body.addView(label("Подключение к сетевому хранилищу", 23, true));
        TextView expl = label("Папки abc и video считаются отдельными SMB-ресурсами. Учётные данные остаются только в памяти приложения.", 15, false);
        expl.setTextColor(MUTED); body.addView(expl);
        EditText user = field("Имя пользователя SMB", false);
        EditText pass = field("Пароль", true);
        EditText domain = field("Домен (необязательно)", false);
        Button connect = action("Подключиться к 10.0.11.1", () -> {
            String u = user.getText().toString().trim();
            String p = pass.getText().toString();
            String d = domain.getText().toString().trim();
            if (u.isEmpty()) { status.setText("Укажи имя пользователя"); return; }
            connectWith(u, p, d);
        });
        connect.requestFocus();
    }
    private void connectWith(String u, String p, String d) {
        status.setText("Проверяю SMB-доступ к abc и video…");
        int id = ++requestId;
        io.execute(() -> {
            try {
                AppSession.SMB.setCredentials(u, p, d);
                AppSession.SMB.verify();
                runOnUiThread(() -> { if (id == requestId) showRoot(); });
            } catch (Exception ex) {
                String message = friendlyError(ex);
                runOnUiThread(() -> { if (id == requestId) status.setText("Ошибка подключения: " + message); });
            }
        });
    }
    private void showRoot() {
        share = null; path = "";
        panel(); body.addView(label("Медиатека", 24, true));
        status.setText("Подключено · выбери сетевой ресурс");
        action("📁  abc — каталог по алфавиту", () -> openFolder("abc", ""));
        action("📁  video — общая видеотека", () -> openFolder("video", ""));
        action("Сменить учётную запись", this::showLogin);
    }
    private void openFolder(String newShare, String newPath) {
        share = newShare; path = newPath;
        panel(); body.addView(label("Обзор: " + share + (path.isEmpty() ? "" : " / " + path.replace('\\', '/')), 20, true));
        status.setText("Загружаю содержимое…");
        action("← Назад", this::goBack);
        int id = ++requestId;
        io.execute(() -> {
            try {
                List<SmbRepository.Entry> items = AppSession.SMB.list(newShare, newPath);
                runOnUiThread(() -> {
                    if (id != requestId) return;
                    status.setText("Найдено элементов: " + items.size());
                    if (items.isEmpty()) body.addView(label("В этой папке нет поддерживаемых видео или подпапок", 16, false));
                    for (SmbRepository.Entry item : items) {
                        action((item.directory ? "📁  " : "▶  ") + item.name, () -> {
                            if (item.directory) openFolder(item.share, item.path);
                            else play(item);
                        });
                    }
                });
            } catch (Exception ex) {
                String error = friendlyError(ex);
                runOnUiThread(() -> { if (id == requestId) status.setText("Не удалось прочитать папку: " + error); });
            }
        });
    }
    private void play(SmbRepository.Entry entry) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("share", entry.share);
        intent.putExtra("path", entry.path);
        intent.putExtra("title", entry.name);
        startActivity(intent);
    }
    private String friendlyError(Exception ex) {
        String msg = ex.getMessage();
        return msg == null || msg.isEmpty() ? ex.getClass().getSimpleName() : msg;
    }
    private void goBack() {
        ++requestId;
        if (share == null) { showLogin(); return; }
        if (path.isEmpty()) { showRoot(); return; }
        int index = path.lastIndexOf('\\');
        openFolder(share, index < 0 ? "" : path.substring(0, index));
    }
    @Override public void onBackPressed() { goBack(); }
    @Override protected void onDestroy() { ++requestId; io.shutdownNow(); super.onDestroy(); }
}
