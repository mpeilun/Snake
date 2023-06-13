package niu.edu.b0943034;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.*;
import java.util.concurrent.*;

import javax.swing.*;

public class GameServer extends JFrame {
    private JTextArea txt = new JTextArea("伺服器運行中...\n");
    private ServerSocket serverSocket = null;
    private static Map<Socket, DataOutputStream> list = new LinkedHashMap<>();
    private ExecutorService exec = null;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public GameServer(int port) throws IOException, SocketException {
        txt.setEditable(false);
        txt.append("執行於 port:" + port + "...\n");

        setLayout(new BorderLayout());
        this.add(new JScrollPane(txt), BorderLayout.CENTER);
        setSize(500, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);

        serverSocket = new ServerSocket(port);
        exec = Executors.newCachedThreadPool();

        while (true) {
            Socket socket = serverSocket.accept();
            DataOutputStream toClient = new DataOutputStream(socket.getOutputStream());
            list.put(socket, toClient);
            exec.execute(new Communication(socket, list, txt, sdf));
        }
    }

    public static class Communication implements Runnable {
        private Socket socket;
        private DataInputStream fromClient;
        private String msg;
        private final Map<Socket, DataOutputStream> list;
        private final JTextArea txt;
        private final SimpleDateFormat sdf;

        public Communication(Socket socket, Map<Socket, DataOutputStream> list, JTextArea txt, SimpleDateFormat sdf) throws IOException ,SocketException{
            this.socket = socket;
            this.list = list;
            this.txt = txt;
            this.sdf = sdf;
            fromClient = new DataInputStream(socket.getInputStream());
            msg = "[" + socket.getPort() + "]:0";
            txt.append("[" + sdf.format(new Date()) + "]" + msg + "\n");
            sendMessage();
        }

        @Override
        public void run() {
            try {
                while ((msg = fromClient.readUTF()) != null) {
                    msg =  "[" + socket.getPort() + "]:" + msg;
                    txt.append("[" + sdf.format(new Date()) + "]"  + msg + "\n");
                    sendMessage();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        public void sendMessage() {
            Iterator<Entry<Socket, DataOutputStream>> it = list.entrySet().iterator();
            while (it.hasNext()) {
                Entry<Socket, DataOutputStream> entry = it.next();
                DataOutputStream toClient = entry.getValue();
                try {
                    toClient.writeUTF(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
