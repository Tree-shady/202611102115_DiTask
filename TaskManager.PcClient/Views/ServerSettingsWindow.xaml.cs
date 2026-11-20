using System;
using System.Windows;
using TaskManager.PcClient.Services;

namespace TaskManager.PcClient.Views;

/// <summary>服务器地址设置对话框：仅负责收集并校验地址，由主窗口决定是否保存</summary>
public partial class ServerSettingsWindow : Window
{
    /// <summary>用户确认后的规范化地址（去除首尾空格与末尾 /）</summary>
    public string ServerBaseUrl { get; private set; } = string.Empty;

    public ServerSettingsWindow(SettingsService settings)
    {
        InitializeComponent();
        ServerUrlBox.Text = settings.Current.ServerBaseUrl;
        ServerUrlBox.Focus();
        ServerUrlBox.SelectAll();
    }

    private void SaveButton_Click(object sender, RoutedEventArgs e)
    {
        var value = (ServerUrlBox.Text ?? string.Empty).Trim().TrimEnd('/');
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri)
            || (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps))
        {
            ErrorText.Text = "请输入有效地址，需包含 http:// 或 https://";
            return;
        }

        ServerBaseUrl = value;
        DialogResult = true;
        Close();
    }
}
