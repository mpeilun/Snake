package niu.edu.b0943034;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;

public class Main {
    public static void main(String[] args) throws IOException, URISyntaxException {
        int port = 8888;

        if (args.length > 0 && args[0].equals("server")) {
            GameServer server = new GameServer(port);  // 啟動伺服器
        } else {
            GameFrame client = new GameFrame(); // 啟動客戶端
        }
    }
}
