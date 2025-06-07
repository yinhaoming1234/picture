package com.yin.picture.controller;

import com.yin.picture.annotation.AuthCheck;
import com.yin.picture.common.BaseResponse;
import com.yin.picture.common.ResultUtils;
import com.yin.picture.constant.UserConstant;
import com.yin.picture.model.dto.picture.PictureUploadRequest;
import com.yin.picture.model.entity.User;
import com.yin.picture.model.vo.PictureVO;
import com.yin.picture.service.impl.PictureServiceImpl;
import com.yin.picture.service.impl.UserServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/picture")
@AllArgsConstructor
public class PictureController {
    private final UserServiceImpl userService;
    private final PictureServiceImpl pictureService;


    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

}
