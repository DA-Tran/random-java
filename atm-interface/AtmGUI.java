
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;

public class AtmGUI extends JFrame {
private Account account = new Account("123456", "0000", 1000.0);
    private JLabel balanceLabel;
    private JTextField amountField;
    private DecimalFormat df = new DecimalFormat("#.00");

    public AtmGUI() {
        setTitle("ATM Interface GUI");
        setSize(400, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel();
        header.setBackground(new Color(41, 128, 185));
        header.add(new JLabel("🏧 ATM MACHINE", JLabel.CENTER)).setForeground(Color.WHITE).setFont(new Font("Arial", Font.BOLD, 24));
        add(header, BorderLayout.NORTH);

        // Balance
        balanceLabel = new JLabel("Balance: $" + df.format(account.getBalance()), JLabel.CENTER);
        balanceLabel.setFont(new Font("Arial", Font.BOLD, 28));
        balanceLabel.setOpaque(true);
        balanceLabel.setBackground(Color.WHITE);
        balanceLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        add(balanceLabel, BorderLayout.CENTER);

        // Amount input
        JPanel inputPanel = new JPanel(new FlowLayout());
        inputPanel.add(new JLabel("Amount: $"));
        amountField = new JTextField(10);
        inputPanel.add(amountField);
        add(inputPanel, BorderLayout.NORTH);

        // Buttons
        JPanel buttonPanel = new JPanel(new GridLayout(4, 1, 10, 10));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        JButton depositBtn = new JButton("💰 Deposit");
        JButton withdrawBtn = new JButton("💳 Withdraw");
        JButton balanceBtn = new JButton("🔄 Balance");
        JButton exitBtn = new JButton("🚪 Exit");

        depositBtn.setFont(new Font("Arial", Font.BOLD, 18));
        withdrawBtn.setFont(new Font("Arial", Font.BOLD, 18));
        balanceBtn.setFont(new Font("Arial", Font.BOLD, 18));
        exitBtn.setFont(new Font("Arial", Font.BOLD, 18));

        depositBtn.setBackground(new Color(46, 204, 113));
        withdrawBtn.setBackground(new Color(231, 76, 60));
        balanceBtn.setBackground(new Color(52, 152, 219));
        exitBtn.setBackground(new Color(149, 165, 166));

        depositBtn.setForeground(Color.WHITE);
        withdrawBtn.setForeground(Color.WHITE);
        balanceBtn.setForeground(Color.WHITE);
        exitBtn.setForeground(Color.WHITE);

        depositBtn.addActionListener(e -> processDeposit());
        withdrawBtn.addActionListener(e -> processWithdraw());
        balanceBtn.addActionListener(e -> updateBalance());
        exitBtn.addActionListener(e -> System.exit(0));

        buttonPanel.add(depositBtn);
        buttonPanel.add(withdrawBtn);
        buttonPanel.add(balanceBtn);
        buttonPanel.add(exitBtn);

        add(buttonPanel, BorderLayout.SOUTH);

        updateBalance();
        setVisible(true);
    }

    private void processDeposit() {
        try {
            double amount = Double.parseDouble(amountField.getText());
if (account.deposit(amount)) {
                JOptionPane.showMessageDialog(this, "Deposited $" + df.format(amount));
            } else {
                JOptionPane.showMessageDialog(this, "Invalid amount!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter valid number!", "Error", JOptionPane.ERROR_MESSAGE);
        }
        amountField.setText("");
        updateBalance();
    }

    private void processWithdraw() {
        try {
            double amount = Double.parseDouble(amountField.getText());
if (account.withdraw(amount)) {
                JOptionPane.showMessageDialog(this, "Withdrew $" + df.format(amount));
            } else {
                JOptionPane.showMessageDialog(this, "Invalid amount or insufficient funds!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter valid number!", "Error", JOptionPane.ERROR_MESSAGE);
        }
        amountField.setText("");
        updateBalance();
    }

    private void updateBalance() {
        balanceLabel.setText("Balance: $" + df.format(account.getBalance()));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AtmGUI::new);
    }
}

