package main.game;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import static main.utils.Helper.MESSAGE_TERMINATOR;

public class Player {
    private final int id;
    private int score;
    private String username = "";
    private String sessionToken;
    private int gamesPlayed;
    boolean absent = false;
    private SocketChannel socketChannel;
    private Timer waitTimer;
    private int waitingTime = 0;
    private boolean inQueue = false;
    private boolean inGame = false;
    private boolean authenticated = false;

    private boolean updated = false;

    public Player(int id, SocketChannel socketChannel) {
        this.id = id;
        this.score = 0;
        this.socketChannel = socketChannel;
    }

    public Player(int id, int score, SocketChannel socketChannel) {
        this.id = id;
        this.score = score;
        this.socketChannel = socketChannel;
    }

    public Player(int id, String username, SocketChannel socketChannel) {
        this.id = id;
        this.username = username;
        this.score = 0;
        this.socketChannel = socketChannel;
    }

    public Player(int id, String username, int score, SocketChannel socketChannel) {
        this.id = id;
        this.username = username;
        this.score = score;
        this.socketChannel = socketChannel;
    }

    public int getId() {
        return id;
    }
    public int getScore() {
        return score;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void setSocketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void updateScore(int points) {
        this.updated = true;
        this.score += points;
        if (this.score < 0) this.score = 0;
    }

    public void setGamesPlayed(int gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
    }

    public int getGamesPlayed() {
        return this.gamesPlayed;
    }

    public void setAbsent(boolean b) {
        absent = b;
    }

    public boolean getAbsent() {
        return absent;
    }

    public boolean getUpdated() {
        return updated;
    }

    public void setUpdated(boolean updated) {
        this.updated = updated;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Player other = (Player) obj;
        if (this.id != other.id) {
            return false;
        }
        return this.socketChannel == other.socketChannel;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + this.id;
        hash = 59 * hash + Objects.hashCode(this.socketChannel);
        return hash;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void startWaitTimer() {
        waitTimer = new Timer();
        setInQueue(true);
        waitTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                waitingTime++;
                //System.out.println("Waiting time: " + waitingTime + " seconds");
            }
        }, 0, 1000); // 1000 milliseconds = 1 second
    }

    public void stopWaitTimer() {
        if (waitTimer != null) {
            waitTimer.cancel();
            waitTimer = null;
        }
        setInQueue(false);
    }

    public int getWaitingTime() {
        return waitingTime;
    }

    public void setInQueue(boolean inQueue) {
        this.inQueue = inQueue;
        waitingTime = 0;
    }

    public boolean isInQueue() {
        return inQueue;
    }

    public void setInGame(boolean inGame) {
        this.inGame = inGame;
    }
    public boolean isInGame() {
        return inGame;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }


    public void notifyGameOver() {
        this.setInGame(false);
        this.gamesPlayed++;
    }

    public void notifyGameStart() {
        this.stopWaitTimer();
        this.setInGame(true);
    }

    public int askToGuess() {
        // Read the guess from the player
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer = ByteBuffer.allocate(1024);
        try {
            socketChannel.read(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        buffer.flip();
        String guess = new String(buffer.array()).trim();
        int number = Integer.parseInt(guess);

        return number;
    }

    public void receiveMessage(String message) {
        System.out.println("Sent message to player " + getUsername() + ": " + message);
        if (message.equals("startWaitingTimer")) {
            startWaitTimer();
        } else if (message.equals("stopWaitingTimer")) {
            stopWaitTimer();
        } else if (message.equals("Make a guess: ")) {
            setInGame(true);

            // Send the message to the player
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            buffer.put(message.getBytes());
            buffer.put((byte) MESSAGE_TERMINATOR);
            buffer.flip();
            try {
                socketChannel.write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // TODO: Handle other message types
            // Output the message to the player
        }
    }

    @Override
    public String toString() {
        return "Player{" +
                "id=" + id +
                ", score=" + score +
                ", username='" + username + '\'' +
                ", sessionToken='" + sessionToken + '\'' +
                ", gamesPlayed=" + gamesPlayed +
                ", socketChannel=" + socketChannel +
                ", waitTimer=" + waitTimer +
                ", waitingTime=" + waitingTime +
                ", inQueue=" + inQueue +
                ", authenticated=" + authenticated +
                '}';
    }
}
