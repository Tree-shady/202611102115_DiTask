require("dotenv").config();
const express = require("express");
const cors = require("cors");

const app = express();
app.use(cors());
app.use(express.json({ limit: "5mb" }));

// 健康检查（无需鉴权）
app.get("/api/v1/health", (req, res) => {
  res.json({ status: "ok", time: new Date().toISOString() });
});

app.use("/api/v1/auth", require("./routes/auth"));
app.use("/api/v1/tasks", require("./routes/tasks"));

// 统一异常处理：始终返回 { message }，与客户端 ApiClient 解析约定一致
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).json({ message: "服务器内部错误" });
});

const port = Number(process.env.PORT) || 3000;
app.listen(port, () => {
  console.log(`TaskManager API 已启动: http://localhost:${port}`);
});
