package server;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class CubeRushServer extends WebSocketServer {

    private static final AtomicInteger NEXT_ID =
            new AtomicInteger(1);

    private final Map<WebSocket, Player> players =
            new ConcurrentHashMap<WebSocket, Player>();

    public CubeRushServer(int port) {
        super(new InetSocketAddress("0.0.0.0", port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {

        int id = NEXT_ID.getAndIncrement();

        Player player = new Player(id, 0, 0);
        players.put(conn, player);

        conn.send("WELCOME|" + id);

        sendPlayers();

        System.out.println("Player " + id + " connected.");
    }

    @Override
    public void onClose(
            WebSocket conn,
            int code,
            String reason,
            boolean remote) {

        Player player = players.remove(conn);

        if (player != null) {

            sendAll("LEAVE|" + player.id);

            sendPlayers();

            System.out.println(
                    "Player " +
                    player.id +
                    " disconnected."
            );
        }
    }

    @Override
    public void onMessage(
            WebSocket conn,
            String message) {

        Player player = players.get(conn);

        if (player == null || message == null) {
            return;
        }

        String[] data = message.split("\\|");

        if (data.length == 3 &&
                "MOVE".equals(data[0])) {

            try {

                float x =
                        Float.parseFloat(data[1]);

                float y =
                        Float.parseFloat(data[2]);

                player.x = x;
                player.y = y;

                sendAll(
                        "PLAYER|" +
                        player.id +
                        "|" +
                        player.x +
                        "|" +
                        player.y
                );

            } catch (NumberFormatException e) {
                System.out.println(
                        "Invalid MOVE: " +
                        message
                );
            }
        }
    }

    @Override
    public void onError(
            WebSocket conn,
            Exception ex) {

        System.out.println(
                "Server error: " +
                ex.getMessage()
        );
    }

    @Override
    public void onStart() {

        System.out.println(
                "Cube Rush server started."
        );

        System.out.println(
                "Port: " +
                getPort()
        );
    }

    private void sendPlayers() {

        StringBuilder message =
                new StringBuilder("PLAYERS");

        for (Player player : players.values()) {

            message.append("|")
                    .append(player.id)
                    .append("|")
                    .append(player.x)
                    .append("|")
                    .append(player.y);
        }

        sendAll(message.toString());
    }

    private void sendAll(String message) {

        for (WebSocket connection :
                players.keySet()) {

            if (connection != null &&
                    connection.isOpen()) {

                connection.send(message);
            }
        }
    }

    private static class Player {

        int id;
        float x;
        float y;

        Player(
                int id,
                float x,
                float y) {

            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    public static void main(String[] args) {

        int port = 10000;

        String renderPort =
                System.getenv("PORT");

        if (renderPort != null &&
                renderPort.length() > 0) {

            try {

                port =
                        Integer.parseInt(
                                renderPort
                        );

            } catch (NumberFormatException e) {

                port = 10000;
            }
        }

        CubeRushServer server =
                new CubeRushServer(port);

        server.start();
    }
}
