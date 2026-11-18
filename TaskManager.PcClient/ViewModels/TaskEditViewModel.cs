using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using TaskManager.PcClient.Models;

namespace TaskManager.PcClient.ViewModels;

public partial class TaskEditViewModel : ObservableObject
{
    private readonly TaskItem _entity;

    public TaskEditViewModel(TaskItem entity, bool isNew)
    {
        _entity = entity;
        WindowTitle = isNew ? "新增任务" : "编辑任务";
        title = entity.Title;
        content = entity.Content;
        statusIndex = entity.Status;
        priorityIndex = entity.Priority;
        hasDueTime = entity.DueTime.HasValue;
        // 数据库存 UTC，界面展示为本地日期和时分
        var localDueTime = entity.DueTime.HasValue
            ? DateTime.SpecifyKind(entity.DueTime.Value, DateTimeKind.Utc).ToLocalTime()
            : (DateTime?)null;
        dueDate = localDueTime?.Date ?? DateTime.Today;
        dueTimeText = localDueTime?.ToString("HH:mm") ?? "18:00";
    }

    public string WindowTitle { get; }

    [ObservableProperty]
    private string title;

    [ObservableProperty]
    private string? content;

    [ObservableProperty]
    private int statusIndex;

    [ObservableProperty]
    private int priorityIndex;

    [ObservableProperty]
    private bool hasDueTime;

    [ObservableProperty]
    private DateTime dueDate;

    [ObservableProperty]
    private string dueTimeText = "18:00";

    /// <summary>校验并写回实体；写操作规范：更新 UpdatedAt(UTC) 并标记待同步</summary>
    public bool TrySave(out string error)
    {
        if (string.IsNullOrWhiteSpace(Title))
        {
            error = "任务标题不能为空";
            return false;
        }

        TimeSpan dueTimeOfDay = default;
        if (HasDueTime && !TimeSpan.TryParseExact(
                DueTimeText, @"hh\:mm", CultureInfo.InvariantCulture, out dueTimeOfDay))
        {
            error = "截止时间格式应为 HH:mm，例如 18:00";
            return false;
        }

        _entity.Title = Title.Trim();
        _entity.Content = string.IsNullOrWhiteSpace(Content) ? null : Content!.Trim();
        _entity.Status = StatusIndex;
        _entity.Priority = PriorityIndex;
        if (HasDueTime)
        {
            var localDueTime = DateTime.SpecifyKind(DueDate.Date.Add(dueTimeOfDay), DateTimeKind.Local);
            _entity.DueTime = localDueTime.ToUniversalTime();
        }
        else
        {
            _entity.DueTime = null;
        }
        _entity.UpdatedAt = DateTime.UtcNow;
        _entity.IsPendingSync = true;
        error = string.Empty;
        return true;
    }
}
