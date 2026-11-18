const { PrismaClient } = require("@prisma/client");

// 全局单例，避免热重载/多模块重复创建连接池
module.exports = new PrismaClient();
