package com.nlu.authservice.service;

import com.nlu.authservice.dto.CreateModeratorRequest;
import com.nlu.authservice.dto.LoginRequest;
import com.nlu.authservice.dto.LoginResponse;
import com.nlu.authservice.entity.Role;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.entity.VerificationToken;
import com.nlu.authservice.repository.RoleRepository;
import com.nlu.authservice.repository.UserRepository;
import com.nlu.authservice.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService – Unit Tests")
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock VerificationTokenRepository tokenRepository;
    @Mock JavaMailSender mailSender;

    @InjectMocks AuthService authService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static LoginRequest lr(String email, String pass) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(pass);
        return r;
    }

    private User buildUser(String email, String encodedPass, boolean locked, String... roleNames) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setPassword(encodedPass);
        user.setFullName("Test User");
        user.setIsLock(locked ? 1 : 0);
        user.setVerified(true);
        Set<Role> roles = new java.util.HashSet<>();
        for (String name : roleNames) {
            Role r = new Role();
            r.setName(name);
            roles.add(r);
        }
        user.setRoles(roles);
        return user;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // loginReturnUser
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("loginReturnUser()")
    class LoginReturnUser {

        @Test
        @DisplayName("TC-L01 – Organizer hợp lệ → trả về User")
        void success_organizer() {
            User user = buildUser("org@test.com", "hashed", false, "ROLE_ORGANIZER");
            when(userRepository.findByEmail("org@test.com")).thenReturn(Optional.of(user));

            User result = authService.loginReturnUser(lr("org@test.com", "any"));

            assertThat(result.getEmail()).isEqualTo("org@test.com");
        }

        @Test
        @DisplayName("TC-L02 – Admin hợp lệ → trả về User")
        void success_admin() {
            User user = buildUser("admin@test.com", "hashed", false, "ROLE_ADMIN");
            when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));

            assertThatNoException().isThrownBy(
                () -> authService.loginReturnUser(lr("admin@test.com", "pass"))
            );
        }

        @Test
        @DisplayName("TC-L04 – Email không tồn tại → RuntimeException")
        void email_not_found() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.loginReturnUser(lr("none@x.com", "p")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("khong ton tai");
        }

        @Test
        @DisplayName("TC-L05 – Tài khoản bị khóa → RuntimeException")
        void account_locked() {
            User user = buildUser("locked@test.com", "hashed", true, "ROLE_ORGANIZER");
            when(userRepository.findByEmail("locked@test.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.loginReturnUser(lr("locked@test.com", "p")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("bi khoa");
        }

        @Test
        @DisplayName("TC-L06 – Voter thuần (không phải admin/organizer) → RuntimeException")
        void voter_only_blocked() {
            User user = buildUser("voter@test.com", "hashed", false, "ROLE_VOTER");
            when(userRepository.findByEmail("voter@test.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.loginReturnUser(lr("voter@test.com", "p")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("link moi");
        }

        @Test
        @DisplayName("TC-L07 – Email có khoảng trắng đầu/cuối vẫn tìm được")
        void email_trimmed() {
            User user = buildUser("trim@test.com", "hashed", false, "ROLE_ORGANIZER");
            when(userRepository.findByEmail("trim@test.com")).thenReturn(Optional.of(user));

            assertThatNoException().isThrownBy(
                () -> authService.loginReturnUser(lr("  trim@test.com  ", "p"))
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // loginWithDetails
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("loginWithDetails()")
    class LoginWithDetails {

        @Test
        @DisplayName("TC-L08 – Mật khẩu đúng → LoginResponse hợp lệ")
        void correct_password_returns_response() {
            User user = buildUser("org@test.com", "hashed", false, "ROLE_ORGANIZER");
            when(userRepository.findByEmail("org@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("plainPass", "hashed")).thenReturn(true);

            LoginResponse resp = authService.loginWithDetails(
                lr("org@test.com", "plainPass"), "jwt-token");

            assertThat(resp.getToken()).isEqualTo("jwt-token");
            assertThat(resp.getEmail()).isEqualTo("org@test.com");
            assertThat(resp.getRoles()).contains("ROLE_ORGANIZER");
        }

        @Test
        @DisplayName("TC-L09 – Sai mật khẩu → RuntimeException")
        void wrong_password_throws() {
            User user = buildUser("org@test.com", "hashed", false, "ROLE_ORGANIZER");
            when(userRepository.findByEmail("org@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.loginWithDetails(
                lr("org@test.com", "wrongPass"), "token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("khong chinh xac");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // verifyEmail
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("verifyEmail()")
    class VerifyEmail {

        @Test
        @DisplayName("TC-L10 – Token hợp lệ → trả true, user được kích hoạt")
        void valid_token_activates_user() {
            User user = buildUser("u@test.com", "h", false, "ROLE_VOTER");
            user.setVerified(false);
            VerificationToken vt = new VerificationToken(user, "123456");
            vt.setExpiryDate(LocalDateTime.now().plusHours(1));
            when(tokenRepository.findByToken("123456")).thenReturn(Optional.of(vt));

            boolean result = authService.verifyEmail("123456");

            assertThat(result).isTrue();
            assertThat(user.isVerified()).isTrue();
            verify(userRepository).save(user);
            verify(tokenRepository).delete(vt);
        }

        @Test
        @DisplayName("TC-L11 – Token không tồn tại → RuntimeException")
        void token_not_found_throws() {
            when(tokenRepository.findByToken("bad")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyEmail("bad"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("khong hop le");
        }

        @Test
        @DisplayName("TC-L12 – Token hết hạn → RuntimeException")
        void expired_token_throws() {
            User user = buildUser("u@test.com", "h", false);
            VerificationToken vt = new VerificationToken(user, "expired");
            vt.setExpiryDate(LocalDateTime.now().minusHours(1));
            when(tokenRepository.findByToken("expired")).thenReturn(Optional.of(vt));

            assertThatThrownBy(() -> authService.verifyEmail("expired"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("het han");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // createModerator
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("createModerator()")
    class CreateModerator {

        @Test
        @DisplayName("TC-A01 – Tạo organizer thành công")
        void creates_organizer_successfully() {
            Role orgRole = new Role();
            orgRole.setName("ROLE_ORGANIZER");
            when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
            when(roleRepository.findByName("ROLE_ORGANIZER")).thenReturn(Optional.of(orgRole));
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");

            CreateModeratorRequest req = new CreateModeratorRequest();
            req.setFullName("New Org");
            req.setEmail("new@test.com");
            req.setPassword("pass123");
            req.setPhone("0900000000");

            String result = authService.createModerator(req, "ROLE_ADMIN");

            assertThat(result).contains("new@test.com");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("TC-A02 – Email trùng → RuntimeException")
        void duplicate_email_throws() {
            when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

            CreateModeratorRequest req = new CreateModeratorRequest();
            req.setEmail("dup@test.com");
            req.setPassword("p");

            assertThatThrownBy(() -> authService.createModerator(req, "ROLE_ADMIN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email da duoc su dung");
        }

        @Test
        @DisplayName("TC-A03 – ROLE_ORGANIZER không có trong DB → RuntimeException")
        void missing_role_throws() {
            when(userRepository.existsByEmail("x@test.com")).thenReturn(false);
            when(roleRepository.findByName("ROLE_ORGANIZER")).thenReturn(Optional.empty());

            CreateModeratorRequest req = new CreateModeratorRequest();
            req.setEmail("x@test.com");
            req.setPassword("p");

            assertThatThrownBy(() -> authService.createModerator(req, "ROLE_ADMIN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ROLE_ORGANIZER");
        }

        @Test
        @DisplayName("TC-A04 – User được tạo với verified=true và isLock=0")
        void new_user_is_verified_and_unlocked() {
            Role orgRole = new Role();
            orgRole.setName("ROLE_ORGANIZER");
            when(userRepository.existsByEmail("v@test.com")).thenReturn(false);
            when(roleRepository.findByName("ROLE_ORGANIZER")).thenReturn(Optional.of(orgRole));
            when(passwordEncoder.encode(anyString())).thenReturn("enc");

            CreateModeratorRequest req = new CreateModeratorRequest();
            req.setEmail("v@test.com");
            req.setPassword("p");
            req.setFullName("Verified");
            req.setPhone("0911");

            authService.createModerator(req, "ROLE_ADMIN");

            verify(userRepository).save(argThat(u ->
                Boolean.TRUE.equals(u.isVerified()) && u.getIsLock() == 0
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // sendVerificationEmail
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("sendVerificationEmail()")
    class SendVerificationEmail {

        @Test
        @DisplayName("TC-L13 – Gửi email kèm token 6 chữ số")
        void sends_email_with_6_digit_token() {
            User user = buildUser("email@test.com", "h", false, "ROLE_VOTER");

            authService.sendVerificationEmail(user);

            verify(tokenRepository).save(argThat(vt ->
                vt.getToken().length() == 6 && vt.getToken().matches("\\d+")
            ));
            verify(mailSender).send(any(SimpleMailMessage.class));
        }
    }
}
