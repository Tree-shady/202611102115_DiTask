const express = require("express");
const prisma = require("../lib/prisma");
const auth = require("../middleware/auth");

const router = express.Router();
router.use(auth);

// 旧版 PC 客户端上传的时间缺少时区标记（如 2026-11-20T14:51:18.749），
// 直接 new Date() 会按服务器本地时区解析，导致落库时间整体偏移、其它设备增量拉取漏数据。
// 不带时区的字符串统一按 UTC 解析。
function parseClientDate(value, fallback) {
  if (value === undefined || value === null || value === "") return fallback;
  const text = String(value);
  const hasZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(text);
  const date = new Date(hasZone ? text : `${text}Z`);
  return Number.isNaN(date.getTime()) ? fallback : date;
}

// GET /api/v1/tasks?lastSyncTime=UTC时间
// 增量拉取：返回当前用户晚于 lastSyncTime 的所有新增/修改/删除任务（含软删除记录）
// 不传 lastSyncTime 时返回全量（首次同步）
router.get("/", async (req, res, next) => {
  try {
    const where = { userId: req.userId };
    const { lastSyncTime } = req.query;
    if (lastSyncTime) {
      const since = new Date(lastSyncTime);
      if (Number.isNaN(since.getTime())) {
        return res.status(400).json({ message: "lastSyncTime 格式不正确" });
      }
      where.updatedAt = { gt: since };
    }
    const tasks = await prisma.task.findMany({ where, orderBy: { updatedAt: "asc" } });
    res.json(tasks);
  } catch (err) {
    next(err);
  }
});

// POST /api/v1/tasks/sync
// 批量上传本地变更：以客户端传入的 updatedAt 时间戳为最终数据依据，按 id 批量 upsert
// 事务保证整批要么全部成功、要么全部失败
router.post("/sync", async (req, res, next) => {
  try {
    const items = req.body;
    if (!Array.isArray(items)) {
      return res.status(400).json({ message: "请求体必须为任务数组" });
    }
    const results = await prisma.$transaction(
      items.map((t) => {
        const data = {
          userId: req.userId, // 归属以登录身份为准，忽略客户端传入值
          title: String(t.title ?? ""),
          content: t.content ?? null,
          status: Number.isInteger(t.status) ? t.status : 0,
          priority: Number.isInteger(t.priority) ? t.priority : 0,
          dueTime: t.dueTime ? parseClientDate(t.dueTime, null) : null,
          isDeleted: Boolean(t.isDeleted),
          createdAt: parseClientDate(t.createdAt, new Date()),
          updatedAt: parseClientDate(t.updatedAt, new Date()),
        };
        return prisma.task.upsert({
          where: { id: String(t.id) },
          update: data,
          create: { id: String(t.id), ...data },
        });
      })
    );
    res.json({ synced: results.length });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
