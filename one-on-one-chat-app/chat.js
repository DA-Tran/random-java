
const status = document.getElementById('status');
const messages = document.getElementById('messages');
const usernameInput = document.getElementById('username');
const messageInput = document.getElementById('message');
const connectBtn = document.getElementById('connect');
const sendBtn = document.getElementById('send');

let ws = null;
let username = '';
let roomId = 'room1'; // Simple room for demo

connectBtn.onclick = connect;
sendBtn.onclick = sendMessage;
messageInput.onkeypress = (e) => {
    if (e.key === 'Enter') sendMessage();
};

function connect() {
    username = usernameInput.value.trim() || 'Anonymous';
    const wsUrl = `wss://echo.websocket.org/?username=${encodeURIComponent(username)}`; // Public echo for demo
    // For true P2P/one-on-one, use signaling server, here echo demo multi-tab/browser
    
    ws = new WebSocket(wsUrl);
    
    ws.onopen = () => {
        status.textContent = `Connected as ${username}`;
        status.className = 'connected';
        connectBtn.disabled = true;
        sendBtn.disabled = false;
        messageInput.disabled = false;
        addMessage('System', 'Connected to chat server. Open another tab/browser for 1:1 simulation.');
    };
    
    ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        const sender = data.username || 'Server';
        addMessage(sender, data.message);
    };
    
    ws.onclose = () => {
        status.textContent = 'Disconnected';
        status.className = 'disconnected';
        connectBtn.disabled = false;
        sendBtn.disabled = true;
        messageInput.disabled = true;
        ws = null;
    };
    
    ws.onerror = (error) => {
        addMessage('Error', 'Connection failed');
        console.error(error);
    };
}

function sendMessage() {
    const message = messageInput.value.trim();
    if (!message || !ws || ws.readyState !== WebSocket.OPEN) return;
    
    const payload = {
        username,
        roomId,
        message,
        timestamp: Date.now()
    };
    
    ws.send(JSON.stringify(payload));
    messageInput.value = '';
}

function addMessage(sender, text) {
    const div = document.createElement('div');
    div.className = sender === username ? 'message sent' : 'message received';
    div.innerHTML = `<strong>${sender}:</strong> ${escapeHtml(text)}`;
    messages.appendChild(div);
    messages.scrollTop = messages.scrollHeight;
}

function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '<',
        '>': '>',
        '"': '"',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, m => map[m]);
}

