using System.IO;
using Microsoft.EntityFrameworkCore;
using TaskManager.PcClient.Models;

namespace TaskManager.PcClient.Data;

/// <summary>
/// 本地 SQLite 数据库上下文，数据库文件位于 %LocalAppData%\TaskManagerPc\tasks.db。
/// 表结构与云端一致，本地专属字段 IsPendingSync / 本地索引在此定义。
/// </summary>
public class AppDbContext : DbContext
{
    public DbSet<TaskItem> Tasks => Set<TaskItem>();

    private static readonly string DbPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "TaskManagerPc", "tasks.db");

    public static void EnsureCreated()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(DbPath)!);
        using var db = new AppDbContext();
        db.Database.EnsureCreated();
    }

    protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
        => optionsBuilder.UseSqlite($"Data Source={DbPath}");

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<TaskItem>().HasKey(t => t.Id);
        // 本地查询索引：待同步数据筛选、按更新时间排序
        modelBuilder.Entity<TaskItem>().HasIndex(t => t.IsPendingSync);
        modelBuilder.Entity<TaskItem>().HasIndex(t => t.UpdatedAt);
    }
}
