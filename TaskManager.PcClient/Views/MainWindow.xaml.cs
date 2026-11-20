using System.Windows;
using System.Windows.Input;
using TaskManager.PcClient.Data;
using TaskManager.PcClient.Models;
using TaskManager.PcClient.Services;
using TaskManager.PcClient.ViewModels;

namespace TaskManager.PcClient.Views;

public partial class MainWindow : Window
{
    private readonly MainViewModel _vm;
    private readonly SettingsService _settings;

    public MainWindow()
    {
        InitializeComponent();
        _settings = new SettingsService();
        _vm = new MainViewModel(_settings);
        DataContext = _vm;
        _vm.RequestEditTask += OnRequestEditTask;
        _vm.RequestRecycleBin += OnRequestRecycleBin;
        _vm.RequestLogout += OnRequestLogout;
    }

    private async void OnRequestEditTask(string? taskId)
    {
        using var db = new AppDbContext();
        var entity = taskId == null ? new TaskItem() : await db.Tasks.FindAsync(taskId);
        if (entity == null) return;

        var dialog = new TaskEditWindow(entity, taskId == null) { Owner = this };
        if (dialog.ShowDialog() == true)
            await _vm.SaveTaskAsync(entity, taskId == null);
    }

    /// <summary>在应用内修改服务器地址；地址变更后旧登录态失效，提示重新登录</summary>
    private void ServerSettingsButton_Click(object sender, RoutedEventArgs e)
    {
        var previous = (_settings.Current.ServerBaseUrl ?? string.Empty).TrimEnd('/');
        var dialog = new ServerSettingsWindow(_settings) { Owner = this };
        if (dialog.ShowDialog() != true || dialog.ServerBaseUrl == previous) return;

        _settings.Current.ServerBaseUrl = dialog.ServerBaseUrl;
        _settings.Save();

        var result = MessageBox.Show(
            $"服务器地址已更新为：\n{dialog.ServerBaseUrl}\n\n旧的登录状态在新服务器上无效，需要重新登录后生效。是否立即重新登录？",
            "服务器设置", MessageBoxButton.YesNo, MessageBoxImage.Question);
        if (result == MessageBoxResult.Yes)
            OnRequestLogout("服务器地址已更新，请重新登录");
        else
            _vm.StatusMessage = "服务器地址已保存，重新登录后生效";
    }

    private async void OnRequestRecycleBin()
    {
        var dialog = new RecycleBinWindow(_settings) { Owner = this };
        dialog.ShowDialog();
        await _vm.LoadTasksAsync();
    }

    private void OnRequestLogout(string? message)
    {
        var login = new LoginWindow(message);
        Application.Current.MainWindow = login;
        login.Show();
        Close();
    }

    private void TaskGrid_MouseDoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (_vm.EditTaskCommand.CanExecute(null))
            _vm.EditTaskCommand.Execute(null);
    }

    protected override void OnClosed(EventArgs e)
    {
        _vm.StopAutoSync();
        base.OnClosed(e);
    }
}
