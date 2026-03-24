
const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');
const scoreElement = document.getElementById('score');

const paddle = { x: canvas.width / 2 - 60, y: canvas.height - 30, width: 120, height: 20, speed: 8 };
const ball = { x: canvas.width / 2, y: canvas.height - 50, radius: 8, dx: 4, dy: -4 };
const bricks = [];
const brickRowCount = 5;
const brickColCount = 10;
const brickWidth = 70;
const brickHeight = 25;
const brickPadding = 5;
const brickOffsetTop = 50;
const brickOffsetLeft = 20;

let score = 0;
let gameRunning = false;
let keys = {};

canvas.addEventListener('mousemove', (e) => {
    const rect = canvas.getBoundingClientRect();
    paddle.x = e.clientX - rect.left - paddle.width / 2;
    if (paddle.x < 0) paddle.x = 0;
    if (paddle.x + paddle.width > canvas.width) paddle.x = canvas.width - paddle.width;
});

document.addEventListener('keydown', (e) => keys[e.code] = true);
document.addEventListener('keyup', (e) => keys[e.code] = false);

function createBricks() {
    bricks.length = 0;
    for (let r = 0; r < brickRowCount; r++) {
        for (let c = 0; c < brickColCount; c++) {
            bricks.push({
                x: c * (brickWidth + brickPadding) + brickOffsetLeft,
                y: r * (brickHeight + brickPadding) + brickOffsetTop,
                width: brickWidth,
                height: brickHeight,
                visible: true
            });
        }
    }
}

function collisionDetection() {
    bricks.forEach((brick, index) => {
        if (brick.visible && ball.x > brick.x && ball.x < brick.x + brick.width && ball.y > brick.y && ball.y < brick.y + brick.height) {
            ball.dy = -ball.dy;
            brick.visible = false;
            score += 10;
            scoreElement.textContent = score;
        }
    });
}

function drawPaddle() {
    ctx.fillStyle = '#4CAF50';
    ctx.fillRect(paddle.x, paddle.y, paddle.width, paddle.height);
}

function drawBall() {
    ctx.beginPath();
    ctx.arc(ball.x, ball.y, ball.radius, 0, Math.PI * 2);
    ctx.fillStyle = '#FF5722';
    ctx.fill();
    ctx.closePath();
}

function drawBricks() {
    ctx.fillStyle = '#2196F3';
    bricks.forEach(brick => {
        if (brick.visible) {
            ctx.fillRect(brick.x, brick.y, brick.width, brick.height);
        }
    });
}

function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    drawBricks();
    drawBall();
    drawPaddle();
    collisionDetection();

    // Ball wall collision
    if (ball.x + ball.dx > canvas.width - ball.radius || ball.x + ball.dx < ball.radius) ball.dx = -ball.dx;
    if (ball.y + ball.dy < ball.radius) ball.dy = -ball.dy;
    else if (ball.y + ball.dy > canvas.height - ball.radius) {
        if (ball.x > paddle.x && ball.x < paddle.x + paddle.width) {
            ball.dy = -Math.abs(ball.dy);
        } else {
            alert('Game Over! Score: ' + score);
            document.location.reload();
        }
    }

    ball.x += ball.dx;
    ball.y += ball.dy;

    // Win condition
    if (bricks.every(b => !b.visible)) {
        alert('You Win! Score: ' + score);
        document.location.reload();
    }
}

function update() {
    if (keys['Space']) {
        gameRunning = !gameRunning;
        keys['Space'] = false; // Prevent repeat
    }
    if (gameRunning) draw();
    requestAnimationFrame(update);
}

createBricks();
update();

