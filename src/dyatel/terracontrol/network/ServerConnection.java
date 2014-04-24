package dyatel.terracontrol.network;

import dyatel.terracontrol.Server;
import dyatel.terracontrol.level.CellMaster;
import dyatel.terracontrol.level.ServerLevel;
import dyatel.terracontrol.util.Debug;
import dyatel.terracontrol.util.Util;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;

public class ServerConnection extends Connection {

    private Player[] players = new Player[2];

    private int currentPlayer;

    private int state = -1; // -1 - waiting connections, 0 - playing, 1 - end

    public ServerConnection(ServerLevel level, int port) {
        super(port, Debug.serverDebug);

        this.level = level;
        start();
    }

    protected void process(final DatagramPacket packet) {
        final String message = new String(packet.getData()).trim();
        final InetAddress address = packet.getAddress();
        final int port = packet.getPort();
        //debug.println(message);

        if (message.startsWith("/co/")) {
            if (((ServerLevel) level).isGenerated()) {
                debug.println("Connection request from " + packet.getAddress() + ":" + packet.getPort() + "");

                String data = "/da/" + level.getWidth() + "x" + level.getHeight() + "x" + level.getMasters().size() + "x";

                if (!players[0].isConnected()) {
                    players[0].connect(address, port);
                    data += players[0].getMaster().getID() + "x" + players[0].getID() + "x" + players[1].getMaster().getID() + "x" + players[1].getID();
                } else {
                    players[1].connect(address, port);
                    data += players[1].getMaster().getID() + "x" + players[1].getID() + "x" + players[0].getMaster().getID() + "x" + players[0].getID();
                }
                data += "x" + Server.colors.length;
                for (int i = 0; i < Server.colors.length; i++) {
                    data += "x" + Server.colors[i];
                }

                send(data, address, port);
            }
        } else if (message.startsWith("/ma/")) {
            new Thread("MasterSender") {
                public void run() {
                    String[] dataR = message.substring(4).split("x");
                    int start = Integer.parseInt(dataR[0]);
                    int end = Integer.parseInt(dataR[1]);

                    ArrayList<CellMaster> masters = level.getMasters();

                    if (end >= masters.size()) end = masters.size() - 1;

                    String data = "";
                    String temp;
                    int messageStart = start;

                    for (int i = start; i <= end; i++) {
                        int color = level.getColorID(masters.get(i).getColor());
                        temp = "/ma/" + messageStart + "x";
                        if ((temp + i + data + "x" + color).length() > Connection.BUFFER_SIZE) {
                            send(temp + (i - 1) + data, address, port);
                            messageStart = i;
                            data = "";
                        }
                        data += "x" + color;
                    }
                    send("/ma/" + messageStart + "x" + end + data, address, port);
                }
            }.start();
        } else if (message.startsWith("/ce/")) {
            new Thread("LevelSender") {
                public void run() {
                    String[] dataR = message.substring(4).split("x");
                    int start = Integer.parseInt(dataR[0]);
                    int end = Integer.parseInt(dataR[1]);

                    int width = level.getWidth();
                    int height = level.getHeight();
                    if (start > width * height) return;
                    if (end >= width * height) end = width * height - 1;

                    String data = "";
                    String temp;
                    int messageStart = start;

                    for (int i = start; i <= end; i++) {
                        int id = level.getMaster(i % width, i / width).getID();
                        temp = "/ce/" + messageStart + "x";
                        if ((temp + i + data + "x" + id).length() > Connection.BUFFER_SIZE) {
                            send(temp + (i - 1) + data, address, port);
                            messageStart = i;
                            data = "";
                        }
                        data += "x" + id;
                    }
                    send("/ce/" + messageStart + "x" + end + data, address, port);
                }
            }.start();
        } else if (message.startsWith("/rd/")) {
            if (players[0].equals(address, port)) {
                players[0].ready();
            } else {
                players[1].ready();
            }

            if (players[0].isReady() && players[1].isReady()) {
                send("/st/0", players[0].getAddress(), players[0].getPort());
                send("/st/0", players[1].getAddress(), players[1].getPort());
                startTurnManager();
            }
        } else if (message.startsWith("/to/")) {
            String[] dataR = message.substring(4).split("x");
            int turn = Integer.parseInt(dataR[0]);
            int colorID = Integer.parseInt(dataR[1]);

            if (players[0].equals(address, port)) {
                if (players[0].getTurns() == turn - 1) {
                    players[0].addTurn(colorID);
                    currentPlayer = 1;
                }
            } else {
                if (players[1].getTurns() == turn - 1) {
                    players[1].addTurn(colorID);
                    currentPlayer = 0;
                }
            }
        } else if (message.startsWith("/te/")) {
            if (players[0].equals(address, port)) {
                if (players[1].getLastTurn() != -1)
                    send("/te/" + players[1].getTurns() + "x" + players[1].getLastTurn(), players[0].getAddress(), players[0].getPort());
            } else {
                if (players[0].getLastTurn() != -1)
                    send("/te/" + players[0].getTurns() + "x" + players[0].getLastTurn(), players[1].getAddress(), players[1].getPort());
            }
        }
    }

    public void createPlayers() {
        players[0] = new Player(0, 0, 0, level);
        players[1] = new Player(level.getWidth() - 1, level.getHeight() - 1, 1, level);
    }

    public void gameOver() {
        int p1 = players[0].getMaster().getCells().size();
        int p2 = players[1].getMaster().getCells().size();
        if (p1 > p2) {
            send("/st/1", players[0].getAddress(), players[0].getPort());
            send("/st/2", players[1].getAddress(), players[1].getPort());
        } else if (p1 < p2) {
            send("/st/2", players[0].getAddress(), players[0].getPort());
            send("/st/1", players[1].getAddress(), players[1].getPort());
        } else {
            send("/st/3", players[0].getAddress(), players[0].getPort());
            send("/st/3", players[1].getAddress(), players[1].getPort());
        }

        state = 2;
    }

    private void startTurnManager() {
        state = 0;
        new Thread() {
            public void run() {
                currentPlayer = Util.getRandom().nextInt(players.length);

                while(running && state == 0) {
                    send("/tu/" + (players[currentPlayer].getTurns() + 1), players[currentPlayer].getAddress(), players[currentPlayer].getPort());
                    try {
                        sleep(100);
                    } catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

}
