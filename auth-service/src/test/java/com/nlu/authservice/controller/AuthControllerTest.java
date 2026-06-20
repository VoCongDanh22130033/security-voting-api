package com.nlu.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlu.authservice.dto.CreateModeratorRequest;
import com.nlu.authservice.dto.LoginRequest;
import com.nlu.authservice.dto.LoginResponse;
import com.nlu.authservice.entity.Role;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.service.AuthService;
import com.nlu.authservice.service.JwtService;
import com.nlu.authservice.service.KafkaProducerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;



import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)   // bỏ Spring Security filter để test thuần controller
@DisplayName("AuthController – MockMvc Tests")
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean AuthService authService;
    @MockBean JwtService jwtService;
    @MockBean KafkaProducerService auditLogger;

    private static LoginRequest lr(String email, String pass) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(pass);
        return r;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private User buildOrgUser(String email) {
        User u = new User();
        u.setId(1L);
        u.setEmail(email);
        u.setPassword("hashed");
        u.setFullName("Test Org");
        u.setIsLock(0);
        Role r = new Role();
        r.setName("ROLE_ORGANIZER");
        u.setRoles(Set.of(r));
        return u;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /register
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /register")
    class Register {

        @Test
        @DisplayName("TC-L01 – Luôn trả 400 (tự đăng ký bị tắt)")
        void register_always_returns_400() throws Exception {
            mvc.perform(post("/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(Map.of("email", "x@x.com", "password", "p"))))
                .andExpect(status().isBadRequest());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /login
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("TC-L02 – Đăng nhập thành công → 200 có token")
        void login_success_returns_token() throws Exception {
            User user = buildOrgUser("org@test.com");
            LoginResponse resp = new LoginResponse("jwt-abc", "Test Org", "org@test.com",
                Set.of("ROLE_ORGANIZER"), 1L, null);

            when(authService.loginReturnUser(any())).thenReturn(user);
            when(jwtService.generateToken(anyString(), anyString())).thenReturn("jwt-abc");
            when(authService.loginWithDetails(any(), eq("jwt-abc"))).thenReturn(resp);
            doNothing().when(auditLogger).sendAuditEvent(anyString(), anyString(), anyString());

            mvc.perform(post("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(lr("org@test.com", "pass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-abc"))
                .andExpect(jsonPath("$.email").value("org@test.com"));
        }

        @Test
        @DisplayName("TC-L03 – Sai mật khẩu → lỗi (4xx hoặc 5xx)")
        void login_wrong_password_throws() throws Exception {
            when(authService.loginReturnUser(any()))
                .thenThrow(new RuntimeException("Mat khau khong chinh xac!"));
            doNothing().when(auditLogger).sendAuditEvent(anyString(), anyString(), anyString());

            mvc.perform(post("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(lr("x@x.com", "wrong"))))
                .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("TC-L04 – Tài khoản bị khóa → lỗi (4xx hoặc 5xx)")
        void login_locked_account() throws Exception {
            when(authService.loginReturnUser(any()))
                .thenThrow(new RuntimeException("Tai khoan cua ban da bi khoa"));
            doNothing().when(auditLogger).sendAuditEvent(anyString(), anyString(), anyString());

            mvc.perform(post("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(lr("locked@test.com", "p"))))
                .andExpect(status().is4xxClientError());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /logout
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /logout")
    class Logout {

        @Test
        @DisplayName("TC-L05 – Logout → 200 message")
        void logout_returns_ok() throws Exception {
            doNothing().when(auditLogger).sendAuditEvent(anyString(), anyString(), anyString());

            mvc.perform(post("/logout")
                    .header("X-User-Email", "org@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
        }

        @Test
        @DisplayName("TC-L06 – Logout không có header email vẫn trả 200")
        void logout_without_email_header() throws Exception {
            doNothing().when(auditLogger).sendAuditEvent(anyString(), anyString(), anyString());

            mvc.perform(post("/logout"))
                .andExpect(status().isOk());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /verify-email
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /verify-email")
    class VerifyEmail {

        @Test
        @DisplayName("TC-L07 – Token hợp lệ → 200")
        void verify_valid_token() throws Exception {
            when(authService.verifyEmail("123456")).thenReturn(true);
            doNothing().when(auditLogger).sendAuditEvent(anyString(), anyString(), anyString());

            mvc.perform(post("/verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(Map.of("token", "123456"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("thanh cong")));
        }

        @Test
        @DisplayName("TC-L08 – Token không hợp lệ → lỗi (4xx)")
        void verify_invalid_token() throws Exception {
            when(authService.verifyEmail("badtoken"))
                .thenThrow(new RuntimeException("Ma khong hop le"));

            mvc.perform(post("/verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(Map.of("token", "badtoken"))))
                .andExpect(status().is4xxClientError());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /admin/create-moderator
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /admin/create-moderator")
    class CreateModerator {

        private CreateModeratorRequest buildReq(String email) {
            CreateModeratorRequest r = new CreateModeratorRequest();
            r.setFullName("New Org");
            r.setEmail(email);
            r.setPassword("pass123");
            r.setPhone("0900000000");
            return r;
        }

        @Test
        @DisplayName("TC-A01 – Tạo moderator thành công → 200")
        void create_moderator_success() throws Exception {
            when(authService.createModerator(any(), anyString()))
                .thenReturn("Tao tai khoan chu tri bau cu thanh cong! Email: new@test.com");
            doNothing().when(auditLogger).sendAuditEvent(anyString(), anyString(), anyString());

            mvc.perform(post("/admin/create-moderator")
                    .header("Authorization", "Bearer valid-token")
                    .header("X-User-Email", "admin@test.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(buildReq("new@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("TC-A02 – Không có Authorization header → 400")
        void create_moderator_no_token() throws Exception {
            mvc.perform(post("/admin/create-moderator")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(buildReq("x@x.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing authorization token"));
        }

        @Test
        @DisplayName("TC-A03 – Email trùng → 400 kèm error message")
        void create_moderator_duplicate_email() throws Exception {
            when(authService.createModerator(any(), anyString()))
                .thenThrow(new RuntimeException("Loi: Email da duoc su dung!"));
            doNothing().when(auditLogger).sendAuditEvent(anyString(), anyString(), anyString());

            mvc.perform(post("/admin/create-moderator")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(buildReq("dup@test.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Loi: Email da duoc su dung!"));
        }
    }
}
