package com.yin.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yin.picture.model.dto.picture.PictureQueryRequest;
import com.yin.picture.model.dto.picture.PictureReviewRequest;
import com.yin.picture.model.dto.picture.PictureUploadRequest;
import com.yin.picture.model.entity.Picture;
import com.yin.picture.model.entity.User;
import com.yin.picture.model.vo.PictureVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.multipart.MultipartFile;

/**
* @author hao
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-06-07 16:12:23
*/
public interface PictureService extends IService<Picture> {
    /**
     * 上传图片
     *
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(MultipartFile multipartFile,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);


    @Async
    void clearPictureFile(Picture oldPicture);

    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    Page<PictureVO> getPictureVOPageByPicture(Page<Picture> picturePage, HttpServletRequest request);

    void validPicture(Picture picture);

    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    void fillReviewParams(Picture picture, User loginUser);



    Page<Picture> getPicturePage(PictureQueryRequest pictureQueryRequest);
}
