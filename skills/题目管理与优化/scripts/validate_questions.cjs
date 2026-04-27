const fs = require('fs');
const path = require('path');

const filePath = path.join(__dirname, '../../builtin_questions.json');

try {
    const rawData = fs.readFileSync(filePath, 'utf8');
    const questions = JSON.parse(rawData);

    console.log(`正在扫描 ${questions.length} 道题目...`);

    const seen = new Set();
    questions.forEach((q, index) => {
        // 检查重复
        if (seen.has(q.question)) {
            console.error(`[重复] 第 ${index + 1} 题: ${q.question}`);
        }
        seen.add(q.question);

        // 检查缺失
        if (!q.options || q.options.length === 0) {
            console.error(`[缺失选项] 第 ${index + 1} 题: ${q.question}`);
        }
    });

    console.log('扫描完成。');
} catch (e) {
    console.error('读取题目文件失败:', e.message);
}
