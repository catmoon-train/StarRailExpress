const fs = require('fs');
const path = require('path');

function getAllJsonFilesSync(dir) {
  let results = [];
  try {
    const items = fs.readdirSync(dir, { withFileTypes: true });
    for (const item of items) {
      const fullPath = path.join(dir, item.name);
      if (item.isDirectory()) {
        // 递归子目录
        results = results.concat(getAllJsonFilesSync(fullPath));
      } else if (item.isFile() && path.extname(item.name) === '.json') {
        results.push(fullPath);
      }
    }
  } catch (err) {
    console.error('读取目录失败:', err);
  }
  return results;
}

// 使用示例
const files = getAllJsonFilesSync('.');
for (var i in files) {
  let file = files[i];
  console.log(file);
  fs.writeFileSync(file, JSON.stringify({
    "parent": `wathe:${file.replaceAll("\\","/").replaceAll(".json","")}`
  }, null, 2));
}