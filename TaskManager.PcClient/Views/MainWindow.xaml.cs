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
