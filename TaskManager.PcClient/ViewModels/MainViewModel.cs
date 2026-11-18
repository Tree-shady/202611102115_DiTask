using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Windows;
using System.Windows.Data;
using System.Windows.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.EntityFrameworkCore;
using TaskManager.PcClient.Data;
using TaskManager.PcClient.Models;
using TaskManager.PcClient.Services;

namespace TaskManager.PcClient.ViewModels;

public partial class MainViewModel : ObservableObject
{
    private readonly SettingsService _settings;
    private readonly SyncService _sync;
    private readonly DispatcherTimer _autoSyncTimer;
    private readonly DispatcherTimer _overdueTimer;
    private readonly HashSet<string> _remindedTaskIds = new();

    public MainViewModel(SettingsService settings)
    {
        _settings = settings;
        _sync = new SyncService(settings);
        username = settings.Current.Username ?? string.Empty;
        TasksView = CollectionViewSource.GetDefaultView(Tasks);
        TasksView.Filter = item => item is TaskItem task && MatchesFilter(task);

        // 后台自动同步（每 5 分钟），联网时自动合并数据
        _autoSyncTimer = new DispatcherTimer { Interval = TimeSpan.FromMinutes(5) };
        _autoSyncTimer.Tick += (_, _) => { if (!IsBusy) _ = SyncAsync(); };
        _autoSyncTimer.Start();

        // 每分钟刷新时间显示并合并提醒新到期的任务
        _overdueTimer = new DispatcherTimer { Interval = TimeSpan.FromMinutes(1) };
        _overdueTimer.Tick += (_, _) =>
        {
            foreach (var task in Tasks) task.RefreshDisplayState();
            CheckDueReminders();
        };
        _overdueTimer.Start();

        // 启动后：加载本地列表，随后自动执行一次同步（登录自动拉取初始数据）
        _ = InitializeAsync();
    }

    public ObservableCollection<TaskItem> Tasks { get; } = new();

    /// <summary>主列表筛选视图，仅在内存中过滤，不访问本地数据库或远端服务</summary>
    public ICollectionView TasksView { get; }

    [ObservableProperty]
    private string searchText = string.Empty;

    partial void OnSearchTextChanged(string value) => TasksView.Refresh();

    [ObservableProperty]
    private int statusFilterIndex;

    partial void OnStatusFilterIndexChanged(int value) => TasksView.Refresh();

    [ObservableProperty]
    private int priorityFilterIndex;

    partial void OnPriorityFilterIndexChanged(int value) => TasksView.Refresh();

    private bool MatchesFilter(TaskItem task)
        => MatchesSearch(task)
            && (StatusFilterIndex == 0 || task.Status == StatusFilterIndex - 1)
            && (PriorityFilterIndex == 0 || task.Priority == PriorityFilterIndex - 1);

    private bool MatchesSearch(TaskItem task)
    {
        var keyword = SearchText.Trim();
        if (keyword.Length == 0) return true;
        return task.Title.IndexOf(keyword, StringComparison.OrdinalIgnoreCase) >= 0
            || (task.Content?.IndexOf(keyword, StringComparison.OrdinalIgnoreCase) ?? -1) >= 0;
    }

    [ObservableProperty]
    private string username;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(EditTaskCommand))]
    [NotifyCanExecuteChangedFor(nameof(DeleteTaskCommand))]
    [NotifyCanExecuteChangedFor(nameof(ToggleCompleteCommand))]
    private TaskItem? selectedTask;

    [ObservableProperty]
    private string statusMessage = string.Empty;

    [ObservableProperty]
    private string connectionStateText = "未检测";

    [ObservableProperty]
    private int pendingSyncCount;

    [ObservableProperty]
    private string lastSyncTimeText = "从未同步";

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SyncCommand))]
    private bool isBusy;

    /// <summary>请求打开编辑窗口；参数为任务 Id，null 表示新增</summary>
    public event Action<string?>? RequestEditTask;

    /// <summary>请求打开回收站窗口</summary>
    public event Action? RequestRecycleBin;

    /// <summary>请求返回登录窗口；参数为登录页提示信息，正常退出时传 null</summary>
    public event Action<string?>? RequestLogout;

    private async Task InitializeAsync()
    {
        await LoadTasksAsync();
        CheckDueReminders();
        await SyncAsync();
    }

    private void CheckDueReminders()
    {
        var now = DateTime.UtcNow;
        var dueTasks = Tasks
            .Where(t => t.Status != 2 && t.DueTime.HasValue
                && !_remindedTaskIds.Contains(t.Id) && AsUtc(t.DueTime.Value) <= now)
            .OrderBy(t => t.DueTime)
            .ToList();
        if (dueTasks.Count == 0) return;

        // 弹窗期间 Dispatcher 仍会处理计时器，先记录避免重复提醒
        foreach (var task in dueTasks) _remindedTaskIds.Add(task.Id);
        var lines = dueTasks.Take(5).Select(t =>
            $"- {t.Title}（{AsUtc(t.DueTime!.Value).ToLocalTime():yyyy-MM-dd HH:mm}）");
        var message = "以下任务已到期：\n\n" + string.Join("\n", lines);
        if (dueTasks.Count > 5) message += $"\n另有 {dueTasks.Count - 5} 项任务已到期。";
        MessageBox.Show(message, "到期提醒", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private static DateTime AsUtc(DateTime value)
        => value.Kind == DateTimeKind.Utc ? value : DateTime.SpecifyKind(value, DateTimeKind.Utc);

    public void StopAutoSync()
    {
        _autoSyncTimer.Stop();
        _overdueTimer.Stop();
    }

    private async Task RefreshPendingSyncCountAsync()
    {
        using var db = new AppDbContext();
        var uid = _settings.Current.UserId;
        PendingSyncCount = await db.Tasks.CountAsync(t => t.UserId == uid && t.IsPendingSync);
    }

    private void UpdateLastSyncTimeText()
    {
        var lastSync = _settings.Current.LastSyncTime;
        LastSyncTimeText = lastSync.HasValue
            ? AsUtc(lastSync.Value).ToLocalTime().ToString("yyyy-MM-dd HH:mm")
            : "从未同步";
    }

    public async Task LoadTasksAsync()
    {
        using var db = new AppDbContext();
        var uid = _settings.Current.UserId;
        // 旧数据迁移：无归属标记的任务归属于当前登录用户
        if (uid != null)
        {
            var orphans = await db.Tasks.Where(t => t.UserId == null).ToListAsync();
            if (orphans.Count > 0)
            {
                foreach (var o in orphans) o.UserId = uid;
                await db.SaveChangesAsync();
            }
        }
        // 按当前登录用户隔离本地数据，防止多账号串数据
        var list = await db.Tasks
            .Where(t => !t.IsDeleted && t.UserId == uid)
            .OrderBy(t => t.Status)
            .ThenByDescending(t => t.Priority)
            .ThenBy(t => t.DueTime)
            .ToListAsync();

        Tasks.Clear();
        foreach (var t in list) Tasks.Add(t);
        PendingSyncCount = await db.Tasks.CountAsync(t => t.UserId == uid && t.IsPendingSync);
        UpdateLastSyncTimeText();
    }

    private bool HasSelection() => SelectedTask != null;

    [RelayCommand]
    private void OpenRecycleBin() => RequestRecycleBin?.Invoke();

    [RelayCommand]
    private void AddTask() => RequestEditTask?.Invoke(null);

    [RelayCommand(CanExecute = nameof(HasSelection))]
    private void EditTask()
    {
        if (SelectedTask != null) RequestEditTask?.Invoke(SelectedTask.Id);
    }

    /// <summary>编辑窗口保存后写库：更新 UpdatedAt(UTC) 并标记待同步</summary>
    public async Task SaveTaskAsync(TaskItem entity, bool isNew)
    {
        using var db = new AppDbContext();
        if (isNew)
        {
            // 创建即打上归属标记，离线创建后换账号登录也不会被误传
            entity.UserId ??= _settings.Current.UserId;
            db.Tasks.Add(entity);
        }
        else db.Tasks.Update(entity);
        await db.SaveChangesAsync();
        await LoadTasksAsync();
        StatusMessage = isNew ? "任务已创建（待同步）" : "任务已保存（待同步）";
    }

    /// <summary>软删除：不物理删除，仅标记 isDeleted，支持回收站恢复与同步一致性</summary>
    [RelayCommand(CanExecute = nameof(HasSelection))]
    private async Task DeleteTaskAsync()
    {
        if (SelectedTask == null) return;
        var result = MessageBox.Show(
            $"确定删除任务“{SelectedTask.Title}”吗？\n（软删除，数据不会物理丢失）",
            "确认删除", MessageBoxButton.YesNo, MessageBoxImage.Question);
        if (result != MessageBoxResult.Yes) return;

        using var db = new AppDbContext();
        var entity = await db.Tasks.FindAsync(SelectedTask.Id);
        if (entity != null)
        {
            entity.IsDeleted = true;
            entity.IsPendingSync = true;
            entity.UpdatedAt = DateTime.UtcNow;
            await db.SaveChangesAsync();
        }
        Tasks.Remove(SelectedTask);
        await RefreshPendingSyncCountAsync();
        StatusMessage = "任务已删除（待同步）";
    }

    [RelayCommand(CanExecute = nameof(HasSelection))]
    private async Task ToggleCompleteAsync()
    {
        if (SelectedTask == null) return;
        using var db = new AppDbContext();
        var entity = await db.Tasks.FindAsync(SelectedTask.Id);
        if (entity == null) return;

        entity.Status = entity.Status == 2 ? 0 : 2;
        entity.IsPendingSync = true;
        entity.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync();

        var selectedId = SelectedTask.Id;
        await LoadTasksAsync();
        SelectedTask = Tasks.FirstOrDefault(t => t.Id == selectedId);
        StatusMessage = entity.Status == 2 ? "任务已完成（待同步）" : "任务已重开（待同步）";
    }

    /// <summary>手动一键同步：上传待同步数据 → 拉取云端增量 → 时间戳覆盖</summary>
    [RelayCommand(CanExecute = nameof(NotBusy))]
    private async Task SyncAsync()
    {
        IsBusy = true;
        StatusMessage = "正在同步…";
        try
        {
            var result = await _sync.SyncAsync();
            await LoadTasksAsync();
            ConnectionStateText = "在线";
            StatusMessage = $"同步完成：上传 {result.Uploaded} 条，拉取 {result.Pulled} 条（{DateTime.Now:HH:mm:ss}）";
        }
        catch (UnauthorizedApiException)
        {
            // 登录失效：停止后续自动同步，清除会话并返回登录窗口，本地任务数据保留
            StopAutoSync();
            _settings.ClearSession();
            RequestLogout?.Invoke("登录状态已失效，请重新登录");
        }
        catch (Exception ex)
        {
            // 仅根据真实请求结果判断网络：连接失败为离线，服务器 HTTP 响应仍视为在线
            if (ex is NetworkApiException) ConnectionStateText = "离线";
            else if (ex is ApiException) ConnectionStateText = "在线";
            await RefreshPendingSyncCountAsync();
            StatusMessage = "同步失败：" + ex.Message + "（本地数据已保存，联网后自动重试）";
        }
        finally
        {
            IsBusy = false;
        }
    }

    private bool NotBusy() => !IsBusy;

    [RelayCommand]
    private void Logout()
    {
        StopAutoSync();
        _settings.ClearSession();
        RequestLogout?.Invoke(null);
    }
}
