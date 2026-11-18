using System.IO;
using System.Text.Json;
using TaskManager.PcClient.Models;

namespace TaskManager.PcClient.Services;

/// <summary>本地配置持久化：%LocalAppData%\TaskManagerPc\settings.json</summary>
public class SettingsService
{
    private static readonly string FilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "TaskManagerPc", "settings.json");

    public AppSettings Current { get; private set; }

    public SettingsService()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
        Current = Load();
        // 旧版本升级兼容：已登录会话缺少 UserId 时从 Token 解码补齐
        if (Current.UserId == null && Current.Token != null)
        {
            Current.UserId = ExtractUserId(Current.Token);
            Save();
        }
    }

    /// <summary>从 JWT 载荷解析 userId（不校验签名，仅作本地数据归属识别）</summary>
    public static string? ExtractUserId(string? token)
    {
        try
        {
            var parts = token?.Split('.');
            if (parts == null || parts.Length < 2) return null;
            var payload = parts[1].Replace('-', '+').Replace('_', '/');
            switch (payload.Length % 4) { case 2: payload += "=="; break; case 3: payload += "="; break; }
            using var doc = JsonDocument.Parse(Convert.FromBase64String(payload));
            return doc.RootElement.TryGetProperty("userId", out var v) ? v.GetString() : null;
        }
        catch
        {
            return null;
        }
    }

    private AppSettings Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(FilePath)) ?? new AppSettings();
        }
        catch
        {
            // 配置文件损坏时回退默认配置
        }
        return new AppSettings();
    }

    public void Save()
        => File.WriteAllText(FilePath, JsonSerializer.Serialize(Current, new JsonSerializerOptions { WriteIndented = true }));

    /// <summary>退出登录：清除会话信息，保留服务器地址与本地数据（数据按 UserId 隔离，切回账号仍可用）</summary>
    public void ClearSession()
    {
        Current.Token = null;
        Current.Username = null;
        Current.UserId = null;
        Current.LastSyncTime = null;
        Save();
    }
}
