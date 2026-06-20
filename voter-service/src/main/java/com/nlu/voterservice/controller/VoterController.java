package com.nlu.voterservice.controller;

import com.nlu.voterservice.dto.ForgotPasswordRequest;
import com.nlu.voterservice.dto.ResetPasswordWithOtpRequest;
import com.nlu.voterservice.dto.UpdateProfileRequest;
import com.nlu.voterservice.entity.Voter;
import com.nlu.voterservice.service.AuditLogClient;
import com.nlu.voterservice.service.VoterService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/voter")
public class VoterController {

  @Autowired
  private VoterService voterService;

  @Autowired
  private AuditLogClient auditLogClient;

  @GetMapping("/profile")
  public ResponseEntity<?> getProfile(@RequestHeader("X-User-Email") String email) {
    try {
      return ResponseEntity.ok(voterService.getProfileByEmail(email));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PutMapping("/profile")
  public ResponseEntity<?> updateProfile(
      @RequestHeader("X-User-Email") String email,
      @ModelAttribute UpdateProfileRequest request) {
    try {
      java.util.Map<String, Object> updated = voterService.updateProfile(email, request);
      auditLogClient.log(email, "PROFILE_UPDATED", "Cập nhật thông tin cá nhân");
      return ResponseEntity.ok(updated);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
    try {
      voterService.sendOtpForgotPassword(request.getEmail());
      auditLogClient.log(request.getEmail(), "PASSWORD_RESET_OTP_REQUESTED", "Yêu cầu đặt lại mật khẩu qua OTP");
      return ResponseEntity.ok(Map.of("message", "Mã OTP đã được gửi về email."));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/verify-otp")
  public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {
    try {
      log.info(">>> [verify-otp] email={} otp={}", body.get("email"), body.get("otpCode"));
      voterService.verifyOtp(body.get("email"), body.get("otpCode"));
      return ResponseEntity.ok(Map.of("message", "OTP hợp lệ."));
    } catch (Exception e) {
      log.error(">>> [verify-otp] ERROR: {} - {}", e.getClass().getName(), e.getMessage());
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/reset-password-otp")
  public ResponseEntity<?> resetPasswordWithOtp(@RequestBody ResetPasswordWithOtpRequest request) {
    try {
      voterService.resetPasswordWithOtp(request);
      auditLogClient.log(request.getEmail(), "PASSWORD_RESET_SUCCESS", "Đặt lại mật khẩu thành công");
      return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công."));
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
  public ResponseEntity<?> lockVoterAccount(
      @PathVariable("id") Long id,
      @RequestHeader(value = "X-User-Email", required = false) String actorEmail) {
    try {
      voterService.lockAccount(id);
      auditLogClient.log(actorEmail, "ACCOUNT_LOCKED", "Khóa tài khoản người dùng ID: " + id);
      return ResponseEntity.ok(Map.of("message", "Tài khoản đã bị khóa."));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/{id}/unlock")
  public ResponseEntity<?> unlockVoterAccount(
      @PathVariable("id") Long id,
      @RequestHeader(value = "X-User-Email", required = false) String actorEmail) {
    try {
      voterService.unlockAccount(id);
      auditLogClient.log(actorEmail, "ACCOUNT_UNLOCKED", "Mở khóa tài khoản người dùng ID: " + id);
      return ResponseEntity.ok(Map.of("message", "Tài khoản đã được mở khóa."));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @GetMapping("/admin/list")
  public ResponseEntity<?> listByRole(@RequestParam("role") Long roleId) {
    try {
      java.util.List<Voter> list = voterService.listVotersByRole(roleId);
      return ResponseEntity.ok(list);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/admin/change-role/{id}")
  public ResponseEntity<?> changeRole(
      @PathVariable("id") Long id,
      @RequestParam("role") Long roleId,
      @RequestHeader(value = "X-User-Email", required = false) String actorEmail) {
    try {
      Voter updated = voterService.changeUserRole(id, roleId);
      auditLogClient.log(actorEmail, "ACCOUNT_ROLE_CHANGED", "Đổi vai trò người dùng ID " + id + " sang vai trò ID " + roleId);
      return ResponseEntity.ok(updated);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteUser(
      @PathVariable("id") Long id,
      @RequestHeader(value = "X-User-Email", required = false) String actorEmail) {
    try {
      voterService.deleteUser(id);
      auditLogClient.log(actorEmail, "ACCOUNT_DELETED", "Xóa tài khoản người dùng ID: " + id);
      return ResponseEntity.ok(Map.of("message", "Tài khoản đã được xóa thành công."));
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

  @GetMapping("/admin/voters")
  public ResponseEntity<?> getAllVoters() {
    try {
      return ResponseEntity.ok(voterService.getAllUsersByRoleId(2L));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
