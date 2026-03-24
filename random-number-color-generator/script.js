
document.addEventListener('DOMContentLoaded', function() {
    const numberDisplay = document.getElementById('number-display');
    const colorBox = document.getElementById('color-display');
    const colorValue = document.getElementById('color-value');
    const randomNumberBtn = document.getElementById('random-number');
    const randomColorBtn = document.getElementById('random-color');
    const randomBothBtn = document.getElementById('random-both');
    const copyColorBtn = document.getElementById('copy-color');

    function generateRandomNumber() {
        return Math.floor(Math.random() * 100) + 1; // 1-100
    }

    function generateRandomColor() {
        return '#' + Math.floor(Math.random()*16777215).toString(16).padStart(6, '0');
    }

    function updateNumber() {
        const num = generateRandomNumber();
        numberDisplay.textContent = num;
        numberDisplay.style.color = num > 50 ? '#fff' : '#000';
    }

    function updateColor() {
        const color = generateRandomColor();
        colorBox.style.backgroundColor = color;
        colorValue.textContent = color;
        document.body.style.background = `linear-gradient(135deg, ${color}, ${generateRandomColor()})`;
    }

    randomNumberBtn.onclick = updateNumber;
    randomColorBtn.onclick = updateColor;
    randomBothBtn.onclick = () => {
        updateNumber();
        updateColor();
    };

    copyColorBtn.onclick = () => {
        navigator.clipboard.writeText(colorValue.textContent).then(() => {
            copyColorBtn.textContent = 'Copied!';
            setTimeout(() => copyColorBtn.textContent = 'Copy Color', 2000);
        });
    };

    // Initial values
    updateNumber();
    updateColor();
});

