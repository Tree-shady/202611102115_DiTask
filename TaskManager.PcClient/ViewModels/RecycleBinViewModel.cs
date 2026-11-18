using System.Collections.ObjectModel;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.EntityFrameworkCore;
using TaskManager.PcClient.Data;
using TaskManager.PcClient.Models;
using TaskManager.PcClient.Services;

namespace TaskManager.PcClient.ViewModels;

public partial class RecycleBinViewModel : ObservableObject
{
    private readonly SettingsService _settings;
    private readonly ApiClient _api;

    public RecycleBinViewModel(SettingsService settings)
    {
        _settings = settings;
        _api = new ApiClient(settings);
        _ = LoadAsync();
    }

    public ObservableCollection<TaskItem> DeletedTasks { get; } = new();

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(RestoreTaskCommand))]
    [NotifyCanExecuteChangedFor(nameof(PurgeTaskCommand))]
    private TaskItem? selectedTask;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(RestoreTaskCommand))]
    [NotifyCanExecuteChangedFor(nameof(PurgeTaskCommand))]
    private bool isBusy;

    [ObservableProperty]
    private string statusMessage = string.Empty;

    public async Task LoadAsync()
    {
        using var db = new AppDbContext();
        var uid = _settings.Current.UserId;
        var list = await db.Tasks
            .Where(t => t.IsDeleted && t.UserId == uid)
            .OrderByDescending(t => t.UpdatedAt)
            .ToListAsync();

        DeletedTasks.Clear();
        foreach (var task in list) DeletedTasks.Add(task);
    }

    private bool CanModify() => SelectedTask != null && !IsBusy;

    [RelayCommand(CanExecute = nameof(CanModify))]
    private async Task RestoreTaskAsync()
    {
        if (SelectedTask == null) return;
        var selected = SelectedTask;
        IsBusy = true;
        try
        {
            using var db = new AppDbContext();
            var uid = _settings.Current.UserId;
            var entity = await db.Tasks.FirstOrDefaultAsync(
                t => t.Id == selected.Id && t.UserId == uid && t.IsDeleted);
            if (entity == null)
            {
                StatusMessage = "该任务已不存在或已被恢复";
            }
            else
            {
                entity.IsDeleted = false;
                entity.IsPendingSync = true;
                entity.UpdatedAt = DateTime.UtcNow;
                await db.SaveChangesAsync();
                StatusMessage = "任务已恢复（待同步）";
            }
            DeletedTasks.Remove(selected);
        }
        finally
        {
            IsBusy = false;
        }
    }

    /// <summary>先上传最新删除墓碑，成功后仅物理清除本机记录，防止离线误删后无法同步</summary>
    [RelayCommand(CanExecute = nameof(CanModify))]
    private async Task PurgeTaskAsync()
    {
        if (SelectedTask == null) return;
        var selected = SelectedTask;
        var result = MessageBox.Show(
            $"确定从本机彻底删除任务“{selected.Title}”吗？\n本机将不可恢复；云端仅保留已删除标记用于多端同步。",
            "确认彻底删除", MessageBoxButton.YesNo, MessageBoxImage.Warning);
        if (result != MessageBoxResult.Yes) return;

        IsBusy = true;
        StatusMessage = "正在同步删除状态…";
        try
        {
            using var db = new AppDbContext();
            var uid = _settings.Current.UserId;
            var entity = await db.Tasks.FirstOrDefaultAsync(
                t => t.Id == selected.Id && t.UserId == uid && t.IsDeleted);
            if (entity == null)
            {
                DeletedTasks.Remove(selected);
                StatusMessage = "该任务已不存在";
                return;
            }

            entity.IsDeleted = true;
            entity.IsPendingSync = true;
            entity.UpdatedAt = DateTime.UtcNow;
            await _api.PostForJsonAsync("/api/v1/tasks/sync", new[] { TaskSyncDto.FromEntity(entity) });

            db.Tasks.Remove(entity);
            await db.SaveChangesAsync();
            DeletedTasks.Remove(selected);
            StatusMessage = "任务已从本机彻底删除";
        }
        catch (Exception ex)
        {
            StatusMessage = "彻底删除失败：" + ex.Message + "（回收站记录已保留）";
        }
        finally
        {
            IsBusy = false;
        }
    }
}
