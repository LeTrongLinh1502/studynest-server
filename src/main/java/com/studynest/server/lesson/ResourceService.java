package com.studynest.server.lesson;

import com.studynest.server.common.CurrentUser;
import com.studynest.server.common.DbRows;
import com.studynest.server.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;

@Service
public class ResourceService {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final String SELECT = "SELECT id, lesson_id AS lessonId, resource_type AS resourceType, title, "
            + "external_url AS externalUrl, original_name AS originalName, mime_type AS mimeType, "
            + "size_bytes AS sizeBytes, sort_order AS sortOrder, created_at AS createdAt FROM lesson_resources WHERE id=?";
    private final Path root;
    private final DbRows db;
    private final LessonService lessons;
    private final CurrentUser currentUser;

    public ResourceService(@Value("${studynest.upload-dir}") String uploadDir, DbRows db,
                           LessonService lessons, CurrentUser currentUser) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.db = db; this.lessons = lessons; this.currentUser = currentUser;
    }

    @Transactional
    public Map<String,Object> addLink(Authentication auth, long lessonId, String type, String title, String url) {
        requireEditable(auth, lessonId);
        if (!"VIDEO_LINK".equals(type) && !"EXTERNAL_LINK".equals(type)) throw ApiException.badRequest("Invalid resource type");
        validateUrl(url);
        long id = db.insert("INSERT INTO lesson_resources (lesson_id,resource_type,title,external_url,sort_order) VALUES (?,?,?,?,?)",
                lessonId, type, title.trim(), url.trim(), nextSort(lessonId));
        return db.one(SELECT, id);
    }

    @Transactional
    public Map<String,Object> addFile(Authentication auth, long lessonId, String title, MultipartFile file) {
        requireEditable(auth, lessonId);
        if (title == null || title.isBlank() || title.length() > 200) throw ApiException.badRequest("Invalid resource title");
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw ApiException.badRequest("File must be 1 byte to 20 MB");
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().replace('\\', '/');
        original = original.substring(original.lastIndexOf('/') + 1);
        original = original.replaceAll("[\\p{Cntrl}]", "_");
        if (original.isBlank() || original.length() > 255) throw ApiException.badRequest("Invalid filename");
        String lower = original.toLowerCase(Locale.ROOT);
        String ext = lower.endsWith(".pdf") ? ".pdf" : lower.endsWith(".docx") ? ".docx" :
                lower.endsWith(".pptx") ? ".pptx" : null;
        if (ext == null) throw ApiException.badRequest("Only PDF, DOCX and PPTX are accepted");
        String mime = switch (ext) {
            case ".pdf" -> "application/pdf";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        };
        String key = UUID.randomUUID() + ext;
        Path path = storedPath(key);
        try {
            Files.createDirectories(root);
            try (var input = file.getInputStream()) { Files.copy(input, path); }
            validateContent(path, ext);
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(path);
            if (exception instanceof ApiException api) throw api;
            throw ApiException.badRequest("Unable to save or read the uploaded file");
        }
        registerRollbackDelete(path);
        long id = db.insert("INSERT INTO lesson_resources (lesson_id,resource_type,title,storage_key,original_name,mime_type,size_bytes,sort_order) "
                + "VALUES (?,'FILE',?,?,?,?,?,?)", lessonId, title.trim(), key, original, mime, file.getSize(), nextSort(lessonId));
        return db.one(SELECT, id);
    }

    @Transactional
    public Map<String,Object> reorder(Authentication auth, long lessonId, long id, String title, int sortOrder) {
        requireEditable(auth, lessonId);
        if (sortOrder < 0) throw ApiException.badRequest("sortOrder must be nonnegative");
        requireResourceInLesson(lessonId, id);
        db.update("UPDATE lesson_resources SET title=?,sort_order=? WHERE id=?", title.trim(), sortOrder, id);
        return db.one(SELECT, id);
    }

    @Transactional
    public void delete(Authentication auth, long lessonId, long id) {
        requireEditable(auth, lessonId);
        Map<String,Object> resource = requireResourceInLesson(lessonId, id);
        String key = db.one("SELECT storage_key AS storageKey FROM lesson_resources WHERE id=?", id).get("storageKey") instanceof String s ? s : null;
        db.update("DELETE FROM lesson_resources WHERE id=?", id);
        if ("FILE".equals(resource.get("resourceType")) && key != null) registerCommitDelete(storedPath(key));
    }

    @Transactional
    public void hideForModeration(long id) {
        Map<String,Object> resource = db.one(SELECT, id);
        Map<String,Object> row = db.one("SELECT storage_key AS storageKey FROM lesson_resources WHERE id=?", id);
        db.update("DELETE FROM lesson_resources WHERE id=?", id);
        if ("FILE".equals(resource.get("resourceType")) && row.get("storageKey") instanceof String key)
            registerCommitDelete(storedPath(key));
    }

    public ResponseEntity<FileSystemResource> download(Authentication auth, long resourceId) {
        return serve(auth, resourceId, false);
    }

    public ResponseEntity<FileSystemResource> preview(Authentication auth, long resourceId) {
        return serve(auth, resourceId, true);
    }

    private ResponseEntity<FileSystemResource> serve(Authentication auth, long resourceId, boolean inline) {
        Map<String,Object> resource = db.one(SELECT, resourceId);
        long lessonId = ((Number) resource.get("lessonId")).longValue();
        lessons.requireVisible(currentUser.get(auth), lessonId);
        if (!"FILE".equals(resource.get("resourceType"))) throw ApiException.badRequest("Resource is a link, not a file");
        if (inline && !"application/pdf".equals(resource.get("mimeType")))
            throw ApiException.badRequest("Only PDF files can be previewed");
        Map<String,Object> row = db.one("SELECT storage_key AS storageKey FROM lesson_resources WHERE id=?", resourceId);
        Path path = storedPath((String) row.get("storageKey"));
        if (!Files.isRegularFile(path)) throw ApiException.notFound("File not found");
        String filename = (String) resource.get("originalName");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType((String) resource.get("mimeType")))
                .contentLength(((Number) resource.get("sizeBytes")).longValue())
                .body(new FileSystemResource(path));
    }

    private void requireEditable(Authentication auth, long lessonId) {
        Map<String,Object> lesson = lessons.requireOwner(auth, lessonId);
        if ("ARCHIVED".equals(lesson.get("status"))) throw ApiException.conflict("Lesson is archived");
    }

    private Map<String,Object> requireResourceInLesson(long lessonId, long id) {
        Map<String,Object> resource = db.one(SELECT, id);
        if (((Number) resource.get("lessonId")).longValue() != lessonId) throw ApiException.notFound("Resource not found");
        return resource;
    }

    private int nextSort(long lessonId) {
        return ((Number) db.one("SELECT COALESCE(MAX(sort_order),-1)+1 AS nextSort FROM lesson_resources WHERE lesson_id=?", lessonId)
                .get("nextSort")).intValue();
    }

    private Path storedPath(String key) {
        if (key == null || !key.matches("[0-9a-f-]{36}\\.(pdf|docx|pptx)")) throw ApiException.badRequest("Invalid storage key");
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw ApiException.forbidden();
        return path;
    }

    private static void validateUrl(String raw) {
        try {
            URI uri = URI.create(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw ApiException.badRequest("Link must be a valid HTTPS URL");
        } catch (IllegalArgumentException exception) { throw ApiException.badRequest("Link must be a valid HTTPS URL"); }
    }

    private static void validateContent(Path path, String ext) throws IOException {
        byte[] header;
        try (var stream = Files.newInputStream(path)) { header = stream.readNBytes(5); }
        if (".pdf".equals(ext)) {
            if (header.length < 5 || !"%PDF-".equals(new String(header, StandardCharsets.US_ASCII)))
                throw ApiException.badRequest("File content is not PDF");
        } else {
            if (header.length < 4 || header[0] != 'P' || header[1] != 'K') throw ApiException.badRequest("File is not an Office document");
            try (ZipFile zip = new ZipFile(path.toFile())) {
                String marker = ".docx".equals(ext) ? "word/document.xml" : "ppt/presentation.xml";
                if (zip.getEntry("[Content_Types].xml") == null || zip.getEntry(marker) == null)
                    throw ApiException.badRequest("File is not the expected Office document");
            }
        }
    }

    private static void registerRollbackDelete(Path path) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteQuietly(path);
            }
        });
    }

    private static void registerCommitDelete(Path path) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deleteQuietly(path); }
        });
    }

    private static void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
}
