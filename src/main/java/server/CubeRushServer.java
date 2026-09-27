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
    public void onOpen(
            WebSocket conn,
            ClientHandshake handshake) {

        int id = NEXT_ID.getAndIncrement();

        Player player =
                new Player(
                        id,
                        0,
                        0,
                        "Player" + id
                );

        players.put(conn, player);

        conn.send(
                "WELCOME|" +
                id
        );

        sendPlayers();

        sendNamesTo(conn);

        System.out.println(
                "Player " +
                id +
                " connected."
        );
    }

    @Override
    public void onClose(
            WebSocket conn,
            int code,
            String reason,
            boolean remote) {

        Player player =
                players.remove(conn);

        if (player != null) {

            sendAll(
                    "LEAVE|" +
                    player.id
            );

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

        Player player =
                players.get(conn);

        if (
                player == null ||
                message == null
        ) {
            return;
        }

        String[] data =
                message.split(
                        "\\|",
                        -1
                );

        if (data.length == 0) {
            return;
        }

        /*
         * MOVIMENTO
         *
         * MOVE|x|y
         */
        if (
                data.length == 3 &&
                "MOVE".equals(data[0])
        ) {

            try {

                float x =
                        Float.parseFloat(
                                data[1]
                        );

                float y =
                        Float.parseFloat(
                                data[2]
                        );

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

            return;
        }

        /*
         * NOME
         *
         * NAME|nome
         */
        if (
                data.length >= 2 &&
                "NAME".equals(data[0])
        ) {

            String name =
                    data[1]
                            .replace(
                                    "\n",
                                    ""
                            )
                            .replace(
                                    "\r",
                                    ""
                            )
                            .trim();

            if (name.length() == 0) {
                name = "Player" + player.id;
            }

            if (name.length() > 16) {
                name =
                        name.substring(
                                0,
                                16
                        );
            }

            player.name = name;

            sendAll(
                    "NAME|" +
                    player.id +
                    "|" +
                    player.name
            );

            System.out.println(
                    "Player " +
                    player.id +
                    " name: " +
                    player.name
            );

            return;
        }

        /*
         * CHAT
         *
         * CHAT|nome|mensagem
         */
        if (
                data.length >= 3 &&
                "CHAT".equals(data[0])
        ) {

            String text =
                    data[2]
                            .replace(
                                    "\n",
                                    ""
                            )
                            .replace(
                                    "\r",
                                    ""
                            )
                            .trim();

            if (text.length() == 0) {
                return;
            }

            if (text.length() > 100) {

                text =
                        text.substring(
                                0,
                                100
                        );
            }

            /*
             * O servidor usa o nome
             * registrado no Player.
             *
             * Assim ninguém consegue
             * fingir ser outro jogador.
             */
            sendAll(
                    "CHAT|" +
                    player.name +
                    "|" +
                    text
            );

            System.out.println(
                    "[CHAT] " +
                    player.name +
                    ": " +
                    text
            );

            return;
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

    /*
     * Envia a posição de todos
     * os jogadores.
     *
     * PLAYERS|id|x|y|id|x|y...
     */
    private void sendPlayers() {

        StringBuilder message =
                new StringBuilder(
                        "PLAYERS"
                );

        for (
                Player player :
                players.values()
        ) {

            message.append("|")
                    .append(player.id)
                    .append("|")
                    .append(player.x)
                    .append("|")
                    .append(player.y);
        }

        sendAll(
                message.toString()
        );
    }

    /*
     * Envia os nomes atuais
     * para quem acabou de entrar.
     */
    private void sendNamesTo(
            WebSocket target) {

        if (
                target == null ||
                !target.isOpen()
        ) {
            return;
        }

        for (
                Player player :
                players.values()
        ) {

            target.send(
                    "NAME|" +
                    player.id +
                    "|" +
                    player.name
            );
        }
    }

    /*
     * Envia mensagem para
     * todos os jogadores.
     */
    private void sendAll(
            String message) {

        for (
                WebSocket connection :
                players.keySet()
        ) {

            if (
                    connection != null &&
                    connection.isOpen()
            ) {

                connection.send(
                        message
                );
            }
        }
    }

    private static class Player {

        int id;

        float x;
        float y;

        String name;

        Player(
                int id,
                float x,
                float y,
                String name) {

            this.id = id;
            this.x = x;
            this.y = y;
            this.name = name;
        }
    }

    public static void main(
            String[] args) {

        int port = 10000;

        String renderPort =
                System.getenv(
                        "PORT"
                );

        if (
                renderPort != null &&
                renderPort.length() > 0
        ) {

            try {

                port =
                        Integer.parseInt(
                                renderPort
                        );

            } catch (
                    NumberFormatException e
            ) {

                port = 10000;
            }
        }

        CubeRushServer server =
                new CubeRushServer(
                        port
                );

        server.start();
    }
                }
