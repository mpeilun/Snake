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

    // 日期時間格式化物件
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // 顯示伺服器運行狀態的文字區域
    private JTextArea txt = new JTextArea("[" + sdf.format(new Date()) + "] " + "伺服器運行中...\n");

    // 伺服器Socket物件
    private ServerSocket serverSocket = null;

    // 存儲客戶端Socket的Map
    private static Map<Integer, Socket> socketMap = new HashMap<>();

    // 存儲玩家清單的List
    private static List<Player> playerList = new ArrayList<>();

    // 執行緒池
    private ExecutorService exec = null;

    public GameServer(int port) throws IOException, SocketException {
        setTitle("Snake Server");

        // 設置文字區域為不可編輯並添加到視窗中
        txt.setEditable(false);
        txt.append("[" + sdf.format(new Date()) + "] " + "執行於 port:" + port + "...\n");
        setLayout(new BorderLayout());
        this.add(new JScrollPane(txt), BorderLayout.CENTER);
        setSize(500, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);

        // 創建ServerSocket並監聽指定的port
        serverSocket = new ServerSocket(port);

        // 創建執行緒池
        exec = Executors.newCachedThreadPool();

        // 載入玩家清單
        loadPlayerList();

        // 初始化所有玩家的線上狀態
        initOnlineStatus();

        while (true) {
            // 接受客戶端的連線
            Socket socket = serverSocket.accept();

            // 創建通訊執行緒並加入執行緒池中
            exec.execute(new Communication(socket, playerList, txt, sdf, socketMap));
        }
    }

    // 載入玩家清單
    public void loadPlayerList() {
        try {
            // 檢查玩家清單檔案是否存在，若不存在則創建新的檔案
            File file = new File("playerList.json");
            if (!file.exists()) {
                file.createNewFile();
            } else {
                // 讀取玩家清單檔案中的內容並轉換為List<Player>物件
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

    // 儲存玩家清單
    public static void savePlayerList() {
        try {
            // 將玩家清單轉換為JSON格式並寫入檔案中
            Gson gson = new Gson();
            String json = gson.toJson(playerList);
            FileWriter writer = new FileWriter("playerList.json");
            writer.write(json);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 傳送玩家清單給所有連線的客戶端
    public static void sendPlayerList(Map<Integer, Socket> socketMap) {
        try {
            // 將玩家清單轉換為JSON格式並傳送給每個客戶端
            Gson gson = new Gson();
            String json = gson.toJson(playerList);
            System.out.println("Send: " + json);
            for (Socket socket : socketMap.values()) {
                DataOutputStream toClient = new DataOutputStream(socket.getOutputStream());
                toClient.writeUTF(json);
                toClient.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 更新玩家清單
    public static void updatePlayerList(Player player) {
        // 檢查玩家是否已存在於清單中
        Player existingPlayer = playerList.stream()
                .filter(p -> p.getName().equals(player.getName()) && p.getPort() == player.getPort())
                .findFirst()
                .orElse(null);
        if (existingPlayer != null) {
            // 若玩家已存在，則更新分數和最佳分數，並將線上狀態設為true
            existingPlayer.setScore(player.getScore());
            if (existingPlayer.getBest() < player.getBest()) {
                existingPlayer.setBest(player.getBest());
            }
            existingPlayer.setOnline(true);
        } else {
            // 若玩家不存在，則將玩家添加到清單中
            playerList.add(player);
        }
        // 儲存玩家清單並傳送給所有連線的客戶端
        savePlayerList();
        sendPlayerList(socketMap);
    }

    // 初始化所有玩家的線上狀態
    public void initOnlineStatus() {
        // 將所有玩家的線上狀態設為false
        for (Player player : playerList) {
            player.setOnline(false);
        }
        // 儲存玩家清單並傳送給所有連線的客戶端
        savePlayerList();
        sendPlayerList(socketMap);
    }

    // 通訊執行緒
    public static class Communication implements Runnable {
        private Socket socket;
        private DataInputStream fromClient;
        private String msg;
        private final List<Player> playerList;
        private final JTextArea txt;
        private final SimpleDateFormat sdf;
        private final Map<Integer, Socket> socketMap;

        public Communication(Socket socket, List<Player> playerList, JTextArea txt, SimpleDateFormat sdf, Map<Integer, Socket> socketMap)
                throws IOException, SocketException {
            this.socket = socket;
            this.playerList = playerList;
            this.txt = txt;
            this.sdf = sdf;
            this.socketMap = socketMap;

            // 創建從客戶端讀取訊息的DataInputStream物件
            fromClient = new DataInputStream(socket.getInputStream());

            // 將客戶端Socket加入socketMap中並傳送玩家清單給該客戶端
            socketMap.put(socket.getPort(), socket);
            sendPlayerList(socketMap);

            // 在文字區域中顯示連線成功的訊息
            txt.append("[" + sdf.format(new Date()) + "]" + "[ :" + socket.getPort() + "] 連接成功\n");
        }

        @Override
        public void run() {
            try {
                while ((msg = fromClient.readUTF()) != null) {
                    // 接收客戶端傳送的訊息並解析為Player物件
                    System.out.println("Received: " + msg);
                    Gson gson = new Gson();
                    Player player = gson.fromJson(msg, Player.class);
                    player.setOnline(false);

                    // 更新玩家清單並在文字區域中顯示分數更新訊息
                    updatePlayerList(player);
                    txt.append("[" + sdf.format(new Date()) + "]" + "[" + player.getName() + ":" + socket.getPort() + "] 更新分數為 "
                            + player.getScore() + "\n");
                }
            } catch (Exception e) {
                // 在文字區域中顯示斷開連線的訊息
                txt.append("[" + sdf.format(new Date()) + "]" + "[ :" + socket.getPort() + "] 斷開連線\n");

                // 獲取斷開連線的玩家並設置線上狀態為false，從socketMap中移除該客戶端Socket
                Player disconnectedPlayer = playerList.stream().filter(p -> p.getPort() == socket.getPort()).findFirst().orElse(null);
                if (disconnectedPlayer != null) {
                    disconnectedPlayer.setOnline(false);
                    socketMap.remove(socket.getPort());

                    // 儲存玩家清單並傳送給所有連線的客戶端
                    savePlayerList();
                    sendPlayerList(socketMap);
                }
            }
        }
    }
}
