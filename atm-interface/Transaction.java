
public class Transaction {
    private String type;
    private double amount;
    private String timestamp;

    public Transaction(String type, double amount, String timestamp) {
        this.type = type;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getDetails() {
        return String.format("%s: $%.2f at %s", type, amount, timestamp);
    }
}

