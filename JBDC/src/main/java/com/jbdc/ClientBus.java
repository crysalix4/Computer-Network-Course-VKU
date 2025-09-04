package com.jbdc;

import java.util.concurrent.CopyOnWriteArrayList;

public class ClientBus {
  private static final CopyOnWriteArrayList<TcpClientHandler> clients =
      new CopyOnWriteArrayList<>();

  static void add(TcpClientHandler h) { clients.addIfAbsent(h); }
  static void remove(TcpClientHandler h) { clients.remove(h); }
  public static int online() { return clients.size(); }

  public static void broadcast(String line) {
    for (TcpClientHandler c : clients)
      c.sendAsync(line);
  }
  public static void broadcastExcept(TcpClientHandler except, String line) {
    for (TcpClientHandler c : clients)
      if (c != except)
        c.sendAsync(line);
  }
}
