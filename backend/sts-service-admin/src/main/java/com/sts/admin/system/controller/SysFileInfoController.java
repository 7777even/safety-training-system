package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.aop.annotation.BusinessType;
import com.sts.admin.aop.annotation.OperLog;
import com.sts.admin.system.dto.FileQuery;
import com.sts.admin.system.service.SysFileInfoService;
import com.sts.admin.system.vo.FileInfoVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 系统文件上传、查询及删除接口。
 */
@RestController
@RequestMapping("/system/files")
@RequiredArgsConstructor
public class SysFileInfoController {

    private final SysFileInfoService fileInfoService;

  /**
   * 分页查询文件。
   */
    @GetMapping
    @SaCheckPermission("sys:file:list")
    public PageResult<FileInfoVO> pageFiles(PageRequest<FileQuery> request) {
        return fileInfoService.pageFiles(request);
    }

  /**
   * 上传文件并保存文件信息。
   */
  @PostMapping
    @SaCheckPermission("sys:file:upload")
    @OperLog(title = "文件管理", businessType = BusinessType.INSERT)
    public FileInfoVO upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "folder", defaultValue = "general") String folder) {
        return fileInfoService.upload(file, folder);
    }

    /**
     * 删除文件。
     */
    @DeleteMapping("/{fileInfoId}")
    @SaCheckPermission("sys:file:delete")
    @OperLog(title = "文件管理", businessType = BusinessType.DELETE)
    public void deleteFile(@PathVariable Long fileInfoId) {
        fileInfoService.deleteFile(fileInfoId);
    }

    /**
     * 批量删除系统文件。
     */
    @DeleteMapping
    @SaCheckPermission("sys:file:delete")
    @OperLog(title = "文件管理", businessType = BusinessType.DELETE)
    public void batchDelete(@RequestBody List<Long> ids) {
        fileInfoService.batchDelete(ids);
    }
}
