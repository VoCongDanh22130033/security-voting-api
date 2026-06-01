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

  @PutMapping("/profile")
  public ResponseEntity<?> updateProfile(
      @RequestHeader("X-User-Email") String email,
      @ModelAttribute UpdateProfileRequest request) {
    try {
      System.out.println(">>> [BE] Tiếp nhận FormData cập nhật hồ sơ cho email: " + email);
      System.out.println(
          ">>> [BE] File avatar đi kèm: " + (request.getAvatar() != null ? request.getAvatar()
              .getOriginalFilename() : "Không có"));

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

  @GetMapping("/{id}")
  public ResponseEntity<?> getVoterById(@PathVariable("id") Long id) {
    try {
      return ResponseEntity.ok(voterService.getVoterById(id));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
  @PostMapping("/{id}/lock")
  public ResponseEntity<?> lockVoterAccount(@PathVariable("id") Long id) {
    try {
      voterService.lockAccount(id);
      return ResponseEntity.ok(Map.of("message", "Tài khoản đã bị khóa."));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/{id}/unlock")
  public ResponseEntity<?> unlockVoterAccount(@PathVariable("id") Long id) {
    try {
      voterService.unlockAccount(id);
      return ResponseEntity.ok(Map.of("message", "Tài khoản đã được mở khóa."));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  //  (roleId: 2 = voter, 3 = nguoi chu tri) ---
  @GetMapping("/admin/list")
  public ResponseEntity<?> listByRole(@RequestParam("role") Long roleId) {
    try {
      java.util.List<Voter> list = voterService.listVotersByRole(roleId);
      return ResponseEntity.ok(list);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  // --- ADMIN: Thay đổi role cho user ---
  @PostMapping("/admin/change-role/{id}")
  public ResponseEntity<?> changeRole(@PathVariable("id") Long id, @RequestParam("role") Long roleId) {
    try {
      Voter updated = voterService.changeUserRole(id, roleId);
      return ResponseEntity.ok(updated);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @GetMapping("/admin/hosts")
  public ResponseEntity<?> getAllHosts() {
    try {
      return ResponseEntity.ok(voterService.getAllUsersByRoleId(3L));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  // (Role ID = 2)
  @GetMapping("/admin/voters")
  public ResponseEntity<?> getAllVoters() {
    try {
      return ResponseEntity.ok(voterService.getAllUsersByRoleId(2L));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}