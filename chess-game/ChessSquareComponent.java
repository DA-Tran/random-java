import java.awt.*;
import javax.swing.*;

public class ChessSquareComponent extends JButton {
    private int row;
    private int col;

    public ChessSquareComponent(int row, int col) {
        this.row = row;
        this.col = col;
        initButton();
    }

    private void initButton() {
        setPreferredSize(new Dimension(64, 64));
        if ((row + col) % 2 == 0) {
            setBackground(Color.LIGHT_GRAY);
        } else {
            setBackground(new Color(205, 133, 63)); // Brown
        }
        setHorizontalAlignment(SwingConstants.CENTER);
        setVerticalAlignment(SwingConstants.CENTER);
        setFont(new Font("Segoe UI Symbol", Font.BOLD, 44));

        setFocusPainted(false);
        setBorderPainted(false);
    }

    public void setPieceSymbol(String symbol, Color color) {
        setText(symbol);
        setForeground(color);
    }

    public void clearPieceSymbol() {
        setText("");
    }
}

