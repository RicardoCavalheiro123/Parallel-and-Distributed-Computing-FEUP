package main.game;

import main.server.Server;
import main.utils.ConcurrentList;
import main.utils.MessageType;

import java.util.*;

import main.utils.ConcurrentHashMap;

import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Game implements Runnable {
    private final int id;
    private final boolean ranked;
    private final int secretNumber;
    private final ConcurrentList<Player> players;
    private static final int MAX_RANGE = 100;
    private static final int MIN_RANGE = 1;
    private final int MAX_ROUNDS = 3;
    private int GAME_ROUND;
    private final ConcurrentHashMap<Player, Integer> playerGuesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Lock> playerLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Condition> playerConditions = new ConcurrentHashMap<>();

    private final ExecutorService threadPoolPlayers;


    public Game(int id, ConcurrentList<Player> players, boolean ranked, ExecutorService executorService) {
        this.id = id;
        this.ranked = ranked;
        this.secretNumber = generateSecretNumber();
        this.players = players;
        this.threadPoolPlayers = executorService;
        this.GAME_ROUND = 1;
    }

    public int getId() {
        return id;
    }

    private static int generateSecretNumber() {
        return new Random().nextInt(MAX_RANGE - MIN_RANGE + 1) + MIN_RANGE;
    }

    public int getSecretNumber() {
        return secretNumber;
    }

    public ConcurrentList<Player> getPlayers() {
        return players;
    }

    public boolean isOver() {
        return allPlayersGuessed();
    }

    public void setPlayerGuess(Player player, int guess) {
        playerGuesses.put(player, guess);
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public boolean allPlayersGuessed() {
        for (Player player : players) {
            if (!playerGuessed(player)) {
                return false;
            }
        }
        return true;
    }

    public boolean playerGuessed(Player player) {
        return !player.isInGame() || playerGuesses.containsKey(player);
    }

    public void guess(Player player, int guess) {
        playerGuesses.put(player, guess);
        int distance = getDistance(player);
        if(ranked) player.updateScore(distance != 0 ? MAX_RANGE / 2 - distance : 100);
    }

    public int getDistance(Player player) {
        return Math.abs(playerGuesses.getOrDefault(player, -1) - getSecretNumber());
    }

    public int getMaxRange() {
        return MAX_RANGE;
    }

    public int getMinRange() {
        return MIN_RANGE;
    }

    @Override
    public void run() {

        while(GAME_ROUND <= MAX_ROUNDS){
            String message = "";
            if(GAME_ROUND == 1) message = "Game started!";
            System.out.println("Game " + getId() + " Round " + GAME_ROUND);
            Server.sendMessageToPlayers(this, MessageType.INFO.toHeader() + "Round " + GAME_ROUND);
            Server.sendMessageToPlayers(this, MessageType.GAME_GUESS_REQUEST.toHeader() + message + " Guess a number between " + getMinRange() + " and " + getMaxRange());

            List<Callable<Void>> tasks = new ArrayList<>();
            for (Player player : players) {
                if (player == null || !player.isInGame()) {
                    continue;
                }
                Callable<Void> task = () -> {
                    while (!allPlayersGuessed() && player.isInGame()) { // removed && !playerGuessed(player) because most times the "Waiting..." message was not printing"
                        waitForGuess(player);
                        if (playerGuessed(player) && player.isInGame()) { //playerGuessed will return true for disconnected players
                            System.out.println("Player " + player.getUsername() + " has guessed.");
                            int guess = playerGuesses.get(player);
                            guess(player, guess);

                            if (!allPlayersGuessed()) {
                                Server.sendMessageToPlayer(player, MessageType.INFO.toHeader() +"Waiting for other players to guess...");
                                break;
                            }
                        }
                    }
                    return null;

                };
                tasks.add(task);
            }

            try {
                List<Future<Void>> futures = threadPoolPlayers.invokeAll(tasks);
                for (Future<Void> future : futures) {
                    future.get(); // This blocks and waits for the task to complete
                }

                // All Players have guessed.
                System.out.println("All Players have guessed.");
                Server.sendMessageToPlayers(this, MessageType.INFO.toHeader() + "All players have guessed! The secret number was " + getSecretNumber());

                for (Player p: players) {
                    int distance = getDistance(p);
                    if (distance == 0) {
                        Server.sendMessageToPlayer(p, MessageType.INFO.toHeader() + "You guessed the secret number!");
                        Server.sendMessageToPlayers(this, MessageType.INFO.toHeader() + "Player " + p.getUsername() + " guessed the secret number!");
                    }
                    Server.sendMessageToPlayer(p,MessageType.INFO.toHeader() +"Your guess was " + distance + " away from the secret number");
                    if(ranked) Server.sendMessageToPlayer(p,MessageType.INFO.toHeader() + "Your score is " + p.getScore());
                }

            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            GAME_ROUND++;
            for(Player p: players){
                playerGuesses.remove(p);
            }
        }
    }

    private void waitForGuess(Player player) {
        Lock lock = playerLocks.get(player);
        if (lock == null) {
            lock = new ReentrantLock();
            playerLocks.put(player, lock);
        }

        Condition condition = lock.newCondition();
        playerConditions.put(player, condition);

        lock.lock();
        try {
            while (!playerGuessed(player)) {
                condition.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    public void signalGuessReceived(Player player) {
        Lock lock = playerLocks.get(player);
        if (lock != null) {
            lock.lock();
            try {
                Condition condition = playerConditions.get(player);
                if (condition != null) {
                    condition.signal();
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public void signalDisconnected(Player player) {
        player.setInGame(false);
        players.remove(player);
        signalGuessReceived(player);
    }

    public ExecutorService getThreadPoolPlayers() {
        return threadPoolPlayers;
    }
}