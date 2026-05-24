package com.nlu.voterservice.controller;

import com.nlu.voterservice.dto.VoterResponse;
import com.nlu.voterservice.entity.Voter;
import com.nlu.voterservice.repository.UpdateProfileRequest;
import com.nlu.voterservice.service.VoterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voter")
public class VoterController {

  @Autowired
  private VoterService voterService;


  @GetMapping("/profile")
  public ResponseEntity<?> getProfile(@RequestHeader("X-User-Email") String email) {
    try {
      Voter voter = voterService.findByEmail(email);
      return ResponseEntity.ok(voter);
    } catch (Exception e) {

      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
  // --- SỬA LẠI THÀNH @ModelAttribute ---
  @PutMapping("/profile")
  public ResponseEntity<?> updateProfile(
      @RequestHeader("X-User-Email") String email,
      @ModelAttribute UpdateProfileRequest request) { // Tuyệt đối KHÔNG dùng @RequestBody ở đây
    try {
      System.out.println(">>> [BE] Tiếp nhận FormData cập nhật hồ sơ cho email: " + email);
      System.out.println(">>> [BE] File avatar đi kèm: " + (request.getAvatar() != null ? request.getAvatar().getOriginalFilename() : "Không có"));

      Voter updatedVoter = voterService.updateProfile(email, request);
      return ResponseEntity.ok(updatedVoter);
    } catch (Exception e) {
      System.err.println(">>> [BE] Lỗi cập nhật hồ sơ: " + e.getMessage());
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}