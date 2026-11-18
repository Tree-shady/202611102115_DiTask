const jwt = require("jsonwebtoken");

// JWT 全局鉴权中间件：校验 Bearer Token，注入 req.userId
module.exports = function auth(req, res, next) {
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : null;
  if (!token) {
    return res.status(401).json({ message: "未提供登录凭证" });
  }
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET);
    req.userId = payload.userId;
    next();
  } catch {
    return res.status(401).json({ message: "登录状态已失效，请重新登录" });
  }
};
