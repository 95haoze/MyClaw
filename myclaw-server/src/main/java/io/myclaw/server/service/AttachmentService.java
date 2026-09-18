package io.myclaw.server.service;
import io.myclaw.server.persistence.entity.AttachmentEntity;
import io.myclaw.server.persistence.repository.AttachmentRepository;
import io.myclaw.server.persistence.repository.ChatSessionRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Service
public class AttachmentService {
    public static final long MAX = 10 * 1024 * 1024;
    private static final int MAX_TEXT = 100_000;
    private static final Set<String> TYPES = Set.of("text/plain", "text/markdown", "application/json", "text/csv",
            "application/pdf", "image/png", "image/jpeg", "image/webp", "image/gif");
    private final AttachmentRepository repo;
    private final ChatSessionRepository sessions;
    private final Path root;
    public AttachmentService(AttachmentRepository repo, ChatSessionRepository sessions,
                             @Value("${myclaw.server.upload-directory:./data/uploads}") String dir) throws IOException {
        this.repo = repo; this.sessions = sessions;
        root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }
    public record View(String id, String name, String contentType, long sizeBytes, String previewUrl, String extractedText) {}
    public View upload(String sessionId, MultipartFile file) throws IOException {
        if (sessions.findByIdAndOwnerEmail(sessionId, owner()).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        if (file.isEmpty()) throw new IllegalArgumentException("文件不能为空");
        if (file.getSize() > MAX) throw new IllegalArgumentException("文件不能超过 10 MB");
        String type = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        if (!TYPES.contains(type)) throw new IllegalArgumentException("不支持的文件类型");
        String id = UUID.randomUUID().toString();
        Path path = root.resolve(id).normalize();
        if (!path.startsWith(root)) throw new SecurityException("非法文件路径");
        file.transferTo(path);
        String text = extractText(path, type);
        return view(repo.save(new AttachmentEntity(id, owner(), sessionId, safe(file.getOriginalFilename()), type,
                file.getSize(), path.toString(), text)));
    }
    private static String extractText(Path path, String type) throws IOException {
        String text = "";
        if (type.startsWith("text/") || type.equals("application/json")) text = Files.readString(path, StandardCharsets.UTF_8);
        else if (type.equals("application/pdf")) {
            try (PDDocument document = Loader.loadPDF(path.toFile())) { text = new PDFTextStripper().getText(document); }
        }
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }
    public Resource content(String id) { return new FileSystemResource(require(id).getStoragePath()); }
    public AttachmentEntity require(String id) {
        return repo.findByIdAndOwnerEmail(id, owner()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在"));
    }
    public void delete(String id) throws IOException {
        var item = require(id); Files.deleteIfExists(Path.of(item.getStoragePath())); repo.delete(item);
    }
    public void deleteForMessages(List<Long> messageIds) {
        for (var item : repo.findByMessageIds(messageIds)) {
            try { Files.deleteIfExists(Path.of(item.getStoragePath())); }
            catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
            repo.delete(item);
        }
    }

    @Transactional
    public void linkToMessage(String sessionId, Long messageId, List<String> ids) {
        if (ids == null) return;
        for (String id : ids) {
            var item = require(id);
            if (!item.getSessionId().equals(sessionId)) throw new SecurityException("附件不属于当前会话");
            item.linkToMessage(messageId);
            repo.save(item);
        }
    }
    @Transactional(readOnly = true)
    public List<View> viewsForMessage(Long messageId) {
        return repo.findByMessageIdAndOwnerEmailOrderByCreatedAt(messageId, owner()).stream().map(this::view).toList();
    }
    public String context(String sessionId, List<String> ids) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder out = new StringBuilder("\n\n[附件上下文]\n");
        for (String id : ids) {
            var item = require(id);
            if (!item.getSessionId().equals(sessionId)) throw new SecurityException("附件不属于当前会话");
            out.append("文件: ").append(item.getOriginalName()).append(" (").append(item.getContentType()).append(")\n");
            if (item.getExtractedText() != null && !item.getExtractedText().isBlank()) out.append(item.getExtractedText()).append("\n");
            else out.append("[该附件没有可提取的文本内容]\n");
        }
        return out.toString();
    }
    private View view(AttachmentEntity item) {
        return new View(item.getId(), item.getOriginalName(), item.getContentType(), item.getSizeBytes(),
                "/api/attachments/" + item.getId() + "/content", item.getExtractedText());
    }
    private static String safe(String name) { return name == null ? "attachment" : Path.of(name).getFileName().toString(); }
    private static String owner() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}