package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    @Value("${app.upload-dir:${user.dir}/uploads}")
    private String uploadDir;

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            if (image == null || image.isEmpty() || image.getSize() > 5 * 1024 * 1024) {
                return Result.fail("文件不能为空且大小不能超过5MB");
            }
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            String suffix = StrUtil.subAfter(originalFilename, ".", true).toLowerCase(java.util.Locale.ROOT);
            if (!java.util.Arrays.asList("jpg", "jpeg", "png", "gif", "webp").contains(suffix)) {
                return Result.fail("仅支持 jpg、jpeg、png、gif、webp 图片");
            }
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(uploadDir, fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @PostMapping("review")
    public Result uploadReviewImage(@RequestParam("file") MultipartFile image) {
        return uploadImage(image);
    }

    @DeleteMapping("/{name:.+}")
    public Result deleteImage(@PathVariable String name) {
        File file = resolveSafe(name);
        if (file == null || file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    @DeleteMapping("/**")
    public Result deleteNestedImage(HttpServletRequest request) {
        String prefix = request.getContextPath() + "/upload/";
        String path = request.getRequestURI().startsWith(prefix)
                ? request.getRequestURI().substring(prefix.length()) : "";
        File file = resolveSafe(path);
        if (file == null || file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    @GetMapping("/{name:.+}")
    public org.springframework.http.ResponseEntity<byte[]> getImage(@PathVariable String name) {
        return readImage(name);
    }

    @GetMapping("/**")
    public org.springframework.http.ResponseEntity<byte[]> getNestedImage(HttpServletRequest request) {
        String prefix = request.getContextPath() + "/upload/";
        String path = request.getRequestURI().startsWith(prefix)
                ? request.getRequestURI().substring(prefix.length()) : "";
        return readImage(path);
    }

    private org.springframework.http.ResponseEntity<byte[]> readImage(String name) {
        File file = resolveSafe(name);
        if (file == null) return org.springframework.http.ResponseEntity.badRequest().build();
        try {
            if (!file.isFile()) return org.springframework.http.ResponseEntity.notFound().build();
            String contentType = Files.probeContentType(file.toPath());
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (contentType != null) headers.set("Content-Type", contentType);
            return new org.springframework.http.ResponseEntity<>(Files.readAllBytes(file.toPath()), headers,
                    org.springframework.http.HttpStatus.OK);
        } catch (IOException e) {
            return org.springframework.http.ResponseEntity.status(500).build();
        }
    }

    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(uploadDir, StrUtil.format("blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }

    private File resolveSafe(String name) {
        if (StrUtil.isBlank(name)) return null;
        File root = new File(uploadDir).getAbsoluteFile();
        File file = new File(root, name).getAbsoluteFile();
        try {
            return file.toPath().normalize().startsWith(root.toPath().normalize()) ? file : null;
        } catch (Exception e) {
            return null;
        }
    }
}
