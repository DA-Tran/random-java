
# ATM Interface (Java Console)

## Files
- AtmInterface.java - Main UI/menu
- Account.java - Account logic
- Bank.java - Accounts storage
- Transaction.java - Transaction records

Demo accounts:
- Acc: 123456, PIN: 0000, Bal: $1000
- Acc: 789012, PIN: 1111, Bal: $500

## Run Console
```bash
cd atm-interface
export PATH="../openJdk-25/bin:$PATH"
javac *.java
java AtmInterface
```

## Run Swing GUI (Legacy)
```bash
cd atm-interface
export PATH="../openJdk-25/bin:$PATH"
javac Account.java AtmGUI.java
java AtmGUI
```

## Run JavaFX GUI (Interactive)
```bash
cd atm-interface
export PATH="../openJdk-25/bin:$PATH"
cd javafx
javac *.java
java --module-path "../../openJdk-25/jmods/javafx.controls.jmod:../../openJdk-25/jmods/javafx.fxml.jmod:../../openJdk-25/jmods/javafx.graphics.jmod:../../openJdk-25/jmods/javafx.base.jmod" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base AtmFXApp
```

**JavaFX**: Styled buttons, gradients, alerts, modern UI.

