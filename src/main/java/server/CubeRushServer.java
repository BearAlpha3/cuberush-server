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

    private static final float WORLD_SIZE = 2200f;

    private static final int MAX_HP = 100;
    private static final int DAMAGE = 25;

    private static final float SHOOT_RANGE = 900f;
    private static final float HIT_RADIUS = 55f;

    private static final long SHOT_COOLDOWN = 100;

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

        Player player = new Player(
                id,
                0,
                0,
                "Player" + id
        );

        players.put(conn, player);

        conn.send("WELCOME|" + id);

        sendPlayers();

        sendNamesTo(conn);

        sendHealthTo(conn);

        System.out.println(
                "Player " + id + " connected."
        );
    }

    @Override
    public void onClose(
            WebSocket conn,
            int code,
            String reason,
            boolean remote) {

        Player player = players.remove(conn);

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

        Player player = players.get(conn);

        if (player == null) return;
        if (message == null) return;

        String[] data =
                message.split("\\|", -1);

        if (data.length == 0) return;


        // =========================
        // MOVEMENT
        // =========================

        if (data.length == 3 &&
                "MOVE".equals(data[0])) {

            try {

                float x =
                        Float.parseFloat(data[1]);

                float y =
                        Float.parseFloat(data[2]);

                float half =
                        WORLD_SIZE / 2f;

                if (x < -half) {
                    x = -half;
                }

                if (x > half) {
                    x = half;
                }

                if (y < -half) {
                    y = -half;
                }

                if (y > half) {
                    y = half;
                }

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


        // =========================
        // NAME
        // =========================

        if (data.length >= 2 &&
                "NAME".equals(data[0])) {

            String name =
                    data[1]
                    .replace("\n", "")
                    .replace("\r", "")
                    .replace("|", "")
                    .trim();

            if (name.length() == 0) {

                name =
                        "Player" +
                        player.id;
            }

            if (name.length() > 16) {

                name =
                        name.substring(0, 16);
            }

            player.name = name;

            sendAll(
                    "NAME|" +
                    player.id +
                    "|" +
                    player.name
            );

            return;
        }


        // =========================
        // CHAT
        // =========================

        if (data.length >= 3 &&
                "CHAT".equals(data[0])) {

            String text =
                    data[2]
                    .replace("\n", "")
                    .replace("\r", "")
                    .trim();

            if (text.length() == 0) {
                return;
            }

            if (text.length() > 100) {

                text =
                        text.substring(0, 100);
            }

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


        // =========================
        // SHOOT
        // =========================

        if (data.length >= 3 &&
                "SHOOT".equals(data[0])) {

            try {

                float dx =
                        Float.parseFloat(data[1]);

                float dy =
                        Float.parseFloat(data[2]);

                processShot(
                        player,
                        dx,
                        dy
                );

            } catch (NumberFormatException e) {

                System.out.println(
                        "Invalid SHOOT: " +
                        message
                );
            }

            return;
        }
    }


    // =========================
    // PROCESS SHOT
    // =========================

    private void processShot(
            Player shooter,
            float dx,
            float dy) {

        if (!shooter.alive) {
            return;
        }

        float length =
                (float) Math.sqrt(
                        dx * dx +
                        dy * dy
                );

        if (length < 0.001f) {
            return;
        }

        dx /= length;
        dy /= length;

        long now =
                System.currentTimeMillis();

        if (now - shooter.lastShot <
                SHOT_COOLDOWN) {

            return;
        }

        shooter.lastShot = now;

        Player target = null;

        float closestDistance =
                SHOOT_RANGE + 1;


        // =========================
        // FIND TARGET
        // =========================

        for (Player other :
                players.values()) {

            if (other == shooter) {
                continue;
            }

            if (!other.alive) {
                continue;
            }

            float vx =
                    other.x -
                    shooter.x;

            float vy =
                    other.y -
                    shooter.y;

            float forward =
                    vx * dx +
                    vy * dy;

            if (forward <= 0) {
                continue;
            }

            if (forward >
                    SHOOT_RANGE) {

                continue;
            }

            float perpendicularX =
                    vx -
                    dx * forward;

            float perpendicularY =
                    vy -
                    dy * forward;

            float perpendicularDistance =
                    (float) Math.sqrt(
                            perpendicularX *
                            perpendicularX +
                            perpendicularY *
                            perpendicularY
                    );

            if (perpendicularDistance <=
                    HIT_RADIUS) {

                if (forward <
                        closestDistance) {

                    closestDistance =
                            forward;

                    target = other;
                }
            }
        }


        // =========================
        // NO TARGET
        // =========================

        if (target == null) {
            return;
        }


        // =========================
        // DAMAGE
        // =========================

        target.hp -= DAMAGE;

        if (target.hp < 0) {
            target.hp = 0;
        }

        sendAll(
                "HIT|" +
                shooter.id +
                "|" +
                target.id +
                "|" +
                target.hp
        );


        // =========================
        // DEATH
        // =========================

        if (target.hp <= 0) {

            target.alive = false;

            final int targetId =
                    target.id;

            sendAll(
                    "DEAD|" +
                    targetId
            );


            // =========================
            // RESPAWN
            // =========================

            new Thread(
                    new Runnable() {

                        @Override
                        public void run() {

                            try {

                                Thread.sleep(
                                        2500
                                );

                            } catch (
                                    InterruptedException e) {

                                return;
                            }


                            Player targetPlayer =
                                    findPlayerById(
                                            targetId
                                    );

                            if (targetPlayer ==
                                    null) {

                                return;
                            }


                            targetPlayer.x = 0;
                            targetPlayer.y = 0;

                            targetPlayer.hp =
                                    MAX_HP;

                            targetPlayer.alive =
                                    true;


                            sendAll(
                                    "RESPAWN|" +
                                    targetPlayer.id +
                                    "|" +
                                    targetPlayer.x +
                                    "|" +
                                    targetPlayer.y +
                                    "|" +
                                    targetPlayer.hp
                            );

                        }

                    }
            ).start();
        }
    }


    // =========================
    // FIND PLAYER
    // =========================

    private Player findPlayerById(
            int id) {

        for (Player player :
                players.values()) {

            if (player.id == id) {

                return player;
            }
        }

        return null;
    }


    // =========================
    // ERROR
    // =========================

    @Override
    public void onError(
            WebSocket conn,
            Exception ex) {

        System.out.println(
                "Server error: " +
                ex.getMessage()
        );
    }


    // =========================
    // START
    // =========================

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


    // =========================
    // SEND PLAYERS
    // =========================

    private void sendPlayers() {

        StringBuilder message =
                new StringBuilder(
                        "PLAYERS"
                );

        for (Player player :
                players.values()) {

            message
                    .append("|")
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


    // =========================
    // SEND NAMES
    // =========================

    private void sendNamesTo(
            WebSocket target) {

        if (target == null) {
            return;
        }

        if (!target.isOpen()) {
            return;
        }

        for (Player player :
                players.values()) {

            target.send(
                    "NAME|" +
                    player.id +
                    "|" +
                    player.name
            );
        }
    }


    // =========================
    // SEND HEALTH
    // =========================

    private void sendHealthTo(
            WebSocket target) {

        if (target == null) {
            return;
        }

        if (!target.isOpen()) {
            return;
        }

        for (Player player :
                players.values()) {

            target.send(
                    "HEALTH|" +
                    player.id +
                    "|" +
                    player.hp
            );

            if (!player.alive) {

                target.send(
                        "DEAD|" +
                        player.id
                );
            }
        }
    }


    // =========================
    // SEND ALL
    // =========================

    private void sendAll(
            String message) {

        for (WebSocket connection :
                players.keySet()) {

            if (connection == null) {
                continue;
            }

            if (connection.isOpen()) {

                connection.send(
                        message
                );
            }
        }
    }


    // =========================
    // PLAYER
    // =========================

    private static class Player {

        int id;

        float x;
        float y;

        int hp;

        boolean alive;

        String name;

        long lastShot;


        Player(
                int id,
                float x,
                float y,
                String name) {

            this.id = id;

            this.x = x;
            this.y = y;

            this.name = name;

            this.hp = MAX_HP;

            this.alive = true;

            this.lastShot = 0;
        }
    }


    // =========================
    // MAIN
    // =========================

    public static void main(
            String[] args) {

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

            } catch (
                    NumberFormatException e) {

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
