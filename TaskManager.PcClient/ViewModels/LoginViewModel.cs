using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using TaskManager.PcClient.Services;

namespace TaskManager.PcClient.ViewModels;

public partial class LoginViewModel : ObservableObject
{
    private readonly AuthService _auth;
    private readonly SettingsService _settings;

    public LoginViewModel(SettingsService settings, AuthService auth)
    {
        _settings = settings;
        _auth = auth;
        serverBaseUrl = settings.Current.ServerBaseUrl;
        username = settings.Current.Username ?? string.Empty;
    }

    [ObservableProperty]
    private string serverBaseUrl;

    [ObservableProperty]
    private string username;

    /// <summary>PasswordBox 不支持直接绑定，由界面代码后置写入</summary>
    public string Password { get; set; } = string.Empty;

    [ObservableProperty]
    private string errorMessage = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(LoginCommand))]
    [NotifyCanExecuteChangedFor(nameof(RegisterCommand))]
    private bool isBusy;

    /// <summary>登录或注册成功事件，由界面订阅后跳转主窗口</summary>
    public event Action? AuthSucceeded;

    private bool NotBusy() => !IsBusy;

    [RelayCommand(CanExecute = nameof(NotBusy))]
    private Task LoginAsync() => DoAuthAsync(isRegister: false);

    [RelayCommand(CanExecute = nameof(NotBusy))]
    private Task RegisterAsync() => DoAuthAsync(isRegister: true);

    private async Task DoAuthAsync(bool isRegister)
    {
        ErrorMessage = string.Empty;
        if (string.IsNullOrWhiteSpace(ServerBaseUrl)) { ErrorMessage = "请输入服务器地址"; return; }
        if (string.IsNullOrWhiteSpace(Username) || string.IsNullOrWhiteSpace(Password))
        {
            ErrorMessage = "请输入用户名和密码";
            return;
        }

        IsBusy = true;
        try
        {
            _settings.Current.ServerBaseUrl = ServerBaseUrl.Trim();
            _settings.Save();
            await _auth.AuthAsync(Username.Trim(), Password, isRegister);
            AuthSucceeded?.Invoke();
        }
        catch (Exception ex)
        {
            ErrorMessage = (isRegister ? "注册失败：" : "登录失败：") + ex.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }
}
