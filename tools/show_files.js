const fs = require('fs');
const path = require('path');
var ext = ".json";
ext = process.argv[process.argv.length - 1];
console.log("EXT: " + ext);
function getAllJsonFilesSync(dir) {
  let results = [];
  try {
    const items = fs.readdirSync(dir, { withFileTypes: true });
    for (const item of items) {
      const fullPath = path.join(dir, item.name);
      if (item.isDirectory()) {
        // 递归子目录
        results = results.concat(getAllJsonFilesSync(fullPath));
      } else if (item.isFile() && path.extname(item.name) === ext) {
        results.push(fullPath.replaceAll("\\", "/"));
      }
    }
  } catch (err) {
    console.error('读取目录失败:', err);
  }
  return results;
}

// 使用示例
const files = getAllJsonFilesSync('.');
console.log(JSON.stringify(files, null, 2))