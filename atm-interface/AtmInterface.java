
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class AtmInterface {
    private Bank bank;
    private Scanner scanner;
    private Account currentAccount;

    public AtmInterface() {
        bank = new Bank();
        scanner = new Scanner(System.in);
    }

    public void start() {
        System.out.println("=== ATM Interface ===");
        while (true) {
            showMenu();
            int choice = scanner.nextInt();
            scanner.nextLine();
            handleChoice(choice);
            if (currentAccount == null) System.out.println("Session ended. Goodbye!");
        }
    }

    private void showMenu() {
        System.out.println("\n1. Login");
        System.out.println("2. Exit");
        System.out.print("Choice: ");
    }

    private void handleChoice(int choice) {
        switch (choice) {
            case 1:
                login();
                break;
            case 2:
                System.exit(0);
            default:
                System.out.println("Invalid choice.");
        }
    }

    private void login() {
        System.out.print("Account Number: ");
        String accNum = scanner.nextLine();
        Account acc = bank.findAccount(accNum);
        if (acc != null) {
            System.out.print("PIN: ");
            String pin = scanner.nextLine();
            if (acc.validatePin(pin)) {
                currentAccount = acc;
                System.out.println("Logged in successfully!");
                showAccountMenu();
            } else {
                System.out.println("Invalid PIN.");
            }
        } else {
            System.out.println("Account not found.");
        }
    }

    private void showAccountMenu() {
        while (currentAccount != null) {
            System.out.println("\n=== Account Menu ===");
            System.out.printf("Balance: $%.2f\n", currentAccount.getBalance());
            System.out.println("1. Deposit");
            System.out.println("2. Withdraw");
            System.out.println("3. Balance");
            System.out.println("4. Logout");
            System.out.print("Choice: ");
            int choice = scanner.nextInt();
            scanner.nextLine();
            handleAccountChoice(choice);
        }
    }

    private void handleAccountChoice(int choice) {
        switch (choice) {
            case 1:
                System.out.print("Amount: ");
                double dep = scanner.nextDouble();
                if (currentAccount.deposit(dep)) {
                    printTransaction("DEPOSIT", dep);
                } else {
                    System.out.println("Invalid amount.");
                }
                scanner.nextLine();
                break;
            case 2:
                System.out.print("Amount: ");
                double with = scanner.nextDouble();
                if (currentAccount.withdraw(with)) {
                    printTransaction("WITHDRAWAL", with);
                } else {
                    System.out.println("Insufficient funds or invalid amount.");
                }
                scanner.nextLine();
                break;
            case 3:
                System.out.printf("Balance: $%.2f\n", currentAccount.getBalance());
                break;
            case 4:
                currentAccount = null;
                System.out.println("Logged out.");
                break;
            default:
                System.out.println("Invalid choice.");
        }
    }

    private void printTransaction(String type, double amount) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Transaction trans = new Transaction(type, amount, time);
        System.out.println(trans.getDetails());
    }

    public static void main(String[] args) {
        new AtmInterface().start();
    }
}

