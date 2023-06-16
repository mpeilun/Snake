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

    private ExecutorService exec;
    private Socket clientSocket;
    private DataInputStream fromServer;
    private DataOutputStream toServer;

    private static final int SCREEN_WIDTH = 500;
    private static final int SCREEN_HEIGHT = 500;
    private static final int UNIT_SIZE = 10;
    private static final int GAME_UNITS = (SCREEN_WIDTH * SCREEN_HEIGHT) / UNIT_SIZE;
    private static final int DELAY = 55;

    private final int[] x = new int[GAME_UNITS];
    private final int[] y = new int[GAME_UNITS];
    private int bodyParts = 3;
    private int applesEaten = 0;
    private String receivedMessage;
    private List<Player> playerList = new ArrayList<>();
    private int appleX;
    private int appleY;
    private char direction = 'R';
    private boolean running = false;
    private boolean started = false;
    private boolean paused = false;
    private boolean gameOver = false;
    private Timer timer = new Timer(DELAY, this);
    private Random random;
    private Player player;

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
        updateScore(player, 0);
        newApple();
        running = true;

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

        timer.start();
    }

    // 產生新的蘋果
    public void newApple() {
        appleX = random.nextInt((int) (SCREEN_WIDTH / UNIT_SIZE)) * UNIT_SIZE;
        appleY = random.nextInt((int) (SCREEN_HEIGHT / UNIT_SIZE)) * UNIT_SIZE;
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        if (running && !paused) {
            move();
            checkApple();
            checkCollisions();
        }
        repaint();
    }

    // 鍵盤事件監聽器
    public class MyKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                    if (direction != 'R') {
                        direction = 'L';
                    }
                    break;

                case KeyEvent.VK_RIGHT:
                    if (direction != 'L') {
                        direction = 'R';
                    }
                    break;

                case KeyEvent.VK_UP:
                    if (direction != 'D') {
                        direction = 'U';
                    }
                    break;

                case KeyEvent.VK_DOWN:
                    if (direction != 'U') {
                        direction = 'D';
                    }
                    break;

                case KeyEvent.VK_SPACE:
                    if (paused) {
                        resume();
                    } else if (running) {
                        pause();
                    }
                    break;
                case KeyEvent.VK_ENTER:
                    if (!started) {
                        started = true;
                        startGame();
                    } else if (gameOver) {
                        started = false;
                        restartGame();
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
                y[0] -= UNIT_SIZE;
                break;
            case 'D':
                y[0] += UNIT_SIZE;
                break;
            case 'L':
                x[0] -= UNIT_SIZE;
                break;
            case 'R':
                x[0] += UNIT_SIZE;
                break;
        }
    }

    // 檢查是否吃到蘋果
    public void checkApple() {
        if ((x[0] == appleX) && (y[0] == appleY)) {
            bodyParts++;
            applesEaten++;
            updateScore(player, applesEaten);
            newApple();
        }
    }

    // 檢查碰撞
    public void checkCollisions() {
        for (int i = bodyParts; i > 0; i--) {
            if ((x[0] == x[i]) && (y[0] == y[i])) {
                running = false;
            }
        }
        if (x[0] < 0 || x[0] > SCREEN_WIDTH || y[0] < 0 || y[0] > SCREEN_HEIGHT) {
            running = false;
        }
        if (!running) {
            timer.stop();
            paused = false;
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    // 更新伺服器資料
    public void topdata(String receivedMessage) {
        Gson gson = new Gson();
        Type type = new TypeToken<List<Player>>() {
        }.getType();
        playerList = gson.fromJson(receivedMessage, type);
    }

    // 傳送玩家資料至伺服器
    public void sendMessage(Player player) {
        try {
            Gson gson = new Gson();
            String json = gson.toJson(player);
            System.out.println("Send: " + json);
            toServer.writeUTF(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 更新玩家分數
    public void updateScore(Player player, int score) {
        player.setScore(score);
        player.setBest(score);
        sendMessage(player);
    }

    // 繪製遊戲畫面
    public void draw(Graphics g) {
        if (!started) {
            drawStartScreen(g);
        } else if (running) {
            drawSnake(g);
            drawPlayerList(g);
            drawTopRank(g);
            if (paused) {
                drawPauseScreen(g);
            }
        } else {
            gameOver(g);
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
        g.fillOval(appleX, appleY, UNIT_SIZE, UNIT_SIZE);

        for (int i = 0; i < bodyParts; i++) {
            g.setColor(i == 0 ? Color.BLUE : Color.GREEN);
            g.fillRect(x[i], y[i], UNIT_SIZE, UNIT_SIZE);
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
        Collections.sort(playerList, scoreComparator);

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
        List<Player> topPlayers = playerList.stream().sorted(scoreComparator).limit(3).collect(Collectors.toList());

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
