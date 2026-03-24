import java.util.Scanner;

public class ChatClient_text {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your name: ");
        String name = scanner.nextLine();
        
        try {
            ChatClient client = new ChatClient("127.0.0.1", 5000, message -> {
                System.out.println(message);
            });
            
            System.out.println("Connected. Type 'exit' to quit.");
            
            client.startClient();
            
            // Send messages loop
            String line;
            while (!(line = scanner.nextLine()).equals("exit")) {
                String msg = "[" + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + name + ": " + line;
                client.sendMessage(msg);
            }
            
            client.sendMessage(name + " has left the chat.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
