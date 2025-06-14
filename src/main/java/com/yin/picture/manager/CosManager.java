package com.yin.picture.manager;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.yin.picture.config.CosClientConfig;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
public class CosManager {

    private final CosClientConfig cosClientConfig;

    private final COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        return cosClient.putObject(putObjectRequest);
    }
    /**
     * 上传对象（附带图片信息）
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, InputStream file, ObjectMetadata metadata) {
        // 原始上传请求的构建与您原来的一致
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file,  metadata);

        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1);

        List<PicOperations.Rule> rules= new ArrayList<>();
        String webpKey = FileUtil.mainName(key)+".webp";
        PicOperations.Rule compressRule = new PicOperations.Rule();
        // rule 规则是 imageMogr2/format/webp，表示将图片处理成 webp 格式
        compressRule.setRule("imageMogr2/format/webp");
        // bucket 是 cosClientConfig.getBucket()
        compressRule.setBucket(cosClientConfig.getBucket());
        // fileid 是 webpKey，表示处理后的图片保存的路径和文件名
        compressRule.setFileId(webpKey);
        rules.add(compressRule);
        // 缩略图处理，仅对 > 20 KB 的图片生成缩略图
        if (metadata.getContentLength()> 2 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            thumbnailRule.setBucket(cosClientConfig.getBucket());
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
            thumbnailRule.setFileId(thumbnailKey);
            // 缩放规则 /thumbnail/<Width>x<Height>>（如果大于原图宽高，则不处理）
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 128, 128));
            rules.add(thumbnailRule);
        }


        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);

        PutObjectResult putResult = null;
        putResult = cosClient.putObject(putObjectRequest);
        return putResult;

    }
    /**
     * 删除对象
     *
     * @param key 文件 key
     */
    public void deleteObject(String key) throws CosClientException {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }

}
