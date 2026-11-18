using System.Windows;
using TaskManager.PcClient.Data;
using TaskManager.PcClient.Services;

namespace TaskManager.PcClient;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // 初始化本地 SQLite 数据库
        AppDbContext.EnsureCreated();

        // 已持有 Token 直接进入主窗口，否则先登录
        var settings = new SettingsService();
        Window window = string.IsNullOrEmpty(settings.Current.Token)
            ? new Views.LoginWindow()
            : new Views.MainWindow();
        MainWindow = window;
        window.Show();
    }
}
