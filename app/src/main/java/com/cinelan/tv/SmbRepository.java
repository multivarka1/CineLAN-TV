package com.cinelan.tv;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.share.Directory;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** SMB2/SMB3 browser. Never persists the password to disk. */
final class SmbRepository {
    static final String HOST = "10.0.11.1";
    private String host = HOST;
    private String username;
    private String password;
    private String domain;

    void setCredentials(String host, String user, String pass, String domain) {
        this.host = host;
        this.username = user;
        this.password = pass;
        this.domain = domain;
    }

    boolean hasCredentials() { return username != null && !username.isEmpty(); }

    List<Entry> list(String shareName, String relativePath) throws Exception {
        if (!hasCredentials()) throw new IllegalStateException("Введите логин и пароль");
        try (SMBClient client = new SMBClient(); Connection connection = client.connect(host)) {
            AuthenticationContext auth = new AuthenticationContext(username, password.toCharArray(), domain);
            try (Session session = connection.authenticate(auth);
                 DiskShare share = (DiskShare) session.connectShare(shareName)) {
                List<Entry> out = new ArrayList<>();
                for (FileIdBothDirectoryInformation info : share.list(relativePath)) {
                    String name = info.getFileName();
                    if (".".equals(name) || "..".equals(name)) continue;
                    boolean folder = EnumWithValue.EnumUtils.isSet(info.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
                    if (!folder && !isVideo(name)) continue;
                    String path = relativePath.isEmpty() ? name : relativePath + "\\" + name;
                    out.add(new Entry(shareName, path, name, folder));
                }
                out.sort(Comparator.comparing((Entry e) -> !e.directory)
                        .thenComparing(e -> e.name.toLowerCase(Locale.ROOT)));
                return out;
            }
        }
    }

    void verify() throws Exception {
        if (!hasCredentials()) throw new IllegalStateException("Введите логин и пароль");
        try (SMBClient client = new SMBClient(); Connection connection = client.connect(host);
             Session session = connection.authenticate(new AuthenticationContext(username, password.toCharArray(), domain))) {
            for (String name : new String[]{"abc", "video"}) {
                try (DiskShare share = (DiskShare) session.connectShare(name);
                     Directory directory = share.openDirectory("", EnumSet.of(AccessMask.FILE_LIST_DIRECTORY),
                             EnumSet.noneOf(FileAttributes.class), SMB2ShareAccess.ALL,
                             SMB2CreateDisposition.FILE_OPEN, EnumSet.noneOf(SMB2CreateOptions.class))) {
                    // Opening each share's root checks access without enumerating it.
                }
            }
        }
    }

    static boolean isVideo(String fileName) {
        String s = fileName.toLowerCase(Locale.ROOT);
        return s.endsWith(".mkv") || s.endsWith(".mp4") || s.endsWith(".avi") ||
                s.endsWith(".mov") || s.endsWith(".ts") || s.endsWith(".m2ts") ||
                s.endsWith(".wmv") || s.endsWith(".webm") || s.endsWith(".mpg") ||
                s.endsWith(".mpeg") || s.endsWith(".m4v");
    }

    MediaIndex startIndex() {
        MediaIndex index = new MediaIndex();
        index.start(host, username, password, domain);
        return index;
    }

    SmbStream openStream(Entry entry) throws Exception {
        return new SmbStream(host, username, password, domain, entry);
    }

    static final class Entry {
        final String share;
        final String path;
        final String name;
        final boolean directory;
        Entry(String share, String path, String name, boolean directory) {
            this.share = share; this.path = path; this.name = name; this.directory = directory;
        }
    }
}
