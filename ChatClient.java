package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class ChatClient extends JFrame {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    // GUI components
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton connectButton;
    private JButton disconnectButton;
    private JTextField usernameField;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    private boolean connected = false;

    public ChatClient() {
        initializeGUI();
    }

    private void initializeGUI() {
        setTitle("Chat Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        // Create components
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 12));
        chatArea.setBackground(Color.WHITE);

        messageField = new JTextField();
        messageField.setFont(new Font("Arial", Font.PLAIN, 12));
        messageField.addActionListener(e -> sendMessage());

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        sendButton.setEnabled(false);

        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> connect());

        disconnectButton = new JButton("Disconnect");
        disconnectButton.addActionListener(e -> disconnect());
        disconnectButton.setEnabled(false);

        usernameField = new JTextField("Enter username", 15);
        usernameField.setForeground(Color.GRAY);
        usernameField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (usernameField.getText().equals("Enter username")) {
                    usernameField.setText("");
                    usernameField.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (usernameField.getText().isEmpty()) {
                    usernameField.setText("Enter username");
                    usernameField.setForeground(Color.GRAY);
                }
            }
        });

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBackground(new Color(240, 240, 240));

        // Layout
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Username:"));
        topPanel.add(usernameField);
        topPanel.add(connectButton);
        topPanel.add(disconnectButton);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new JLabel("Online Users", SwingConstants.CENTER), BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        rightPanel.setPreferredSize(new Dimension(150, 0));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        // Add panels to frame
        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // Window closing event
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                System.exit(0);
            }
        });
    }

    private void connect() {
        String enteredUsername = usernameField.getText().trim();
        if (enteredUsername.isEmpty() || enteredUsername.equals("Enter username")) {
            JOptionPane.showMessageDialog(this, "Please enter a username", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send username to server
            String serverPrompt = in.readLine(); // "Enter your username:"
            out.println(enteredUsername);
            this.username = enteredUsername;

            connected = true;
            updateGUIState();

            // Start thread to listen for messages
            Thread messageListener = new Thread(this::listenForMessages);
            messageListener.setDaemon(true);
            messageListener.start();

            appendToChat("Connected to chat server as " + username);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to connect to server: " + e.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disconnect() {
        if (connected) {
            try {
                if (out != null) {
                    out.println("/quit");
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Error during disconnect: " + e.getMessage());
            }

            connected = false;
            updateGUIState();
            appendToChat("Disconnected from server");
            userListModel.clear();
        }
    }

    private void sendMessage() {
        if (connected && out != null) {
            String message = messageField.getText().trim();
            if (!message.isEmpty()) {
                out.println(message);
                messageField.setText("");
                messageField.requestFocus();
            }
        }
    }

    private void listenForMessages() {
        try {
            String message;
            while (connected && (message = in.readLine()) != null) {
                if (message.startsWith("USERLIST:")) {
                    updateUserList(message);
                } else if (message.startsWith("HELP:")) {
                    appendToChat(message);
                } else {
                    appendToChat(message);
                }
            }
        } catch (IOException e) {
            if (connected) {
                appendToChat("Connection lost: " + e.getMessage());
                connected = false;
                SwingUtilities.invokeLater(this::updateGUIState);
            }
        }
    }

    private void updateUserList(String userListMessage) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            String[] parts = userListMessage.split(":");
            if (parts.length > 1) {
                String[] users = parts[1].split(",");
                for (String user : users) {
                    if (!user.trim().isEmpty()) {
                        userListModel.addElement(user.trim());
                    }
                }
            }
        });
    }

    private void appendToChat(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private void updateGUIState() {
        connectButton.setEnabled(!connected);
        disconnectButton.setEnabled(connected);
        sendButton.setEnabled(connected);
        messageField.setEnabled(connected);
        usernameField.setEnabled(!connected);

        if (connected) {
            messageField.requestFocus();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            new ChatClient().setVisible(true);
        });
    }
}
