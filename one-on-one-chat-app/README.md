
# one-on-one-chat-app/: Multi-Client Chat (Console + Swing GUI + Server)\n\n**Purpose**: Local network chat - server broadcasts messages with name/timestamp to clients.\n\n**Files**:\n- **Server**: ChatServer.java (port 5000 localhost)\n- **Console Client**: ChatClient_text.java (input, exit)\n- **Swing Client**: ChatClient_interactive.java (GUI styled list, input field)\n- ChatClient.java (networking)\n\n**Run** (3 Git Bash terminals):\n1. `cd one-on-one-chat-app && export PATH=\"../openJdk-25/bin:$PATH\" && javac *.java && java ChatServer`\n2. `java ChatClient_text`\n3. `java ChatClient_interactive`\n\nSend msg → all see \"name (time): msg\", exit broadcasts bye.

## Java Chat App (Guide Implementation)

**Console (text)** + **Swing GUI (interactive)** + **Server** (multi-client broadcast).

**Files**:
- ChatServer.java - Server broadcasts messages
- ChatClient.java - Networking
- ChatClient_text.java - Console client (name, timestamp, exit)
- ChatClient_interactive.java - Swing GUI (styled, name/timestamp/exit)

**Run** (Git Bash, 3 terminals in one-on-one-chat-app):
```
export PATH=../openJdk-25/bin:$PATH
javac *.java
```

**Terminal 1** (Server):
```
java ChatServer
```

**Terminal 2** (Console Client):
```
java ChatClient_text
```

**Terminal 3** (GUI Client):
```
java ChatClient_interactive
```

**Test**: Name entry, send msgs → broadcast/timestamp, exit msg.

## Original JS Demo
```
start index.html
```
(WebSocket echo)

## Notes
- Localhost port 5000
- Full guide features (name, time, exit, styling)
- OpenJDK 25 Swing

