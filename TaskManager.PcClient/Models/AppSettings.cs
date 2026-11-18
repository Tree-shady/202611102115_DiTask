namespace TaskManager.PcClient.Models;

/// <summary>本地持久化配置：Token、服务器地址、最后同步时间</summary>
public class AppSettings
{
    public string ServerBaseUrl { get; set; } = "http://localhost:3000";
    public string? Token { get; set; }
    public string? Username { get; set; }
    /// <summary>当前登录用户 Id（登录时从 JWT 解码），用于本地数据按账号隔离</summary>
    public string? UserId { get; set; }
    /// <summary>全局最后同步时间（UTC），增量拉取的基准</summary>
    public DateTime? LastSyncTime { get; set; }
}
