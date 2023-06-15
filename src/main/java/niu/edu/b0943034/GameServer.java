package niu.edu.b0943034;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;

public class GameServer extends JFrame {

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private JTextArea txt = new JTextArea("[" + sdf.format(new Date()) + "] " + "伺服器運行中...\n");
    private ServerSocket serverSocket = null;
    private static List<Player> playerList = new ArrayList<>();
    private ExecutorService exec = null;

    public GameServer(int port) throws IOException, SocketException {
        txt.setEditable(false);
        txt.append("[" + sdf.format(new Date()) + "] " + "執行於 port:" + port + "...\n");

        setLayout(new BorderLayout());
        this.add(new JScrollPane(txt), BorderLayout.CENTER);
        setSize(500, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);

        serverSocket = new ServerSocket(port);
        exec = Executors.newCachedThreadPool();

        loadPlayerList(); // 載入之前的 playerList

        while (true) {
            Socket socket = serverSocket.accept();
            exec.execute(new Communication(socket, playerList, txt, sdf));
        }
    }

    public void loadPlayerList() {
        try {
            File file = new File("playerList.json");
            if (!file.exists()) {
                file.createNewFile();
            } else {
                BufferedReader br = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                String json = sb.toString();
                Gson gson = new Gson();
                playerList = gson.fromJson(json, new TypeToken<List<Player>>() {
                }.getType());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void savePlayerList() {
        try {
            Gson gson = new Gson();
            String json = gson.toJson(playerList);
            FileWriter writer = new FileWriter("playerList.json");
            writer.write(json);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendPlayerList(Socket socket) {
        try {
            Gson gson = new Gson();
            String json = gson.toJson(playerList);
            System.out.println(json);
            for (Player player : playerList) {
                DataOutputStream toClient = new DataOutputStream(socket.getOutputStream());
                toClient.writeUTF(json);
                toClient.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updatePlayerList(Socket socket, Player player) {
        Player existingPlayer = playerList.stream()
                .filter(p -> p.getName().equals(player.getName()) && p.getPort() == player.getPort())
                .findFirst()
                .orElse(null);
        if (existingPlayer != null) {
            existingPlayer.setScore(player.getScore());
            existingPlayer.setOnline(true);
        } else {
            playerList.add(player);
        }
        savePlayerList();
        sendPlayerList(socket);
    }

    public static class Communication implements Runnable {
        private Socket socket;
        private DataInputStream fromClient;
        private String msg;
        private final List<Player> playerList;
        private final JTextArea txt;
        private final SimpleDateFormat sdf;

        public Communication(Socket socket, List<Player> playerList, JTextArea txt, SimpleDateFormat sdf)
                throws IOException, SocketException {
            this.socket = socket;
            this.playerList = playerList;
            this.txt = txt;
            this.sdf = sdf;
            fromClient = new DataInputStream(socket.getInputStream());
            sendPlayerList(socket);
            txt.append("[" + sdf.format(new Date()) + "]" + "[ :" + socket.getPort() + "] 連接成功\n");
        }

        @Override
        public void run() {
            try {
                while ((msg = fromClient.readUTF()) != null) {
                    Gson gson = new Gson();
                    Player player = gson.fromJson(msg, Player.class);
                    player.setOnline(false);
                    updatePlayerList(socket, player);
                    txt.append("[" + sdf.format(new Date()) + "]" + "[" + player.getName() + ":" + socket.getPort() + "] 更新分數為 "
                            + player.getScore() + "\n");
                }
            } catch (Exception e) {
                txt.append("[" + sdf.format(new Date()) + "]" + "[ :" + socket.getPort() + "] 斷開連線\n");
                Player disconnectedPlayer = playerList.stream().filter(p -> p.getPort() == socket.getPort()).findFirst().orElse(null);
                if (disconnectedPlayer != null) {
                    disconnectedPlayer.setOnline(false);
                    savePlayerList();
                    sendPlayerList(socket);
                }
            }
        }
    }
}
