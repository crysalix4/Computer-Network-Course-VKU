package com.jbdc;

import com.jbdc.db.StudentRepo;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("TCP_PORT", "9090"));
        StudentRepo repo = new StudentRepo();
        repo.ensureTable();
        repo.seedIfEmpty();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        String bindAddr = System.getenv().getOrDefault("BIND_ADDR", "0.0.0.0");
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(bindAddr, port));
            System.out.printf("TCP server listening on %s:%d ...%n", bindAddr, port);
            List<String> ips = localIPv4();
            if (ips.isEmpty()) {
                System.out.println("No non-loopback IPv4 found. Still reachable via 127.0.0.1 on this machine.");
            } else {
                System.out.println("Connect from LAN using:");
                for (String ip : ips) {
                    System.out.printf("  - %s:%d%n", ip, port);
                }
            }
            while (true) {
                Socket client = server.accept();
                String remote = client.getInetAddress().getHostAddress() + ":" + client.getPort();
                String local  = client.getLocalAddress().getHostAddress()   + ":" + client.getLocalPort();
                System.out.println("Accepted " + remote + " -> " + local);
                pool.submit(new TcpClientHandler(client, repo));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }
    private static List<String> localIPv4() throws SocketException {
        List<String> ips = new ArrayList<>();
        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        while (ifaces.hasMoreElements()) {
            NetworkInterface ni = ifaces.nextElement();
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = addrs.nextElement();
                if (a instanceof Inet4Address) {
                    ips.add(a.getHostAddress());
                }
            }
        }
        ips.sort(Comparator.naturalOrder());
        return ips;
    }
}
