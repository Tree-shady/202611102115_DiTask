using Microsoft.EntityFrameworkCore;
using TaskManager.PcClient.Data;
using TaskManager.PcClient.Models;

namespace TaskManager.PcClient.Services;

public record SyncResult(int Uploaded, int Pulled);

/// <summary>
/// 增量同步服务，时序（系统核心规范，两端一致）：
/// 1. 上传本地所有待同步数据 → 2. 拉取云端增量数据
/// → 3. 本地按时间戳比对，新时间戳覆盖旧数据 → 4. 更新最后同步时间、清空待同步标记
/// </summary>
public class SyncService
{
    private readonly ApiClient _api;
    private readonly SettingsService _settings;

    public SyncService(SettingsService settings)
    {
        _settings = settings;
        _api = new ApiClient(settings);
    }

    public async Task<SyncResult> SyncAsync()
    {
        // 以同步开始时刻作为新的基准，避免同步期间产生的变更被漏掉
        var syncStart = DateTime.UtcNow;
        var uid = _settings.Current.UserId;

        using var db = new AppDbContext();

        // 第一步：上传本地所有待同步数据（仅当前账号的数据，防止换账号后他人数据被误传到自己名下）
        var pending = await db.Tasks.Where(t => t.IsPendingSync && t.UserId == uid).ToListAsync();
        if (pending.Count > 0)
        {
            var payload = pending.Select(TaskSyncDto.FromEntity).ToList();
            await _api.PostForJsonAsync("/api/v1/tasks/sync", payload);
            foreach (var t in pending) t.IsPendingSync = false;
            await db.SaveChangesAsync();
        }

        // 第二步：拉取云端所有增量更新数据
        var lastSync = _settings.Current.LastSyncTime;
        var path = lastSync.HasValue
            ? $"/api/v1/tasks?lastSyncTime={Uri.EscapeDataString(lastSync.Value.ToUniversalTime().ToString("o"))}"
            : "/api/v1/tasks";
        var remoteTasks = await _api.GetListAsync<TaskSyncDto>(path);

        // 第三步：本地比对时间戳，新时间戳覆盖旧数据（客户端时间戳优先策略）
        foreach (var dto in remoteTasks)
        {
            // 防御：云端数据不属于当前账号时跳过（服务端已按 token 过滤，理论上不会触发）
            if (uid != null && dto.UserId != null && dto.UserId != uid) continue;
            var local = await db.Tasks.FindAsync(dto.Id);
            if (local == null)
            {
                // 未知墓碑无需落库：既避免全量同步堆积历史删除记录，
                // 也防止回收站中已本机彻底清除的任务被重新拉回
                if (!dto.IsDeleted) db.Tasks.Add(dto.ToEntity());
            }
            else if (dto.UpdatedAt.ToUniversalTime() > local.UpdatedAt.ToUniversalTime())
            {
                dto.ApplyTo(local);
            }
        }
        await db.SaveChangesAsync();

        // 第四步：更新全局最后同步时间（待同步标记已在上传成功后清空）
        _settings.Current.LastSyncTime = syncStart;
        _settings.Save();

        return new SyncResult(pending.Count, remoteTasks.Count);
    }
}
