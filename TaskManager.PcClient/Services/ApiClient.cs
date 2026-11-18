using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

namespace TaskManager.PcClient.Services;

public class ApiException : Exception
{
    public ApiException(string message) : base(message) { }
}

/// <summary>网络连接失败或请求超时，用于区分服务器可访问但返回 HTTP 错误的情况</summary>
public sealed class NetworkApiException : ApiException
{
    public NetworkApiException(string message) : base(message) { }
}

/// <summary>登录状态失效（HTTP 401），由主界面捕获并引导重新登录</summary>
public sealed class UnauthorizedApiException : ApiException
{
    public UnauthorizedApiException(string message) : base(message) { }
}

/// <summary>
/// 后端 API 客户端：统一拼接 /api/v1 地址、携带 JWT、JSON 序列化（camelCase）。
/// </summary>
public class ApiClient
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(30) };
    private readonly SettingsService _settings;

    public ApiClient(SettingsService settings) => _settings = settings;

    /// <summary>发送请求并返回 JSON 文档（无响应体时返回空对象）</summary>
    public async Task<JsonDocument> SendForJsonAsync(HttpMethod method, string path, object? body = null)
    {
        var baseUrl = (_settings.Current.ServerBaseUrl ?? string.Empty).TrimEnd('/');
        using var request = new HttpRequestMessage(method, baseUrl + path);

        var token = _settings.Current.Token;
        if (!string.IsNullOrEmpty(token))
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

        if (body != null)
            request.Content = new StringContent(
                JsonSerializer.Serialize(body, JsonOptions), Encoding.UTF8, "application/json");

        HttpResponseMessage response;
        try
        {
            response = await _http.SendAsync(request);
        }
        catch (HttpRequestException ex)
        {
            throw new NetworkApiException($"无法连接服务器（{ex.Message}）");
        }
        catch (TaskCanceledException)
        {
            throw new NetworkApiException("请求超时，请检查网络或服务器地址");
        }

        using (response)
        {
            var text = await response.Content.ReadAsStringAsync();
            if (response.StatusCode == HttpStatusCode.Unauthorized)
                throw new UnauthorizedApiException(ParseErrorMessage(text, response.StatusCode));
            if (!response.IsSuccessStatusCode)
                throw new ApiException(ParseErrorMessage(text, response.StatusCode));
            return JsonDocument.Parse(string.IsNullOrWhiteSpace(text) ? "{}" : text);
        }
    }

    public Task<JsonDocument> PostForJsonAsync(string path, object body)
        => SendForJsonAsync(HttpMethod.Post, path, body);

    /// <summary>GET 并反序列化；服务端返回数组或 { data: 数组 } 包装均可</summary>
    public async Task<List<T>> GetListAsync<T>(string path)
    {
        using var doc = await SendForJsonAsync(HttpMethod.Get, path);
        var root = doc.RootElement;
        var array = root.ValueKind == JsonValueKind.Array ? root
            : root.TryGetProperty("data", out var data) && data.ValueKind == JsonValueKind.Array ? data
            : root.TryGetProperty("tasks", out var tasks) && tasks.ValueKind == JsonValueKind.Array ? tasks
            : default;
        if (array.ValueKind != JsonValueKind.Array)
            return new List<T>();
        return array.Deserialize<List<T>>(JsonOptions) ?? new List<T>();
    }

    /// <summary>从鉴权响应中提取 token（兼容 { token } 与 { data: { token } }）</summary>
    public static string? ExtractToken(JsonDocument doc)
    {
        var root = doc.RootElement;
        if (root.ValueKind == JsonValueKind.Object)
        {
            if (root.TryGetProperty("token", out var t)) return t.GetString();
            if (root.TryGetProperty("data", out var d) && d.ValueKind == JsonValueKind.Object
                && d.TryGetProperty("token", out var t2)) return t2.GetString();
        }
        return null;
    }

    private static string ParseErrorMessage(string body, HttpStatusCode statusCode)
    {
        try
        {
            using var doc = JsonDocument.Parse(body);
            if (doc.RootElement.ValueKind == JsonValueKind.Object
                && doc.RootElement.TryGetProperty("message", out var msg))
                return msg.GetString() ?? $"请求失败（{(int)statusCode}）";
        }
        catch { }
        return statusCode == HttpStatusCode.Unauthorized
            ? "登录状态已失效，请重新登录"
            : $"请求失败（{(int)statusCode}）";
    }
}
