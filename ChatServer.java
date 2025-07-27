package org.example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private static ExecutorService executor = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        System.out.println("Chat Server starting on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                executor.submit(clientHandler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    // Broadcast message to all connected clients
    public static void broadcast(String message, ClientHandler sender) {
        synchronized (clients) {
            Iterator<ClientHandler> iterator = clients.iterator();
            while (iterator.hasNext()) {
                ClientHandler client = iterator.next();
                if (client != sender) {
                    if (!client.sendMessage(message)) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    // Remove disconnected client
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Client disconnected. Active clients: " + clients.size());
    }

    // Get list of connected clients
    public static void broadcastUserList() {
        StringBuilder userList = new StringBuilder("USERLIST:");
        synchronized (clients) {
            for (ClientHandler client : clients) {
                userList.append(client.getUsername()).append(",");
            }
        }

        String userListMessage = userList.toString();
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(userListMessage);
            }
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Get username from client
            out.println("Enter your username:");
            username = in.readLine();

            if (username == null || username.trim().isEmpty()) {
                username = "Anonymous_" + socket.getInetAddress().toString().hashCode();
            }

            System.out.println("User " + username + " joined the chat");

            // Notify all clients about new user
            ChatServer.broadcast("SERVER: " + username + " joined the chat", this);
            ChatServer.broadcastUserList();

            String message;
            while ((message = in.readLine()) != null) {
                if (message.equalsIgnoreCase("/quit")) {
                    break;
                }

                // Handle special commands
                if (message.startsWith("/")) {
                    handleCommand(message);
                } else {
                    // Broadcast regular message
                    String formattedMessage = username + ": " + message;
                    System.out.println(formattedMessage);
                    ChatServer.broadcast(formattedMessage, this);
                }
            }

        } catch (IOException e) {
            System.err.println("Error handling client " + username + ": " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handleCommand(String command) {
        if (command.equalsIgnoreCase("/users")) {
            ChatServer.broadcastUserList();
        } else if (command.equalsIgnoreCase("/help")) {
            sendMessage("HELP: Available commands: /users, /help, /quit");
        } else {
            sendMessage("SERVER: Unknown command. Type /help for available commands.");
        }
    }

    public boolean sendMessage(String message) {
        try {
            out.println(message);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void disconnect() {
        try {
            if (username != null) {
                System.out.println("User " + username + " left the chat");
                ChatServer.broadcast("SERVER: " + username + " left the chat", this);
            }

            ChatServer.removeClient(this);
            ChatServer.broadcastUserList();

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing client socket: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username != null ? username : "Unknown";
    }
}
