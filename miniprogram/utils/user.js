/**
 * utils/user.js — 用户工具函数
 */

/**
 * 生成持久化用户 ID。
 * 优先使用 crypto.getRandomValues（更安全），否则降级至 Math.random。
 */
function generateUserId() {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  const bytes = new Uint8Array(32);
  try {
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
      crypto.getRandomValues(bytes);
    } else {
      for (let i = 0; i < bytes.length; i++) {
        bytes[i] = Math.floor(Math.random() * 256);
      }
    }
  } catch (_) {
    for (let i = 0; i < bytes.length; i++) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  let result = '';
  for (let i = 0; i < bytes.length; i++) {
    result += chars.charAt(bytes[i] % chars.length);
  }
  return result;
}

module.exports = { generateUserId };
