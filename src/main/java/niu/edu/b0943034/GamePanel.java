package niu.edu.b0943034;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.Timer;

public class GamePanel extends JPanel implements ActionListener {

    private ExecutorService exec; // 執行緒池，用於執行資料接收任務
    private Socket clientSocket; // 客戶端 Socket
    private DataInputStream fromServer; // 從伺服器接收資料的輸入串流
    private DataOutputStream toServer; // 向伺服器發送資料的輸出串流

    private static final int SCREEN_WIDTH = 500; // 螢幕寬度
    private static final int SCREEN_HEIGHT = 500; // 螢幕高度
    private static final int UNIT_SIZE = 10; // 單位大小
    private static final int GAME_UNITS = (SCREEN_WIDTH * SCREEN_HEIGHT) / UNIT_SIZE; // 遊戲單位數量
    private static final int DELAY = 55; // 遊戲更新間隔時間

    private final int[] x = new int[GAME_UNITS]; // 蛇身體的 x 坐標
    private final int[] y = new int[GAME_UNITS]; // 蛇身體的 y 坐標
    private int bodyParts = 3; // 蛇身體的部分數量
    private int applesEaten = 0; // 吃到的蘋果數量
    private String receivedMessage; // 從伺服器接收的訊息
    private List<Player> playerList = new ArrayList<>(); // 玩家列表
    private int appleX; // 蘋果的 x 坐標
    private int appleY; // 蘋果的 y 坐標
    private char direction = 'R'; // 蛇的移動方向，初始為右方
    private boolean running = false; // 遊戲是否運行中
    private boolean started = false; // 遊戲是否已開始
    private boolean paused = false; // 遊戲是否暫停中
    private boolean gameOver = false; // 遊戲是否結束
    private Timer timer = new Timer(DELAY, this); // 遊戲更新計時器
    private Random random; // 隨機數生成器
    private Player player; // 玩家資訊

    public GamePanel() {
        random = new Random();
        this.setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        this.setBackground(Color.BLACK);
        this.setFocusable(true);
        this.addKeyListener(new MyKeyAdapter());

        // 詢問伺服器的 IP 和 Port
        String inputIpPort = JOptionPane.showInputDialog(null, "請輸入 IP:Port", "localhost:8888");
        String[] ipPortArr = inputIpPort.split(":");
        String host = ipPortArr[0];
        int port = Integer.parseInt(ipPortArr[1]);

        // 連線至伺服器
        client(host, port);

        // 詢問玩家的遊戲 ID
        String inputName = "";
        while (inputName.isEmpty()) {
            inputName = JOptionPane.showInputDialog(null, "請輸入遊戲 ID：");
        }

        // 建立玩家物件
        player = new Player(inputName, clientSocket.getLocalPort(), 0, 0, true);

        startGame();
    }

    // 連線至伺服器
    private void client(String host, int port) {
        try {
            clientSocket = new Socket(host, port);
            fromServer = new DataInputStream(clientSocket.getInputStream());
            toServer = new DataOutputStream(clientSocket.getOutputStream());

            exec = Executors.newCachedThreadPool();
            exec.execute(new ClientSocket());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 伺服器資料接收執行緒
    class ClientSocket implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    receivedMessage = fromServer.readUTF();
                    System.out.println("Received: " + receivedMessage);
                    topdata(receivedMessage);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (clientSocket != null || started == false) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 遊戲開始
    public void startGame() {
        if (started && paused) {
            return;
        }
        updateScore(player, 0); // 更新玩家分數為 0
        newApple(); // 產生新的蘋果
        running = true; // 開始遊戲

        // 計算螢幕中心點的坐標
        int centerX = SCREEN_WIDTH / 2;
        int centerY = SCREEN_HEIGHT / 2;

        // 設置蛇頭的起始位置為螢幕中心
        x[0] = centerX;
        y[0] = centerY;

        // 計算蛇身體初始位置
        for (int i = 1; i < bodyParts; i++) {
            x[i] = centerX - i * UNIT_SIZE;
            y[i] = centerY;
        }

        timer.start(); // 啟動遊戲更新計時器
    }

    // 產生新的蘋果
    public void newApple() {
        appleX = random.nextInt((int) (SCREEN_WIDTH / UNIT_SIZE)) * UNIT_SIZE;
        appleY = random.nextInt((int) (SCREEN_HEIGHT / UNIT_SIZE)) * UNIT_SIZE;
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        if (running && !paused) {
            move(); // 移動蛇
            checkApple(); // 檢查是否吃到蘋果
            checkCollisions(); // 檢查碰撞
        }
        repaint(); // 重新繪製遊戲畫面
    }

    // 鍵盤事件監聽器
    public class MyKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                    if (direction != 'R') {
                        direction = 'L'; // 左
                    }
                    break;

                case KeyEvent.VK_RIGHT:
                    if (direction != 'L') {
                        direction = 'R'; // 右
                    }
                    break;

                case KeyEvent.VK_UP:
                    if (direction != 'D') {
                        direction = 'U'; // 上
                    }
                    break;

                case KeyEvent.VK_DOWN:
                    if (direction != 'U') {
                        direction = 'D'; // 下
                    }
                    break;

                case KeyEvent.VK_SPACE:
                    if (paused) {
                        resume(); // 恢復遊戲
                    } else if (running) {
                        pause(); // 暫停遊戲
                    }
                    break;
                case KeyEvent.VK_ENTER:
                    if (!started) {
                        started = true;
                        startGame(); // 開始遊戲
                    } else if (gameOver) {
                        started = false;
                        restartGame(); // 重新開始遊戲
                    }
                    break;
            }
        }
    }

    // 移動蛇
    public void move() {
        for (int i = bodyParts; i > 0; i--) {
            x[i] = x[i - 1];
            y[i] = y[i - 1];
        }
        switch (direction) {
            case 'U':
                y[0] -= UNIT_SIZE; // 向上移動
                break;
            case 'D':
                y[0] += UNIT_SIZE; // 向下移動
                break;
            case 'L':
                x[0] -= UNIT_SIZE; // 向左移動
                break;
            case 'R':
                x[0] += UNIT_SIZE; // 向右移動
                break;
        }
    }

    // 檢查是否吃到蘋果
    public void checkApple() {
        if ((x[0] == appleX) && (y[0] == appleY)) {
            bodyParts++; // 蛇身體增加一部分
            applesEaten++; // 吃到的蘋果數量增加
            updateScore(player, applesEaten); // 更新玩家分數
            newApple(); // 產生新的蘋果
        }
    }

    // 檢查碰撞
    public void checkCollisions() {
        for (int i = bodyParts; i > 0; i--) {
            if ((x[0] == x[i]) && (y[0] == y[i])) {
                running = false; // 蛇頭碰到自己的身體，遊戲結束
            }
        }
        if (x[0] < 0 || x[0] > SCREEN_WIDTH || y[0] < 0 || y[0] > SCREEN_HEIGHT) {
            running = false; // 蛇頭碰到邊界，遊戲結束
        }
        if (!running) {
            timer.stop(); // 停止遊戲更新計時器
            paused = false; // 取消暫停狀態
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g); // 繪製遊戲畫面
    }

    // 更新伺服器資料
    public void topdata(String receivedMessage) {
        Gson gson = new Gson();
        Type type = new TypeToken<List<Player>>() {
        }.getType();
        playerList = gson.fromJson(receivedMessage, type); // 將接收到的 JSON 資料轉換為玩家列表
    }

    // 傳送玩家資料至伺服器
    public void sendMessage(Player player) {
        try {
            Gson gson = new Gson();
            String json = gson.toJson(player); // 將玩家物件轉換為 JSON 字串
            System.out.println("Send: " + json);
            toServer.writeUTF(json); // 發送 JSON 字串至伺服器
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 更新玩家分數
    public void updateScore(Player player, int score) {
        player.setScore(score); // 更新玩家分數
        player.setBest(score); // 更新玩家最高分數
        sendMessage(player); // 傳送玩家資料至伺服器
    }

    // 繪製遊戲畫面
    public void draw(Graphics g) {
        if (!started) {
            drawStartScreen(g); // 繪製開始畫面
        } else if (running) {
            drawSnake(g); // 繪製蛇
            drawPlayerList(g); // 繪製玩家列表
            drawTopRank(g); // 繪製排行榜
            if (paused) {
                drawPauseScreen(g); // 繪製暫停畫面
            }
        } else {
            gameOver(g); // 繪製遊戲結束畫面
        }
    }

    // 繪製開始畫面
    private void drawStartScreen(Graphics g) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 24));
        FontMetrics metrics = getFontMetrics(g.getFont());
        String nameAndPort = "[ID] " + player.getName() + ":" + player.getPort();
        g.drawString(nameAndPort, (SCREEN_WIDTH - metrics.stringWidth("[Enter] => 開始遊戲")) / 2, SCREEN_HEIGHT / 2 - 16);
        g.setColor(Color.RED);
        g.drawString("[Enter] => 開始遊戲", (SCREEN_WIDTH - metrics.stringWidth("[Enter] => 開始遊戲")) / 2, SCREEN_HEIGHT / 2 + 32);
        g.drawString("[Space] => 暫停遊戲", (SCREEN_WIDTH - metrics.stringWidth("[Space] => 暫停遊戲")) / 2, (SCREEN_HEIGHT / 2) + 64);
    }

    // 繪製蛇
    private void drawSnake(Graphics g) {
        g.setColor(Color.RED);
        g.fillOval(appleX, appleY, UNIT_SIZE, UNIT_SIZE); // 繪製蘋果

        for (int i = 0; i < bodyParts; i++) {
            g.setColor(i == 0 ? Color.BLUE : Color.GREEN);
            g.fillRect(x[i], y[i], UNIT_SIZE, UNIT_SIZE); // 繪製蛇的身體部分，蛇頭為藍色，其餘部分為綠色
        }
    }

    // 繪製玩家列表
    private void drawPlayerList(Graphics g) {
        int text_y = 0;
        int x_pos = 10;

        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        g.drawString("↳在線玩家", x_pos, g.getFont().getSize());
        text_y++;

        Comparator<Player> scoreComparator = (p1, p2) -> p2.getScore() - p1.getScore();
        Collections.sort(playerList, scoreComparator); // 根據分數排序玩家列表

        for (Player p : playerList) {
            if (p.getOnline()) {
                g.setFont(new Font("Monospaced", Font.BOLD, 14));
                String playerString = p.getName() + ":" + p.getPort() + " " + p.getScore();
                g.setColor(p.getPort() == player.getPort() ? Color.YELLOW : Color.CYAN);
                g.drawString(p.getPort() == player.getPort() ? "•" : "", x_pos + 8, g.getFont().getSize() + text_y * 20);
                g.drawString(playerString, x_pos + 20, g.getFont().getSize() + text_y * 20);
                text_y++;
            }
        }
    }

    // 繪製排行榜
    private void drawTopRank(Graphics g) {
        int text_y = 0;
        int x_pos = 340;

        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        g.drawString("↳歷史排行榜(TOP3)", x_pos, g.getFont().getSize());
        text_y++;

        Comparator<Player> scoreComparator = (p1, p2) -> p2.getBest() - p1.getBest();
        List<Player> topPlayers = playerList.stream().sorted(scoreComparator).limit(3).collect(Collectors.toList()); // 根據最高分數排序取前三名

        for (Player p : topPlayers) {
            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            String playerString = p.getName() + ":" + p.getPort() + " " + p.getBest();
            g.setColor(p.getPort() == player.getPort() ? Color.RED : Color.CYAN);
            g.drawString(p.getPort() == player.getPort() ? "•" : "", x_pos + 8, g.getFont().getSize() + text_y * 20);
            g.drawString(playerString, x_pos + 20, g.getFont().getSize() + text_y * 20);
            text_y++;
        }
    }

    // 繪製暫停畫面
    private void drawPauseScreen(Graphics g) {
        drawCenteredString(g, new Font("Monospaced", Font.BOLD, 24), Color.YELLOW, "遊戲暫停中...", SCREEN_HEIGHT / 2);
    }

    // 繪製遊戲結束畫面
    public void gameOver(Graphics g) {

        Font font = new Font("Monospaced", Font.BOLD, 36);
        drawCenteredString(g, font, Color.RED, "遊戲結束", SCREEN_HEIGHT / 2);

        font = new Font("Monospaced", Font.BOLD, 30);
        int y = (SCREEN_HEIGHT / 2) + 50;
        drawCenteredString(g, font, Color.RED, "Score: " + applesEaten, y);

        y += 75;
        drawCenteredString(g, font, Color.BLUE, "按下 Enter 重新開始遊戲...", y);

        gameOver = true;
    }

    // 重新開始遊戲
    public void restartGame() {
        updateScore(player, 0);

        running = false;
        started = false;
        paused = false;
        gameOver = false;
        bodyParts = 3;
        applesEaten = 0;
        receivedMessage = "";
        playerList.clear();
        direction = 'R';
        timer.stop();
        random = new Random();

        Arrays.fill(x, 0);
        Arrays.fill(y, 0);

        repaint();
        startGame();
    }

    // 暫停遊戲
    public void pause() {
        paused = true;
        repaint();
    }

    // 恢復遊戲
    public void resume() {
        paused = false;
    }

    // 繪製文字置中
    public void drawCenteredString(Graphics g, Font font, Color color, String text, int y) {
        g.setColor(color);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics(font);
        int x = (SCREEN_WIDTH - metrics.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }
}
