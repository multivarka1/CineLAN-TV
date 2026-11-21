package com.cinelan.tv;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Builds an in-memory index without retaining credentials or touching the UI thread. */
final class MediaIndex {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "media-index");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final List<SmbRepository.Entry> entries = new ArrayList<>();
    private volatile boolean cancelled;
    private volatile boolean complete;
    private volatile int errors;

    void start(String host, String user, String password, String domain) {
        executor.execute(() -> {
            try (SMBClient client = new SMBClient(); Connection connection = client.connect(host);
                 Session session = connection.authenticate(new AuthenticationContext(user, password.toCharArray(), domain))) {
                for (String shareName : new String[]{"abc", "video"}) {
                    if (cancelled) break;
                    try (DiskShare share = (DiskShare) session.connectShare(shareName)) {
                        ArrayDeque<String> pending = new ArrayDeque<>();
                        pending.add("");
                        while (!pending.isEmpty() && !cancelled) {
                            String folder = pending.removeFirst();
                            try {
                                List<SmbRepository.Entry> batch = new ArrayList<>();
                                for (FileIdBothDirectoryInformation info : share.list(folder)) {
                                    if (cancelled) break;
                                    String name = info.getFileName();
                                    if (".".equals(name) || "..".equals(name)) continue;
                                    boolean directory = EnumWithValue.EnumUtils.isSet(info.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
                                    boolean link = EnumWithValue.EnumUtils.isSet(info.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT);
                                    String path = folder.isEmpty() ? name : folder + "\\" + name;
                                    if (directory && !link) pending.addLast(path);
                                    if ((directory && !link) || (!directory && SmbRepository.isVideo(name)))
                                        batch.add(new SmbRepository.Entry(shareName, path, name, directory));
                                }
                                synchronized (entries) { entries.addAll(batch); }
                            } catch (Exception e) {
                                errors++;
                            }
                        }
                    } catch (Exception e) {
                        errors++;
                    }
                }
            } catch (Exception e) {
                errors++;
            } finally {
                complete = true;
            }
        });
    }

    List<SmbRepository.Entry> search(String query) {
        String term = query.trim().toLowerCase(Locale.ROOT);
        List<SmbRepository.Entry> matches = new ArrayList<>();
        if (term.isEmpty()) return matches;
        synchronized (entries) {
            for (SmbRepository.Entry entry : entries) {
                if (entry.name.toLowerCase(Locale.ROOT).contains(term)) matches.add(entry);
            }
        }
        return matches;
    }

    int size() { synchronized (entries) { return entries.size(); } }
    boolean isComplete() { return complete; }
    int errors() { return errors; }

    void close() {
        cancelled = true;
        executor.shutdownNow();
    }
}
