package com.cinelan.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.os.Handler;
import android.os.Looper;
import android.widget.BaseAdapter;
import android.widget.ListView;
import java.util.ArrayList;
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
    private MediaIndex index;
    private boolean searching;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private int searchId;
    private Runnable searchRefresh;
    private Runnable pendingSearch;
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
        TextView description = label("Локальный кинотеатр · SMB", 14, false);
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
        EditText host = field("IP-адрес сервера", false);
        host.setText(SmbRepository.HOST);
        EditText user = field("Имя пользователя SMB", false);
        EditText pass = field("Пароль", true);
        EditText domain = field("Домен (необязательно)", false);
        Button connect = action("Подключиться", () -> {
            String u = user.getText().toString().trim();
            String p = pass.getText().toString();
            String d = domain.getText().toString().trim();
            if (u.isEmpty()) { status.setText("Укажи имя пользователя"); return; }
            String address = host.getText().toString().trim();
            if (address.isEmpty()) { status.setText("Укажи адрес сервера"); return; }
            connectWith(address, u, p, d);
        });
        connect.requestFocus();
    }
    private void connectWith(String host, String u, String p, String d) {
        status.setText("Проверяю SMB-доступ к abc и video…");
        int id = ++requestId;
        io.execute(() -> {
            try {
                AppSession.SMB.setCredentials(host, u, p, d);
                AppSession.SMB.verify();
                runOnUiThread(() -> { if (id == requestId) {
                    if (index != null) index.close();
                    index = AppSession.SMB.startIndex();
                    showRoot();
                } });
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
        action("🔎  Поиск по медиатеке", this::showSearch);
        action("Обновить индекс медиатеки", () -> {
            if (index != null) index.close();
            index = AppSession.SMB.startIndex();
            status.setText("Индексация запущена в фоне");
        });
        action("Сменить учётную запись", () -> {
            if (index != null) { index.close(); index = null; }
            showLogin();
        });
    }
    private Button listButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setTextSize(17);
        button.setTextColor(Color.WHITE);
        button.setBackground(background(CARD, 12));
        button.setPadding(dp(18), dp(10), dp(18), dp(10));
        button.setOnFocusChangeListener((v, focused) ->
                v.setBackground(background(focused ? 0xFF176B91 : CARD, 12)));
        button.setText(text);
        return button;
    }

    private final class EntryAdapter extends BaseAdapter {
        private List<SmbRepository.Entry> items = new ArrayList<>();
        private final boolean showPath;

        EntryAdapter(boolean showPath) { this.showPath = showPath; }
        void setItems(List<SmbRepository.Entry> entries) { items = entries; notifyDataSetChanged(); }
        @Override public int getCount() { return items.size(); }
        @Override public SmbRepository.Entry getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            TextView row = recycled instanceof TextView ? (TextView) recycled : new TextView(MainActivity.this);
            SmbRepository.Entry item = getItem(position);
            row.setText((item.directory ? "📁  " : "▶  ") + item.name
                    + (showPath ? "\n" + item.share + " / " + item.path.replace('\\', '/') : ""));
            row.setTextColor(Color.WHITE);
            row.setTextSize(17);
            row.setPadding(dp(18), dp(12), dp(18), dp(12));
            row.setMinHeight(dp(56));
            return row;
        }
    }

    private LinearLayout listScreen(String title) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(18), dp(40), dp(18));
        root.setBackgroundColor(BG);
        root.addView(label(title, 23, true));
        status = label("", 14, false);
        status.setTextColor(ACCENT);
        root.addView(status);
        setContentView(root);
        return root;
    }

    private ListView addList(LinearLayout root, EntryAdapter adapter) {
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setSelector(android.R.drawable.list_selector_background);
        list.setOnItemClickListener((parent, view, position, id) -> {
            SmbRepository.Entry item = adapter.getItem(position);
            if (item.directory) {
                stopSearchRefresh();
                searching = false;
                openFolder(item.share, item.path);
            } else {
                play(item);
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        return list;
    }

    private void openFolder(String newShare, String newPath) {
        searching = false;
        stopSearchRefresh();
        share = newShare; path = newPath;
        LinearLayout root = listScreen("Обзор: " + share + (path.isEmpty() ? "" : " / " + path.replace('\\', '/')));
        status.setText("Загружаю содержимое…");
        Button back = listButton("← Назад");
        back.setOnClickListener(v -> goBack());
        root.addView(back);
        EntryAdapter adapter = new EntryAdapter(false);
        addList(root, adapter);
        int id = ++requestId;
        io.execute(() -> {
            try {
                List<SmbRepository.Entry> items = AppSession.SMB.list(newShare, newPath);
                runOnUiThread(() -> {
                    if (id != requestId) return;
                    status.setText(items.isEmpty() ? "В этой папке нет видео или подпапок" : "Найдено элементов: " + items.size());
                    adapter.setItems(items);
                });
            } catch (Exception ex) {
                String error = friendlyError(ex);
                runOnUiThread(() -> { if (id == requestId) status.setText("Не удалось прочитать папку: " + error); });
            }
        });
    }

    private void showSearch() {
        searching = true;
        ++requestId;
        LinearLayout root = listScreen("Поиск по медиатеке");
        EditText query = new EditText(this);
        query.setSingleLine();
        query.setTextColor(Color.WHITE);
        query.setHintTextColor(MUTED);
        query.setHint("Название фильма или папки");
        root.addView(query);
        Button back = listButton("← Назад");
        back.setOnClickListener(v -> goBack());
        root.addView(back);
        EntryAdapter adapter = new EntryAdapter(true);
        addList(root, adapter);
        Runnable update = () -> updateSearch(query.getText().toString(), adapter);
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                ++searchId;
                pendingSearch = update;
                handler.postDelayed(pendingSearch, 250);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        searchRefresh = new Runnable() {
            @Override public void run() {
                if (!searching) return;
                update.run();
                if (index != null && !index.isComplete()) handler.postDelayed(this, 1500);
            }
        };
        searchRefresh.run();
        query.requestFocus();
    }

    private void updateSearch(String query, EntryAdapter adapter) {
        MediaIndex current = index;
        int id = ++searchId;
        if (current == null) return;
        searchExecutor.execute(() -> {
            List<SmbRepository.Entry> results = current.search(query);
            int count = current.size();
            boolean complete = current.isComplete();
            int errors = current.errors();
            runOnUiThread(() -> {
                if (!searching || id != searchId || index != current) return;
                adapter.setItems(results);
                String progress = complete ? "Индексация завершена" : "Индексация…";
                status.setText(progress + " · записей: " + count + " · найдено: " + results.size()
                        + (errors > 0 ? " · недоступных папок: " + errors : ""));
            });
        });
    }

    private void stopSearchRefresh() {
        ++searchId;
        if (searchRefresh != null) handler.removeCallbacks(searchRefresh);
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        searchRefresh = null;
        pendingSearch = null;
    }

    private void play(SmbRepository.Entry entry) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("share", entry.share);
        intent.putExtra("path", entry.path);
        intent.putExtra("title", entry.name);
        startActivity(intent);
    }
    private String friendlyError(Exception ex) {
        String msg = String.valueOf(ex.getMessage()).toUpperCase(java.util.Locale.ROOT);
        if (msg.contains("LOGON_FAILURE") || msg.contains("WRONG_PASSWORD") || msg.contains("ACCESS_DENIED"))
            return "Ошибка авторизации или доступ запрещён. Проверьте логин, пароль и права.";
        if (msg.contains("BAD_NETWORK_NAME") || msg.contains("OBJECT_NAME_NOT_FOUND"))
            return "SMB-ресурс не найден. Проверьте имена общих ресурсов abc и video.";
        if (ex instanceof java.io.IOException)
            return "Сервер недоступен. Проверьте адрес, сеть и порт 445.";
        return "Не удалось подключиться к SMB. Проверьте настройки и доступ к ресурсам.";
    }
    private void goBack() {
        ++requestId;
        if (searching) {
            searching = false;
            stopSearchRefresh();
            showRoot();
            return;
        }
        if (share == null) { showLogin(); return; }
        if (path.isEmpty()) { showRoot(); return; }
        int index = path.lastIndexOf('\\');
        openFolder(share, index < 0 ? "" : path.substring(0, index));
    }
    @Override public void onBackPressed() { goBack(); }
    @Override protected void onDestroy() { ++requestId; stopSearchRefresh(); if (index != null) index.close(); searchExecutor.shutdownNow(); io.shutdownNow(); super.onDestroy(); }
}
