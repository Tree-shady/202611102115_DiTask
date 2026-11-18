using System.ComponentModel.DataAnnotations.Schema;
using CommunityToolkit.Mvvm.ComponentModel;

namespace TaskManager.PcClient.Models;

/// <summary>
/// 任务实体：字段与云端 MySQL Task 表保持一致，
/// IsPendingSync 为本地专属字段，用于识别离线修改数据。
/// 所有时间均为 UTC 标准时间。
/// </summary>
public class TaskItem : ObservableObject
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string? UserId { get; set; }
    public string Title { get; set; } = string.Empty;
    public string? Content { get; set; }
    /// <summary>任务状态：0 待办、1 进行中、2 已完成</summary>
    public int Status { get; set; }
    /// <summary>优先级：0 普通、1 重要、2 紧急</summary>
    public int Priority { get; set; }
    public DateTime? DueTime { get; set; }
    /// <summary>软删除标记：不物理删除，支持回收站恢复</summary>
    public bool IsDeleted { get; set; }
    /// <summary>本地待同步标记：离线修改后置为 true，联网批量上传</summary>
    public bool IsPendingSync { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    /// <summary>修改 UTC 时间，同步冲突解决核心字段</summary>
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    /// <summary>是否逾期：未完成且截止时间早于当前 UTC 时间；仅用于界面展示，不落库</summary>
    [NotMapped]
    public bool IsOverdue
    {
        get
        {
            if (Status == 2 || !DueTime.HasValue) return false;
            var due = DueTime.Value;
            if (due.Kind != DateTimeKind.Utc) due = DateTime.SpecifyKind(due, DateTimeKind.Utc);
            return due < DateTime.UtcNow;
        }
    }

    /// <summary>是否今天到期：按本地日期判断，已完成任务不参与</summary>
    [NotMapped]
    public bool IsDueToday
    {
        get
        {
            if (Status == 2 || !DueTime.HasValue) return false;
            var due = DueTime.Value;
            if (due.Kind != DateTimeKind.Utc) due = DateTime.SpecifyKind(due, DateTimeKind.Utc);
            return due.ToLocalTime().Date == DateTime.Today;
        }
    }

    /// <summary>任务详情的单行摘要，仅用于主列表展示</summary>
    [NotMapped]
    public string ContentSummary
    {
        get
        {
            if (string.IsNullOrWhiteSpace(Content)) return string.Empty;
            var summary = string.Join(" ", Content!.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries));
            return summary.Length <= 80 ? summary : summary.Substring(0, 80) + "…";
        }
    }

    public void RefreshDisplayState()
    {
        OnPropertyChanged(nameof(IsOverdue));
        OnPropertyChanged(nameof(IsDueToday));
    }
}
