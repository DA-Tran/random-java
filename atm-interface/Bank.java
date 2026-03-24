
import java.util.HashMap;
import java.util.Map;

public class Bank {
    private Map<String, Account> accounts;

    public Bank() {
        accounts = new HashMap<>();
        // Demo accounts
        accounts.put("123456", new Account("123456", "0000", 1000.0));
        accounts.put("789012", new Account("789012", "1111", 500.0));
    }

    public Account findAccount(String accountNumber) {
        return accounts.get(accountNumber);
    }
}

