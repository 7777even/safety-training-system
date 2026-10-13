package com.sts.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.S3StorageGateway;
import com.sts.admin.storage.StorageObject;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.asset.entity.Asset;
import com.sts.asset.entity.AssetDerivative;
import com.sts.asset.mapper.AssetDerivativeMapper;
import com.sts.asset.mapper.AssetMapper;
import com.sts.asset.vo.AssetPlayInfoVO;
import com.sts.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class AssetMediaAccessService {
  /**
   * Excel 不转码，由前端表格组件直显；两个后缀归一为同一个格式标识。
   */
  private static final java.util.Set<String> EXCEL_SUFFIXES = java.util.Set.of("xls", "xlsx");

  private final AssetMapper assetMapper;
  private final AssetDerivativeMapper derivativeMapper;
  private final SysFileInfoMapper fileInfoMapper;
  private final S3StorageGateway storage;
  private final TenantObjectKeyService objectKeys;

  public AssetPlayInfoVO playInfo(Long assetId) {
    Asset asset = requireSucceededAsset(assetId);
    List<AssetDerivative> derivatives = derivativeMapper.selectList(
      new LambdaQueryWrapper<AssetDerivative>().eq(AssetDerivative::getAssetId, assetId)
        .eq(AssetDerivative::getStatus, 1));
    AssetDerivative primary = choosePrimary(asset, derivatives);
    AssetDerivative cover = derivatives.stream()
      .filter(item -> "THUMBNAIL".equals(item.getDerivativeType())).findFirst().orElse(null);
    String format;
    String url;
    if (primary != null) {
      if ("HLS".equals(primary.getDerivativeType())) {
        SysFileInfo file = fileInfoMapper.selectById(primary.getFileInfoId());
        String jobId = derivativeJobId(file.getObjectName());
        format = "hls";
        url = "/api/asset/files/" + assetId + "/hls/" + jobId + "/index.m3u8";
      } else {
        format = "pdf";
        url = "/api/asset/files/" + assetId + "/" + primary.getFileInfoId();
      }
    } else {
      // 无派生产物时必须有源文件才谈得上播放。外链与富文本的 fileInfoId 为空，
      // 此前会拼出 /api/asset/files/{id}/null 这种坏地址；它们按设计走
      // rich_content / external_url 渲染，不经本接口。
      if (asset.getFileInfoId() == null) {
        throw notFound();
      }
      format = formatWithoutDerivative(asset);
      url = "/api/asset/files/" + assetId + "/" + asset.getFileInfoId();
    }
    String coverUrl = cover == null ? null
      : "/api/asset/files/" + assetId + "/" + cover.getFileInfoId();
    return new AssetPlayInfoVO(assetId, format, url, coverUrl,
                               asset.getDurationSeconds(), asset.getPageCount());
  }

  public MediaObject file(Long assetId, Long fileInfoId, String rangeHeader) {
    Asset asset = requireSucceededAsset(assetId);
    boolean allowed = fileInfoId != null && fileInfoId.equals(asset.getFileInfoId());
    if (!allowed) {
      allowed = derivativeMapper.selectCount(new LambdaQueryWrapper<AssetDerivative>()
                                               .eq(AssetDerivative::getAssetId,
                                                   assetId).eq(AssetDerivative::getFileInfoId, fileInfoId)
                                               .eq(AssetDerivative::getStatus, 1)) > 0;
    }
    if (!allowed) throw notFound();
    SysFileInfo file = fileInfoMapper.selectById(fileInfoId);
    if (file == null) throw notFound();
    return load(file.getObjectName(), rangeHeader);
  }

  /**
   * 读取启用素材当前关联的原始上传文件，供完成上层业务授权后的受控下载使用。
   */
  public MediaObject sourceFile(Long assetId, Long fileInfoId, String rangeHeader) {
    Asset asset = assetMapper.selectById(assetId);
    if (asset == null || !Integer.valueOf(1).equals(asset.getStatus())
      || !"SUCCEEDED".equals(asset.getProcessStatus()) || !"UPLOAD".equals(asset.getSourceType())
      || fileInfoId == null || !fileInfoId.equals(asset.getFileInfoId())) {
      throw notFound();
    }
    SysFileInfo file = fileInfoMapper.selectById(fileInfoId);
    if (file == null || file.getObjectName() == null || file.getObjectName().isBlank()) {
      throw notFound();
    }
    return load(file.getObjectName(), rangeHeader);
  }

  public MediaObject hls(Long assetId, Long jobId, String name, String rangeHeader) {
    requireSucceededAsset(assetId);
    if (name == null || !name.matches("(?:index\\.m3u8|segment-[0-9]{5}\\.ts)")) throw notFound();
    String legacyManifest = "derivative/" + assetId + "/" + jobId + "/hls/index.m3u8";
    String tenantManifest = objectKeys.forCurrentTenant(legacyManifest);
    SysFileInfo manifestFile = fileInfoMapper.selectOne(new LambdaQueryWrapper<SysFileInfo>()
                                                          .eq(SysFileInfo::getObjectName, tenantManifest));
    if (manifestFile == null) {
      manifestFile = fileInfoMapper.selectOne(new LambdaQueryWrapper<SysFileInfo>()
                                                .eq(SysFileInfo::getObjectName, legacyManifest));
    }
    if (manifestFile == null || derivativeMapper.selectCount(new LambdaQueryWrapper<AssetDerivative>()
                                                               .eq(AssetDerivative::getAssetId, assetId).eq(
        AssetDerivative::getFileInfoId,
        manifestFile.getFileInfoId())
                                                               .eq(AssetDerivative::getDerivativeType, "HLS").eq(
        AssetDerivative::getStatus,
        1)) == 0) throw notFound();
    String manifestObjectName = manifestFile.getObjectName();
    String directory = manifestObjectName.substring(0, manifestObjectName.lastIndexOf('/') + 1);
    return load(directory + name, rangeHeader);
  }

  private static String derivativeJobId(String objectName) {
    if (objectName == null) throw notFound();
    String[] parts = objectName.split("/");
    for (int index = 0; index + 2 < parts.length; index++) {
      if ("derivative".equals(parts[index])) {
        return parts[index + 2];
      }
    }
    throw notFound();
  }

  private MediaObject load(String objectName, String rangeHeader) {
    try {
      long total = storage.objectSize(objectName);
      if (rangeHeader == null || rangeHeader.isBlank()) {
        StorageObject object = storage.getObject(objectName);
        return new MediaObject(object, total, null);
      }
      ByteRange range = ByteRangeParser.parse(rangeHeader, total);
      return new MediaObject(storage.getObjectRange(objectName, range.start(), range.end()), total, range);
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        throw notFound();
      }
      throw e;
    }
  }

  private Asset requireSucceededAsset(Long assetId) {
    // 归属条件由 TenantLineInnerInterceptor 注入；此前这里取"默认租户"（硬编码 DEMO），
    // 等于所有租户的播放请求都去鉴权 DEMO 的素材
    Asset asset = assetMapper.selectById(assetId);
    if (asset == null || !"SUCCEEDED".equals(asset.getProcessStatus())) throw notFound();
    return asset;
  }

  /**
   * 无派生产物时的格式标识。
   * <p>
   * 取值 MUST 落在契约枚举内，表达「这个 url 该用什么组件渲染」，
   * 而不是回显源文件后缀——后者是开放集合，且会把「转码失败的视频」
   * 伪装成「可直接播放的 mp4」。对照
   * {@code docs/architecture/asset-transcode-design.md} §5.1 类型矩阵与 §6.3 预览组件表。
   */
  private String formatWithoutDerivative(Asset asset) {
    if ("IMAGE".equals(asset.getAssetType())) {
      return "image";
    }
    if ("DOCUMENT".equals(asset.getAssetType())) {
      SysFileInfo source = fileInfoMapper.selectById(asset.getFileInfoId());
      String suffix = source == null || source.getFileSuffix() == null
        ? null : source.getFileSuffix().toLowerCase();
      if ("pdf".equals(suffix)) {
        return "pdf";
      }
      if (EXCEL_SUFFIXES.contains(suffix)) {
        return "excel";
      }
    }
    // 不支持预览的文档，以及音视频派生缺失的异常情况。
    // 视频/音频/Word 落到 original 一定是出了问题，可据此监控。
    return "original";
  }

  /**
   * 选择主播放派生。VIDEO 与 AUDIO 都只认 HLS；遗留 TRANSCODED 不作为可播格式。
   */
  private static AssetDerivative choosePrimary(Asset asset, List<AssetDerivative> derivatives) {
    if ("VIDEO".equals(asset.getAssetType()) || "AUDIO".equals(asset.getAssetType())) {
      return firstDerivative(derivatives, "HLS");
    }
    if ("DOCUMENT".equals(asset.getAssetType())) {
      return firstDerivative(derivatives, "PREVIEW");
    }
    return null;
  }

  private static AssetDerivative firstDerivative(List<AssetDerivative> derivatives, String type) {
    return derivatives.stream().filter(item -> type.equals(item.getDerivativeType())).findFirst().orElse(null);
  }

  private static BusinessException notFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, 6011, "素材或媒体文件不存在");
  }

  public record MediaObject(StorageObject object, long totalSize, ByteRange range) {
  }
}
