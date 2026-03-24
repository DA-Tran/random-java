
const canvas = document.getElementById('chartCanvas');
const ctx = canvas.getContext('2d');
const generateBtn = document.getElementById('generate-data');
const chartType = document.getElementById('chart-type');
const animateBtn = document.getElementById('animate');
const datasetSize = document.getElementById('dataset-size');
const avgSpan = document.getElementById('avg');
const maxSpan = document.getElementById('max');

let data = [];
let animationId;
let isAnimating = false;

function generateRandomData() {
    const size = Math.floor(Math.random() * 50) + 20;
    data = [];
    for (let i = 0; i < size; i++) {
        data.push(Math.random() * 100);
    }
    updateStats();
}

function updateStats() {
    datasetSize.textContent = data.length;
    if (data.length > 0) {
        const sum = data.reduce((a, b) => a + b, 0);
        avgSpan.textContent = sum / data.length.toFixed(2);
        maxSpan.textContent = Math.max(...data).toFixed(2);
    }
}

function drawBarChart() {
    const barWidth = canvas.width / data.length;
    const maxValue = Math.max(...data);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    data.forEach((value, i) => {
        const barHeight = (value / maxValue) * (canvas.height - 100);
        const hue = (i / data.length) * 360;
        ctx.fillStyle = `hsl(${hue}, 70%, 50%)`;
        ctx.fillRect(i * barWidth, canvas.height - barHeight - 50, barWidth - 2, barHeight);
        ctx.fillStyle = '#333';
        ctx.font = '12px Arial';
        ctx.fillText(value.toFixed(1), i * barWidth, canvas.height - 55);
    });
    ctx.fillStyle = '#666';
    ctx.font = '16px Arial';
    ctx.textAlign = 'center';
    ctx.fillText('Bar Chart - Random Data', canvas.width / 2, 25);
}

function drawLineChart() {
    if (data.length < 2) return drawBarChart();
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const maxValue = Math.max(...data);
    ctx.strokeStyle = '#48bb78';
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.moveTo(0, canvas.height - 50 - (data[0] / maxValue) * (canvas.height - 100));
    for (let i = 1; i < data.length; i++) {
        ctx.lineTo(i * (canvas.width / data.length), canvas.height - 50 - (data[i] / maxValue) * (canvas.height - 100));
    }
    ctx.stroke();
    ctx.fillStyle = '#2d3748';
    ctx.font = '14px Arial';
    ctx.textAlign = 'center';
    ctx.fillText('Line Chart - Random Data Trend', canvas.width / 2, 25);
}

function drawPieChart() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const total = data.reduce((a, b) => a + b, 0);
    let startAngle = 0;
    data.forEach((value, i) => {
        const sliceAngle = (value / total) * 2 * Math.PI;
        const hue = (i / data.length) * 360;
        ctx.fillStyle = `hsl(${hue}, 70%, 60%)`;
        ctx.beginPath();
        ctx.moveTo(300, 250);
        ctx.arc(300, 250, 200, startAngle, startAngle + sliceAngle);
        ctx.closePath();
        ctx.fill();
        startAngle += sliceAngle;
    });
    ctx.fillStyle = '#333';
    ctx.font = '20px Arial';
    ctx.textAlign = 'center';
    ctx.fillText('Pie Chart - Data Distribution', 300, 450);
}

function drawChart() {
    switch (chartType.value) {
        case 'bar': drawBarChart(); break;
        case 'line': drawLineChart(); break;
        case 'pie': drawPieChart(); break;
    }
}

generateBtn.onclick = () => {
    generateRandomData();
    drawChart();
    if (animationId) cancelAnimationFrame(animationId);
    isAnimating = false;
    animateBtn.textContent = 'Animate';
};

chartType.onchange = drawChart;

animateBtn.onclick = () => {
    if (isAnimating) {
        cancelAnimationFrame(animationId);
        isAnimating = false;
        animateBtn.textContent = 'Animate';
    } else {
        isAnimating = true;
        animateBtn.textContent = 'Stop';
        function animate() {
            generateRandomData();
            drawChart();
            if (isAnimating) animationId = requestAnimationFrame(animate);
        }
        animate();
    }
};

// Initial
generateRandomData();
drawChart();

