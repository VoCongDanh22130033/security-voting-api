package com.nlu.electionservice.service;


import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

  @Autowired
  private Cloudinary cloudinary;

  public String uploadFile(MultipartFile file) {
    try {
      // Upload và tự động xác định loại file
      Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap("resource_type", "auto"));
      return uploadResult.get("url").toString();
    } catch (IOException e) {
      throw new RuntimeException("Lỗi khi upload ảnh lên Cloudinary: " + e.getMessage());
    }
  }


}