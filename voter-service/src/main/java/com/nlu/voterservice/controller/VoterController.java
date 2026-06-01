package com.nlu.voterservice.controller;


import com.nlu.voterservice.dto.ResetPasswordWithOtpRequest;
import com.nlu.voterservice.entity.Voter;
import com.nlu.voterservice.dto.ForgotPasswordRequest;

import com.nlu.voterservice.dto.UpdateProfileRequest;
import com.nlu.voterservice.service.VoterService;
import java.util.Map;
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

  @PostMapping("/forgot-password")
  public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
    try {
      voterService.sendOtpForgotPassword(request.getEmail());
      return ResponseEntity.ok(Map.of("message", "Mã OTP đã được gửi về Email của bạn."));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/reset-password-otp")
  public ResponseEntity<?> resetPasswordWithOtp(@RequestBody ResetPasswordWithOtpRequest request) {
    try {
      voterService.resetPasswordWithOtp(request);
      return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công!"));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  // --- ADMIN / MANAGEMENT: Xem chi tiết cử tri theo userId ---
  @GetMapping("/{id}")
  public ResponseEntity<?> getVoterById(@PathVariable("id") Long id) {
    try {
      Voter voter = voterService.getVoterById(id);
      return ResponseEntity.ok(voter);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  // --- ADMIN / MANAGEMENT: Khóa tài khoản cử tri ---
  @PostMapping("/{id}/lock")
  public ResponseEntity<?> lockVoterAccount(@PathVariable("id") Long id) {
    try {
      voterService.lockAccount(id);
      return ResponseEntity.ok(Map.of("message", "Tài khoản đã bị khóa."));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}