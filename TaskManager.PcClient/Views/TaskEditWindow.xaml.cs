using System.Windows;
using TaskManager.PcClient.Models;
using TaskManager.PcClient.ViewModels;

namespace TaskManager.PcClient.Views;

public partial class TaskEditWindow : Window
{
    private readonly TaskEditViewModel _vm;

    public TaskEditWindow(TaskItem entity, bool isNew)
    {
        InitializeComponent();
        _vm = new TaskEditViewModel(entity, isNew);
        DataContext = _vm;
    }

    private void SaveButton_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.TrySave(out var error))
        {
            DialogResult = true;
            Close();
        }
        else
        {
            MessageBox.Show(error, "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }
}
