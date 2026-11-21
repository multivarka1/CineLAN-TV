package com.cinelan.tv;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.UUID;

/** Serves one SMB file on loopback, with byte ranges for seeking. */
final class SmbStream implements AutoCloseable {
    private final ServerSocket server;
    private final String token = UUID.randomUUID().toString();
    private final Thread thread;
    private volatile boolean closed;
    private volatile Socket active;
    private final String host, user, password, domain;
    private final SmbRepository.Entry entry;

    SmbStream(String host, String user, String password, String domain, SmbRepository.Entry entry)
            throws Exception {
        this.host = host;
        this.user = user;
        this.password = password;
        this.domain = domain;
        this.entry = entry;
        server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
        thread = new Thread(this::serve, "smb-stream");
        thread.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getLocalPort() + "/" + token;
    }

    private void serve() {
        while (!closed) {
            try (Socket socket = server.accept()) {
                active = socket;
                socket.setSoTimeout(15000);
                handle(socket);
            } catch (Exception ignored) {
                // The player reports playback errors. Never log SMB credentials.
            } finally {
                active = null;
            }
        }
    }

    private void handle(Socket socket) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        String request = reader.readLine();
        if (request == null) return;
        String[] parts = request.split(" ");
        if (parts.length < 2 || !parts[1].equals("/" + token)
                || !(parts[0].equals("GET") || parts[0].equals("HEAD"))) return;
        String range = null;
        for (String line; (line = reader.readLine()) != null && !line.isEmpty();) {
            if (line.regionMatches(true, 0, "Range:", 0, 6)) range = line.substring(6).trim();
        }
        try (SMBClient client = new SMBClient(); Connection connection = client.connect(host);
             Session session = connection.authenticate(new AuthenticationContext(user, password.toCharArray(), domain));
             DiskShare share = (DiskShare) session.connectShare(entry.share);
             File file = share.openFile(entry.path, EnumSet.of(AccessMask.GENERIC_READ),
                     EnumSet.noneOf(FileAttributes.class), SMB2ShareAccess.ALL,
                     SMB2CreateDisposition.FILE_OPEN, EnumSet.noneOf(SMB2CreateOptions.class))) {
            long size = file.getFileInformation().getStandardInformation().getEndOfFile();
            long start = 0, end = size - 1;
            if (range != null) {
                if (!range.startsWith("bytes=") || range.contains(",")) return;
                String[] bounds = range.substring(6).split("-", -1);
                if (bounds.length != 2) return;
                if (bounds[0].isEmpty()) {
                    long suffix = Long.parseLong(bounds[1]);
                    if (suffix <= 0) return;
                    start = Math.max(0, size - suffix);
                } else {
                    start = Long.parseLong(bounds[0]);
                    if (!bounds[1].isEmpty()) end = Math.min(end, Long.parseLong(bounds[1]));
                }
                if (start < 0 || start >= size || end < start) {
                    socket.getOutputStream().write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + size + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    return;
                }
            }
            long length = end - start + 1;
            String headers = "HTTP/1.1 " + (range == null ? "200 OK" : "206 Partial Content")
                    + "\r\nContent-Type: application/octet-stream\r\nAccept-Ranges: bytes\r\nContent-Length: " + length
                    + (range == null ? "" : "\r\nContent-Range: bytes " + start + "-" + end + "/" + size)
                    + "\r\nConnection: close\r\n\r\n";
            OutputStream output = socket.getOutputStream();
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            if (parts[0].equals("HEAD")) return;
            byte[] buffer = new byte[65536];
            long offset = start;
            while (!closed && offset <= end) {
                int read = file.read(buffer, offset, 0, (int) Math.min(buffer.length, end - offset + 1));
                if (read <= 0) break;
                output.write(buffer, 0, read);
                offset += read;
            }
        }
    }

    @Override public void close() {
        closed = true;
        try { server.close(); } catch (Exception ignored) { }
        Socket socket = active;
        if (socket != null) try { socket.close(); } catch (Exception ignored) { }
    }
}
