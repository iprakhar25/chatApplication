package server;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int PORT = 12345;
    private static Map<String, PrintWriter> clientWriters = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("Server started and listening on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Request a unique client name
                out.println("Enter your name:");
                clientName = in.readLine();
                synchronized (clientWriters) {
                    if (clientName == null || clientWriters.containsKey(clientName)) {
                        out.println("Name is invalid or already taken. Disconnecting...");
                        socket.close();
                        return;
                    }
                    clientWriters.put(clientName, out);
                }

                System.out.println(clientName + " has connected.");
                broadcast("Server: " + clientName + " has joined the chat.", null);

                // Read messages from this client
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("@")) {
                        sendPrivateMessage(message);
                    } else {
                        broadcast(clientName + ": " + message, clientName);
                    }
                }
            } catch (IOException e) {
                System.out.println(clientName + " disconnected unexpectedly.");
            } finally {
                disconnectClient();
            }
        }

        private void broadcast(String message, String excludeClient) {
            synchronized (clientWriters) {
                for (Map.Entry<String, PrintWriter> entry : clientWriters.entrySet()) {
                    if (excludeClient != null && entry.getKey().equals(excludeClient)) {
                        continue; // Skip sending to the excluded client
                    }
                    entry.getValue().println(message);
                }
            }
        }

        private void sendPrivateMessage(String message) {
            int spaceIndex = message.indexOf(' ');
            if (spaceIndex != -1) {
                String targetName = message.substring(1, spaceIndex);
                String privateMessage = message.substring(spaceIndex + 1);

                synchronized (clientWriters) {
                    PrintWriter targetOut = clientWriters.get(targetName);
                    if (targetOut != null) {
                        targetOut.println("[Private] " + clientName + ": " + privateMessage);
                    } else {
                        out.println("User " + targetName + " is not available.");
                    }
                }
            } else {
                out.println("Invalid format. Use @<name> <message> to send a private message.");
            }
        }

        private void disconnectClient() {
            if (clientName != null) {
                System.out.println(clientName + " has disconnected.");
                synchronized (clientWriters) {
                    clientWriters.remove(clientName);
                }
                broadcast("Server: " + clientName + " has left the chat.", null);
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
