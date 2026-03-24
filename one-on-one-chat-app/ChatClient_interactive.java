import java.awt.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;

public class ChatClient_interactive extends JFrame {
    private JTextArea messageArea;
    private JTextField textField;
    private JButton exitButton;
    private ChatClient client;
    private String name;

    public ChatClient_interactive() {
        super("Chat Application");
        setSize(400, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Styling
        Color backgroundColor = new Color(240, 240, 240);
        Color buttonColor = new Color(75, 75, 75);
        Color textColor = new Color(50, 50, 50);
        Font textFont = new Font("Arial", Font.PLAIN, 14);
        Font buttonFont = new Font("Arial", Font.BOLD, 12);

        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setBackground(backgroundColor);
        messageArea.setForeground(textColor);
        messageArea.setFont(textFont);
        JScrollPane scrollPane = new JScrollPane(messageArea);
        add(scrollPane, BorderLayout.CENTER);

        // Name entry
        name = JOptionPane.showInputDialog(this, "Enter your name:", "Name Entry", JOptionPane.PLAIN_MESSAGE);
        setTitle("Chat Application - " + name);

        textField = new JTextField();
        textField.setFont(textFont);
        textField.setForeground(textColor);
        textField.setBackground(backgroundColor);
        textField.addActionListener(e -> {
            String msgText = textField.getText().trim();
            if (!msgText.isEmpty()) {
                String message = "[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + name + ": " + msgText;
                client.sendMessage(message);
                textField.setText("");
            }
        });

        exitButton = new JButton("Exit");
        exitButton.setFont(buttonFont);
        exitButton.setBackground(buttonColor);
        exitButton.setForeground(Color.WHITE);
        exitButton.addActionListener(e -> {
            client.sendMessage(name + " has left the chat.");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        });

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(backgroundColor);
        bottomPanel.add(textField, BorderLayout.CENTER);
        bottomPanel.add(exitButton, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // Connect
        try {
            this.client = new ChatClient("127.0.0.1", 5000, this::onMessageReceived);
            client.startClient();
            messageArea.append("Connected to server.\n");
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error connecting to server", "Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        setVisible(true);
    }

    private void onMessageReceived(String message) {
        SwingUtilities.invokeLater(() -> {
            messageArea.append(message + "\n");
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClient_interactive::new);
    }
}
