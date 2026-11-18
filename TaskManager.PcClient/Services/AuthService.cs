namespace TaskManager.PcClient.Services;

/// <summary>鉴权服务：注册 / 登录，成功后持久化 JWT Token</summary>
public class AuthService
{
    private readonly ApiClient _api;
    private readonly SettingsService _settings;

    public AuthService(SettingsService settings)
    {
        _settings = settings;
        _api = new ApiClient(settings);
    }

    /// <param name="isRegister">true 走注册接口，false 走登录接口</param>
    public async Task AuthAsync(string username, string password, bool isRegister)
    {
        var path = isRegister ? "/api/v1/auth/register" : "/api/v1/auth/login";
        using var doc = await _api.PostForJsonAsync(path, new { username, password });
        var token = ApiClient.ExtractToken(doc)
            ?? throw new ApiException("服务器响应中未包含 Token");
        var newUserId = SettingsService.ExtractUserId(token);
        // 切换账号后同步基准作废，触发该账号的全量拉取
        if (_settings.Current.UserId != null && newUserId != null && _settings.Current.UserId != newUserId)
            _settings.Current.LastSyncTime = null;
        _settings.Current.Token = token;
        _settings.Current.Username = username;
        _settings.Current.UserId = newUserId;
        // 新账号没有历史数据，重置同步基准，触发全量拉取
        if (isRegister) _settings.Current.LastSyncTime = null;
        _settings.Save();
    }
}
