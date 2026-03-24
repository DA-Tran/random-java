
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');
const generateBtn = document.getElementById('generate');
const bubbleBtn = document.getElementById('bubble');
const mergeBtn = document.getElementById('merge');
const quickBtn = document.getElementById('quick');
const sizeSpan = document.getElementById('size');
const timeSpan = document.getElementById('time');
const comparesSpan = document.getElementById('compares');

let array = [];
let arraySize = 100;
let isSorting = false;
let compares = 0;
let startTime = 0;

function randomArray(n) {
    const arr = [];
    for (let i = 0; i < n; i++) {
        arr.push(Math.random() * 400 + 10);
    }
    return arr;
}

function drawArray() {
    const barWidth = canvas.width / arraySize;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    array.forEach((value, i) => {
        const barHeight = (value / 410) * canvas.height;
        ctx.fillStyle = `hsl(${i * 360 / arraySize}, 70%, 50%)`;
        ctx.fillRect(i * barWidth, canvas.height - barHeight, barWidth - 1, barHeight);
    });
}

generateBtn.onclick = () => {
    arraySize = parseInt(Math.random() * 50) + 50;
    sizeSpan.textContent = `Size: ${arraySize}`;
    array = randomArray(arraySize);
    isSorting = false;
    compares = 0;
    updateStats();
    drawArray();
};

function updateStats() {
    timeSpan.textContent = `Time: ${startTime ? (Date.now() - startTime) : 0}ms`;
    comparesSpan.textContent = `Compares: ${compares}`;
}

bubbleBtn.onclick = () => bubbleSort();
mergeBtn.onclick = () => mergeSort();
quickBtn.onclick = () => quickSort();

async function bubbleSort() {
    isSorting = true;
    disableButtons();
    startTime = Date.now();
    compares = 0;
    updateStats();
    
    for (let i = 0; i < array.length; i++) {
        for (let j = 0; j < array.length - i - 1; j++) {
            compares++;
            updateStats();
            if (array[j] > array[j + 1]) {
                [array[j], array[j + 1]] = [array[j + 1], array[j]];
            }
            drawArray();
            await sleep(10);
        }
    }
    isSorting = false;
    enableButtons();
}

async function mergeSort() {
    isSorting = true;
    disableButtons();
    startTime = Date.now();
    compares = 0;
    array = await mergeSortHelper(array.slice());
    isSorting = false;
    enableButtons();
}

async function mergeSortHelper(arr) {
    if (arr.length <= 1) return arr;
    const mid = Math.floor(arr.length / 2);
    const left = await mergeSortHelper(arr.slice(0, mid));
    const right = await mergeSortHelper(arr.slice(mid));
    const merged = [];
    let i = 0, j = 0;
    while (i < left.length && j < right.length) {
        compares++;
        updateStats();
        if (left[i] < right[j]) {
            merged.push(left[i++]);
        } else {
            merged.push(right[j++]);
        }
        drawArray();
        await sleep(20);
    }
    while (i < left.length) merged.push(left[i++]);
    while (j < right.length) merged.push(right[j++]);
    array = merged;
    drawArray();
    return merged;
}

async function quickSort() {
    isSorting = true;
    disableButtons();
    startTime = Date.now();
    compares = 0;
    quickSortHelper(0, array.length - 1);
    isSorting = false;
    enableButtons();
}

function quickSortHelper(low, high) {
    if (low < high) {
        const pi = partition(low, high);
        quickSortHelper(low, pi - 1);
        quickSortHelper(pi + 1, high);
    }
}

function partition(low, high) {
    const pivot = array[high];
    let i = low - 1;
    for (let j = low; j < high; j++) {
        compares++;
        updateStats();
        if (array[j] < pivot) {
            i++;
            [array[i], array[j]] = [array[j], array[i]];
        }
        drawArray();
        sleep(5); // Non-async, approximate
    }
    [array[i + 1], array[high]] = [array[high], array[i + 1]];
    return i + 1;
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function disableButtons() {
    generateBtn.disabled = true;
    bubbleBtn.disabled = true;
    mergeBtn.disabled = true;
    quickBtn.disabled = true;
}

function enableButtons() {
    generateBtn.disabled = false;
    bubbleBtn.disabled = false;
    mergeBtn.disabled = false;
    quickBtn.disabled = false;
}

// Init
array = randomArray(arraySize);
sizeSpan.textContent = `Size: ${arraySize}`;
drawArray();

