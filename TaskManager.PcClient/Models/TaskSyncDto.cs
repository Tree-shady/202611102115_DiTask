namespace TaskManager.PcClient.Models;

/// <summary>与云端 /api/v1/tasks 接口交换的任务传输对象（不含本地专属字段）</summary>
public class TaskSyncDto
{
    public string Id { get; set; } = string.Empty;
    public string? UserId { get; set; }
    public string Title { get; set; } = string.Empty;
    public string? Content { get; set; }
    public int Status { get; set; }
    public int Priority { get; set; }
    public DateTime? DueTime { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }

    /// <summary>
    /// 统一标记为 UTC：SQLite 读出的 DateTime 丢失 Kind（Unspecified），
    /// 直接序列化会缺少时区后缀，服务端会按自身时区解析导致时间戳偏移。
    /// </summary>
    public static DateTime AsUtc(DateTime value)
        => value.Kind == DateTimeKind.Utc ? value : DateTime.SpecifyKind(value, DateTimeKind.Utc);

    public static DateTime? AsUtc(DateTime? value) => value.HasValue ? AsUtc(value.Value) : null;

    public static TaskSyncDto FromEntity(TaskItem t) => new()
    {
        Id = t.Id,
        UserId = t.UserId,
        Title = t.Title,
        Content = t.Content,
        Status = t.Status,
        Priority = t.Priority,
        DueTime = AsUtc(t.DueTime),
        IsDeleted = t.IsDeleted,
        CreatedAt = AsUtc(t.CreatedAt),
        UpdatedAt = AsUtc(t.UpdatedAt)
    };

    public TaskItem ToEntity() => new()
    {
        Id = Id,
        UserId = UserId,
        Title = Title,
        Content = Content,
        Status = Status,
        Priority = Priority,
        DueTime = AsUtc(DueTime),
        IsDeleted = IsDeleted,
        IsPendingSync = false,
        CreatedAt = AsUtc(CreatedAt),
        UpdatedAt = AsUtc(UpdatedAt)
    };

    /// <summary>用云端数据覆盖本地实体（调用前需已确认云端 UpdatedAt 更新）</summary>
    public void ApplyTo(TaskItem local)
    {
        local.UserId = UserId;
        local.Title = Title;
        local.Content = Content;
        local.Status = Status;
        local.Priority = Priority;
        local.DueTime = AsUtc(DueTime);
        local.IsDeleted = IsDeleted;
        local.CreatedAt = AsUtc(CreatedAt);
        local.UpdatedAt = AsUtc(UpdatedAt);
        local.IsPendingSync = false;
    }
}
