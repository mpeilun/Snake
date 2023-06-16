package niu.edu.b0943034;

public class Player {
    private String name;
    private int port;
    private int score;
    private int best;
    private boolean online;


    public Player(String name, int port, int score, int best, boolean online) {
        this.name = name;
        this.port = port;
        this.score = score;
        this.best = best;
        this.online = online;
    }

    public String getName() {
        return name;
    }

    public int getPort() {
        return port;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getBest() {
        return best;
    }

    public void setBest(int best) {
        this.best = best;
    }

    public boolean getOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}
