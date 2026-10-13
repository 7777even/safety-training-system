package com.sts.asset.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.asset.service.AssetMediaAccessService;
import com.sts.asset.service.UnsatisfiableRangeException;
import com.sts.asset.vo.AssetPlayInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 素材播放信息及媒体文件流式访问接口。
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class AssetMediaController {
  private final AssetMediaAccessService mediaService;

  /**
   * 查询素材播放信息。
   */
  @GetMapping("/asset/assets/{assetId}/play-info")
  @SaCheckPermission("asset:asset:list")
  public AssetPlayInfoVO playInfo(@PathVariable Long assetId) {
    return mediaService.playInfo(assetId);
  }

  /**
   * 按字节范围读取素材媒体文件。
   */
  @GetMapping("/asset/files/{assetId}/{fileInfoId}")
  @SaCheckPermission("asset:asset:list")
  public ResponseEntity<StreamingResponseBody> file(
    @PathVariable Long assetId, @PathVariable Long fileInfoId,
    @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
    return response(() -> mediaService.file(assetId, fileInfoId, range));
  }

  /**
   * 读取素材的 HLS 播放文件。
   */
  @GetMapping("/asset/files/{assetId}/hls/{jobId}/{name}")
  @SaCheckPermission("asset:asset:list")
  public ResponseEntity<StreamingResponseBody> hls(
    @PathVariable Long assetId, @PathVariable Long jobId, @PathVariable String name,
    @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
    return response(() -> mediaService.hls(assetId, jobId, name, range));
  }

  /**
   * 构造支持分段读取的媒体响应。
   */
  private ResponseEntity<StreamingResponseBody> response(MediaSupplier supplier) {
    try {
      var media = supplier.get();
      var object = media.object();
      HttpHeaders headers = new HttpHeaders();
      headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
      headers.setContentLength(object.size());
      headers.setContentType(safeContentType(object.contentType()));
      if (media.range() != null) {
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + media.range().start() + "-"
          + media.range().end() + "/" + media.totalSize());
      }
      StreamingResponseBody body = output -> {
        try (var input = object.inputStream()) {
          input.transferTo(output);
        }
      };
      return new ResponseEntity<>(body, headers,
                                  media.range() == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT);
    } catch (UnsatisfiableRangeException e) {
      HttpHeaders headers = new HttpHeaders();
      headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + e.size());
      return new ResponseEntity<>(null, headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }
  }

  /**
   * 解析安全的媒体内容类型。
   */
  private static MediaType safeContentType(String value) {
    if (value == null || !value.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    return MediaType.parseMediaType(value);
  }

  @FunctionalInterface
  private interface MediaSupplier {
    /**
     * 获取待输出的媒体对象。
     */
    AssetMediaAccessService.MediaObject get();
  }
}
