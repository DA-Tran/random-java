import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.text.DecimalFormat;

public class AtmFXApp extends Application {
    private Account account = new Account("123456", "0000", 1000.0);
    private Label balanceLabel;
    private TextField amountField;
    private DecimalFormat df = new DecimalFormat("#.00");

    @Override
    public void start(Stage stage) {
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #416680, #c0c0c0);");

        // Header
        Label header = new Label("🏧 ATM MACHINE");
        header.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: white;");

        // Balance
        balanceLabel = new Label("Balance: $" + df.format(account.getBalance()));
        balanceLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-background-color: white; -fx-padding: 20;");
        balanceLabel.setMinWidth(300);

        // Amount input
        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER);
        inputBox.getChildren().addAll(new Label("Amount: $"), amountField = new TextField());

        // Buttons
        VBox buttons = new VBox(10);
        Button deposit = new Button("💰 Deposit");
        Button withdraw = new Button("💳 Withdraw");
        Button refresh = new Button("🔄 Refresh");
        Button exit = new Button("🚪 Exit");

        deposit.setPrefSize(200, 50);
        deposit.setStyle("-fx-font-size: 16; -fx-background-color: #2ecc71;");
        withdraw.setStyle("-fx-font-size: 16; -fx-background-color: #e74c3c;");
        refresh.setStyle("-fx-font-size: 16; -fx-background-color: #3498db;");
        exit.setStyle("-fx-font-size: 16; -fx-background-color: #95a5a6;");
        deposit.setTextFill(Color.WHITE);
        withdraw.setTextFill(Color.WHITE);
        refresh.setTextFill(Color.WHITE);
        exit.setTextFill(Color.WHITE);

        deposit.setOnAction(e -> handleDeposit());
        withdraw.setOnAction(e -> handleWithdraw());
        refresh.setOnAction(e -> updateBalance());
        exit.setOnAction(e -> stage.close());

        buttons.getChildren().addAll(deposit, withdraw, refresh, exit);

        root.getChildren().addAll(header, balanceLabel, inputBox, buttons);
        Scene scene = new Scene(root, 400, 500);
        stage.setScene(scene);
        stage.setTitle("ATM - JavaFX");
        stage.show();
        updateBalance();
    }

    private void handleDeposit() {
        try {
            double amount = Double.parseDouble(amountField.getText());
            if (account.deposit(amount)) {
                showAlert("Success", "Deposited $" + df.format(amount));
            } else {
                showAlert("Error", "Invalid amount!", Alert.AlertType.ERROR);
            }
        } catch (NumberFormatException ex) {
            showAlert("Error", "Enter valid number!", Alert.AlertType.ERROR);
        }
        amountField.clear();
        updateBalance();
    }

    private void handleWithdraw() {
        try {
            double amount = Double.parseDouble(amountField.getText());
            if (account.withdraw(amount)) {
                showAlert("Success", "Withdrew $" + df.format(amount));
            } else {
                showAlert("Error", "Insufficient funds!", Alert.AlertType.ERROR);
            }
        } catch (NumberFormatException ex) {
            showAlert("Error", "Enter valid number!", Alert.AlertType.ERROR);
        }
        amountField.clear();
        updateBalance();
    }

    private void showAlert(String title, String msg) {
        showAlert(title, msg, Alert.AlertType.INFORMATION);
    }

    private void showAlert(String title, String msg, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();
    }

    private void updateBalance() {
        balanceLabel.setText("Balance: $" + df.format(account.getBalance()));
    }

    public static void main(String[] args) {
        launch(args);
    }
}

