using System.Windows;
using TaskManager.PcClient.Services;
using TaskManager.PcClient.ViewModels;

namespace TaskManager.PcClient.Views;

public partial class LoginWindow : Window
{
    private readonly LoginViewModel _vm;

    public LoginWindow(string? message = null)
    {
        InitializeComponent();
        var settings = new SettingsService();
        _vm = new LoginViewModel(settings, new AuthService(settings));
        if (message != null) _vm.ErrorMessage = message;
        DataContext = _vm;
        _vm.AuthSucceeded += OnAuthSucceeded;
    }

    private void OnAuthSucceeded()
    {
        var main = new MainWindow();
        Application.Current.MainWindow = main;
        main.Show();
        Close();
    }

    private void PwdBox_PasswordChanged(object sender, RoutedEventArgs e)
        => _vm.Password = PwdBox.Password;
}
