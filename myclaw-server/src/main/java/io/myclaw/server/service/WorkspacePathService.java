package io.myclaw.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.awt.GraphicsEnvironment;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorkspacePathService {

    private final List<Path> roots;
    private final Path defaultRoot;
    private final boolean allowAnyDirectory;
    private final Map<String, Set<Path>> userRoots = new ConcurrentHashMap<>();

    public WorkspacePathService(@Value("${myclaw.working-directory:.}") String defaultDirectory,
                                @Value("${myclaw.workspace.allowed-roots:.}") String allowedRoots,
                                @Value("${myclaw.workspace.allow-any-directory:false}") boolean allowAnyDirectory) {
        this.allowAnyDirectory = allowAnyDirectory;
        try {
            this.defaultRoot = Path.of(defaultDirectory).toAbsolutePath().normalize().toRealPath();
            LinkedHashSet<Path> configured = new LinkedHashSet<>();
            configured.add(defaultRoot);
            for (String value : allowedRoots.split(";"))
                if (!value.isBlank()) configured.add(Path.of(value.strip()).toAbsolutePath().normalize().toRealPath());
            this.roots = List.copyOf(configured);
        } catch (IOException e) {
            throw new IllegalStateException("配置的工作目录不存在或不可访问", e);
        }
    }

    public Path resolve(String requested) {
        String value = requested == null || requested.isBlank() ? "." : requested.strip();
        Path candidate = Path.of(value);
        candidate = (candidate.isAbsolute() ? candidate : defaultRoot.resolve(candidate)).normalize();
        try {
            Path real = candidate.toRealPath();
            if (!isAllowed(real)) throw new SecurityException("工作目录不在允许目录列表中");
            if (!Files.isDirectory(real)) throw new IllegalArgumentException("工作目录不是文件夹: " + value);
            return real;
        } catch (SecurityException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("工作目录不存在或不可访问: " + value, e);
        }
    }

    public String selectDirectory() {
        Path selected = isWindows() ? selectWindowsDirectory() : selectSwingDirectory();
        if (selected == null) return null;
        userRoots.computeIfAbsent(owner(), key -> ConcurrentHashMap.newKeySet()).add(selected);
        return display(selected);
    }

    private Path selectWindowsDirectory() {
        String script = "$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';$InformationPreference='SilentlyContinue';$OutputEncoding=[Console]::OutputEncoding=[Text.UTF8Encoding]::new();" +
                "Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; " +
                "public static class DpiAware { [DllImport(\"user32.dll\")] " +
                "public static extern bool SetProcessDpiAwarenessContext(IntPtr value); }';" +
                "[DpiAware]::SetProcessDpiAwarenessContext([IntPtr](-4))|Out-Null;" +
                "Add-Type -AssemblyName System.Windows.Forms;" +
                "$dialog=New-Object System.Windows.Forms.OpenFileDialog;" +
                "$dialog.Title='Select Workspace Directory';" +
                "$dialog.InitialDirectory='" + defaultRoot.toString().replace("'", "''") + "';" +
                "$dialog.AutoUpgradeEnabled=$true;" +
                "$dialog.RestoreDirectory=$true;" +
                "$dialog.ValidateNames=$false;" +
                "$dialog.CheckFileExists=$false;" +
                "$dialog.CheckPathExists=$true;" +
                "$dialog.FileName='选择此文件夹';" +
                "if($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK){" +
                "$path=[IO.Path]::GetDirectoryName($dialog.FileName);[Console]::Write($path)}";
        try {
            String encodedScript = java.util.Base64.getEncoder().encodeToString(
                    script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
            Process process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-STA",
                    "-OutputFormat", "Text", "-EncodedCommand", encodedScript).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
            String errorOutput = new String(process.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
            int exitCode = process.waitFor();
            if (exitCode != 0) throw new IllegalStateException("系统目录选择器执行失败: " + errorOutput);
            if (output.isBlank()) return null;
            try {
                Path selected = Path.of(output).toRealPath();
                if (!Files.isDirectory(selected)) throw new IllegalStateException("系统目录选择器未返回文件夹: " + output);
                return selected;
            } catch (RuntimeException invalidOutput) {
                throw new IllegalStateException("系统目录选择器返回了无效路径: " + output, invalidOutput);
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法启动 Windows 目录选择器", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("目录选择操作被中断", e);
        }
    }

    private Path selectSwingDirectory() {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("当前后端运行在无桌面环境，无法打开系统目录选择器");
        final Path[] selected = new Path[1];
        final RuntimeException[] failure = new RuntimeException[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                javax.swing.UIManager.put("FileChooser.useShellFolder", Boolean.FALSE);
                JFileChooser chooser = new JFileChooser(defaultRoot.toFile());
                chooser.putClientProperty("FileChooser.useShellFolder", Boolean.FALSE);
                chooser.setDialogTitle("选择工作区目录");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    try { selected[0] = chooser.getSelectedFile().toPath().toRealPath(); }
                    catch (IOException e) { failure[0] = new IllegalArgumentException("选择的目录不可访问", e); }
                }
            });
        } catch (Exception e) { throw new IllegalStateException("无法打开系统目录选择器", e); }
        if (failure[0] != null) throw failure[0];
        return selected[0];
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }
    private boolean isAllowed(Path path) {
        if (allowAnyDirectory || roots.stream().anyMatch(path::startsWith)) return true;
        return userRoots.getOrDefault(owner(), Set.of()).stream().anyMatch(path::startsWith);
    }

    private String owner() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }

    public Path root() {
        return defaultRoot;
    }

    public DirectoryView browse(String requested) {
        if ("__roots__".equals(requested)) return rootsView();
        Path current = resolve(requested);
        try (var children = Files.list(current)) {
            List<DirectoryEntry> directories = children.filter(Files::isDirectory).map(path -> {
                try {
                    Path real = path.toRealPath();
                    return allowAnyDirectory || roots.stream().anyMatch(real::startsWith) ? new DirectoryEntry(path.getFileName().toString(), display(real)) : null;
                } catch (IOException ignored) {
                    return null;
                }
            }).filter(Objects::nonNull).sorted(Comparator.comparing(DirectoryEntry::name, String.CASE_INSENSITIVE_ORDER)).toList();
            Path owningRoot = roots.stream().filter(current::startsWith).max(Comparator.comparingInt(Path::getNameCount)).orElse(defaultRoot);
            String parent = current.getParent() == null || (!allowAnyDirectory && current.equals(owningRoot)) ? "__roots__" : display(current.getParent());
            return new DirectoryView(display(current), parent, directories, allowAnyDirectory || roots.size() > 1);
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取工作目录: " + requested, e);
        }
    }

    private String display(Path path) {
        return path.toString().replace('\\', '/');
    }

    private DirectoryView rootsView() {
        List<Path> available = allowAnyDirectory ? new ArrayList<>() : roots;
        if (allowAnyDirectory) Path.of(".").getFileSystem().getRootDirectories().forEach(available::add);
        return new DirectoryView("__roots__", null, available.stream().filter(Files::isReadable).map(p -> new DirectoryEntry(p.getFileName() == null ? p.toString() : p.getFileName().toString(), display(p))).toList(), allowAnyDirectory || roots.size() > 1);
    }

    public record DirectoryEntry(String name, String path) {
    }

    public record DirectoryView(String current, String parent, List<DirectoryEntry> directories,
                                boolean canBrowseRoots) {
    }
}