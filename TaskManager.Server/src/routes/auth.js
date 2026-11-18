const express = require("express");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const prisma = require("../lib/prisma");

const router = express.Router();

// 签发 JWT，7 天有效期
function signToken(userId) {
  return jwt.sign({ userId }, process.env.JWT_SECRET, { expiresIn: "7d" });
}

// POST /api/v1/auth/register
router.post("/register", async (req, res, next) => {
  try {
    const { username, password } = req.body ?? {};
    if (!username?.trim() || !password) {
      return res.status(400).json({ message: "用户名和密码不能为空" });
    }
    if (String(password).length < 6) {
      return res.status(400).json({ message: "密码长度至少 6 位" });
    }
    const name = username.trim();
    const exists = await prisma.user.findUnique({ where: { username: name } });
    if (exists) {
      return res.status(409).json({ message: "用户名已存在" });
    }
    const hash = await bcrypt.hash(String(password), 10);
    const user = await prisma.user.create({ data: { username: name, password: hash } });
    res.json({ token: signToken(user.id) });
  } catch (err) {
    next(err);
  }
});

// POST /api/v1/auth/login
router.post("/login", async (req, res, next) => {
  try {
    const { username, password } = req.body ?? {};
    if (!username?.trim() || !password) {
      return res.status(400).json({ message: "用户名和密码不能为空" });
    }
    const user = await prisma.user.findUnique({ where: { username: username.trim() } });
    // 统一报错，避免暴露用户名是否存在
    if (!user || !(await bcrypt.compare(String(password), user.password))) {
      return res.status(401).json({ message: "用户名或密码错误" });
    }
    res.json({ token: signToken(user.id) });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
