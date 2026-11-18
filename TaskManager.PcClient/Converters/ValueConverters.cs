using System.Globalization;
using System.Windows.Data;

namespace TaskManager.PcClient.Converters;

public class StatusToTextConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        => value is int s ? s switch { 0 => "待办", 1 => "进行中", 2 => "已完成", _ => "未知" } : "";

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotSupportedException();
}

public class PriorityToTextConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        => value is int p ? p switch { 0 => "普通", 1 => "重要", 2 => "紧急", _ => "未知" } : "";

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotSupportedException();
}

/// <summary>UTC 时间转本地时间显示（数据库中存的都是 UTC）</summary>
public class UtcToLocalTimeConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is not DateTime dt) return "";
        var utc = dt.Kind == DateTimeKind.Utc ? dt : DateTime.SpecifyKind(dt, DateTimeKind.Utc);
        return utc.ToLocalTime().ToString("yyyy-MM-dd HH:mm");
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotSupportedException();
}
