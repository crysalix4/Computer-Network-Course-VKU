package com.jbdc;

import com.google.gson.Gson;
import com.jbdc.db.Student;
import com.jbdc.db.StudentRepo;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TcpClientHandler implements Runnable {
  private final Socket socket;
  private final StudentRepo repo;
  private static final Gson GSON = new Gson();

  private BufferedReader in;
  private BufferedWriter out;
  private String remote;

  public TcpClientHandler(Socket socket, StudentRepo repo) {
    this.socket = socket;
    this.repo = repo;
  }
  @Override
  public void run() {
    remote = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    System.out.println("Accepted: " + remote);
    try {
      in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                                                    StandardCharsets.UTF_8));
      out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                                                      StandardCharsets.UTF_8));

      ClientBus.add(this);
      sendAsync("OK TCP-MySQL Server. Type HELP");
      int onlineNow = ClientBus.online();
      sendAsync(eventJson("join", Map.of("peer", remote, "online", onlineNow)));
      ClientBus.broadcastExcept(
          this, eventJson("join", Map.of("peer", remote, "online", onlineNow)));

      String line;
      while ((line = in.readLine()) != null) {
        String cmd = line.trim();
        String resp = handle(cmd);
        sendAsync(resp);
      }
    } catch (Exception e) {
      System.err.println("Client " + remote + " error: " + e.getMessage());
    } finally {
      closeQuiet();
      ClientBus.remove(this);
      ClientBus.broadcastExcept(
          this, eventJson("leave", Map.of("peer", remote, "online",
                                          ClientBus.online())));
      System.out.println("Closed: " + remote);
    }
  }
  public void sendAsync(String line) {
    synchronized (this) {
      try {
        out.write(line);
        out.write("\n");
        out.flush();
        System.out.println("-> " + remote + ": " + line);
      } catch (IOException e) {
        closeQuiet();
        ClientBus.remove(this);
      }
    }
  }

  private void closeQuiet() {
    try {
      if (in != null)
        in.close();
    } catch (IOException ignored) {
    }
    try {
      if (out != null)
        out.close();
    } catch (IOException ignored) {
    }
    try {
      socket.close();
    } catch (IOException ignored) {
    }
  }

  private static String eventJson(String type, Map<String, Object> extra) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    if (extra != null)
      m.putAll(extra);
    return "EVENT " + GSON.toJson(m);
  }

  private String handle(String cmd) {
    try {
      if (cmd.equalsIgnoreCase("PING"))
        return "PONG";

      if (cmd.equalsIgnoreCase("HELP")) {
        return String.join("\n", "Commands:", "PING", "GET_ALL", "GET <ma>",
                           "INSERT <ma>|<ten>|<sdt>", "DELETE <ma>");
      }
      if (cmd.equalsIgnoreCase("GET_ALL")) {
        long t0 = System.nanoTime();
        List<Student> all = repo.getAll();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        ClientBus.broadcastExcept(
            this, eventJson("get_all", Map.of("by", remote, "count", all.size(),
                                              "ms", ms)));
        return GSON.toJson(Map.of("ok", true, "data", all));
      }

      if (cmd.startsWith("GET ")) {
        String ma = cmd.substring(4).trim();
        long t0 = System.nanoTime();
        Student sv = repo.getByMa(ma);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        ClientBus.broadcastExcept(
            this, eventJson("get", Map.of("by", remote, "ma", ma, "hit",
                                          sv != null, "ms", ms)));
        return GSON.toJson(Map.of("ok", sv != null, "data", sv));
      }
      if (cmd.startsWith("INSERT ")) {
        String[] p = cmd.substring(7).split("\\|", 3);
        if (p.length != 3) {
          return GSON.toJson(
              Map.of("ok", false, "err", "Format: INSERT ma|ten|sdt"));
        }
        Student s = new Student(p[0].trim(), p[1].trim(), p[2].trim());
        boolean ok = repo.insert(s);
        if (ok) {
          sendAsync(eventJson("insert", Map.of("row", s, "by", remote)));
          ClientBus.broadcastExcept(
              this, eventJson("insert", Map.of("row", s, "by", remote)));
        }
        return GSON.toJson(Map.of("ok", ok));
      }

      if (cmd.startsWith("DELETE ")) {
        String ma = cmd.substring(7).trim();
        boolean ok = repo.deleteByMa(ma);
        if (ok) {
          sendAsync(eventJson("delete", Map.of("ma", ma, "by", remote)));
          ClientBus.broadcastExcept(
              this, eventJson("delete", Map.of("ma", ma, "by", remote)));
        }
        return GSON.toJson(Map.of("ok", ok));
      }

      return GSON.toJson(
          Map.of("ok", false, "err", "Unknown command. Type HELP"));
    } catch (Exception e) {
      return GSON.toJson(Map.of("ok", false, "err", e.getMessage()));
    }
  }
}
