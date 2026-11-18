using TaskManager.PcClient.Services;
using TaskManager.PcClient.ViewModels;

namespace TaskManager.PcClient.Views;

public partial class RecycleBinWindow : System.Windows.Window
{
    public RecycleBinWindow(SettingsService settings)
    {
        InitializeComponent();
        DataContext = new RecycleBinViewModel(settings);
    }
}
