package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.ElectionInviteEmailRequest;
import com.nlu.electionservice.dto.RoundClosedEmailRequest;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionRound;
import com.nlu.electionservice.entity.ElectionVoterInvite;
import com.nlu.electionservice.entity.Role;
import com.nlu.electionservice.entity.User;
import com.nlu.electionservice.entity.Voter;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import com.nlu.electionservice.repository.ElectionVoterInviteRepository;
import com.nlu.electionservice.repository.ElectionVoterRepository;
import com.nlu.electionservice.repository.BlindSignatureLogRepository;
import com.nlu.electionservice.repository.RoleRepository;
import com.nlu.electionservice.repository.UserRepository;
import com.nlu.electionservice.repository.VoterRepository;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ElectionParticipantInviteService {
  private final ElectionRepository electionRepository;
  private final ElectionRoundRepository roundRepository;
  private final UserRepository userRepository;
  private final VoterRepository voterRepository;
  private final RoleRepository roleRepository;
  private final ElectionVoterRepository electionVoterRepository;
  private final ElectionVoterInviteRepository inviteRepository;
  private final BlindSignatureLogRepository blindSignatureLogRepository;
  private final NotificationClient notificationClient;
  private static final String DISABLED_VOTER_PASSWORD = "ROLE_VOTER_LOGIN_DISABLED";

  @Value("${app.frontend-url:http://localhost:5173}")
  private String frontendUrl;

  @Value("${app.invite-email-enabled:true}")
  private boolean inviteEmailEnabled;

  @Value("${app.load-test-skip-cccd:false}")
  private boolean loadTestSkipCccd;

  public ElectionParticipantInviteService(
      ElectionRepository electionRepository,
      ElectionRoundRepository roundRepository,
      UserRepository userRepository,
      VoterRepository voterRepository,
      RoleRepository roleRepository,
      ElectionVoterRepository electionVoterRepository,
      ElectionVoterInviteRepository inviteRepository,
      BlindSignatureLogRepository blindSignatureLogRepository,
      NotificationClient notificationClient) {
    this.electionRepository = electionRepository;
    this.roundRepository = roundRepository;
    this.userRepository = userRepository;
    this.voterRepository = voterRepository;
    this.roleRepository = roleRepository;
    this.electionVoterRepository = electionVoterRepository;
    this.inviteRepository = inviteRepository;
    this.blindSignatureLogRepository = blindSignatureLogRepository;
    this.notificationClient = notificationClient;
  }

  @Transactional
  public List<String> importParticipants(Long electionId, MultipartFile file) throws Exception {
    List<String> errors = new ArrayList<>();
    List<ParticipantRow> rows = readRows(file, errors);
    if (!errors.isEmpty()) {
      return errors;
    }

    Election election = electionRepository.findById(electionId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử."));
    ElectionRound round = roundRepository.findByElectionIdAndRoundNumber(electionId, 1)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy vòng bầu cử đầu tiên."));
    Role voterRole = roleRepository.findByName("ROLE_VOTER")
        .orElseThrow(() -> new RuntimeException("Không tìm thấy ROLE_VOTER."));

    List<ElectionVoterInvite> newInvites = new ArrayList<>();
    int successCount = 0;

    for (int i = 0; i < rows.size(); i++) {
      ParticipantRow row = rows.get(i);
      int rowNum = i + 2; // Excel row số (header ở row 1)
      try {
        User user = userRepository.findByEmail(row.email()).orElseGet(() -> createVoterUser(row, voterRole));
        Voter voter = voterRepository.findByEmail(row.email()).orElseGet(() -> createVoter(user, row));

        // Luôn cập nhật citizenId và fullName theo dữ liệu mới nhất
        if (row.citizenId() != null && !row.citizenId().isBlank()) {
          voter.setCitizenId(row.citizenId());
        }
        if (row.fullName() != null && !row.fullName().isBlank()) {
          voter.setFullName(row.fullName());
        }
        voter.setVerified(true);
        voterRepository.save(voter);

        electionVoterRepository.insertElectionVoter(electionId, user.getId());

        ElectionVoterInvite invite = inviteRepository.findByElectionIdAndVoterIdAndRoundNumber(electionId, user.getId(), 1)
            .orElseGet(ElectionVoterInvite::new);
        boolean isNew = invite.getId() == null;
        if (isNew) {
          invite.setElectionId(electionId);
          invite.setVoterId(user.getId());
          invite.setRoundId(round.getId());
          invite.setRoundNumber(1);
          invite.setEmail(user.getEmail());
          invite.setInviteToken(UUID.randomUUID().toString().replace("-", ""));
        }
        // Luôn đồng bộ citizenId và fullName vào invite (kể cả khi đã tồn tại)
        invite.setFullName(voter.getFullName() != null ? voter.getFullName() : user.getFullName());
        invite.setCitizenId(voter.getCitizenId());
        inviteRepository.save(invite);
        if (isNew) {
          newInvites.add(invite);
        }
        successCount++;
      } catch (Exception rowEx) {
        errors.add("Dòng " + rowNum + " [" + row.email() + "]: " + rowEx.getMessage());
      }
    }

    // Chỉ gửi email khi cuộc bầu cử đang OPEN — không gửi trước khi bắt đầu
    if (!newInvites.isEmpty() && "OPEN".equals(election.getStatus())) {
      final Election electionRef = election;
      final ElectionRound roundRef = round;
      CompletableFuture.runAsync(() -> {
        for (ElectionVoterInvite invite : newInvites) {
          try {
            sendInvitationEmail(electionRef, roundRef, invite);
          } catch (Exception emailEx) {
            System.err.println("Không gửi được email tới " + invite.getEmail() + ": " + emailEx.getMessage());
          }
        }
      });
    }

    if (!errors.isEmpty()) {
      errors.add(0, "Import hoàn tất: " + successCount + " thành công, " + errors.size() + " lỗi.");
    }
    return errors;
  }

  @Transactional
  public void sendRoundInvitations(Long electionId, Integer roundNumber) throws Exception {
    Election election = electionRepository.findById(electionId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử."));
    ElectionRound round = roundRepository.findByElectionIdAndRoundNumber(electionId, roundNumber)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy vòng bầu cử."));

    List<Long> voterIds = electionVoterRepository.findVoterIdsByElectionId(electionId);
    if (voterIds.isEmpty() && roundNumber != null && roundNumber > 1) {
      for (ElectionVoterInvite previousInvite : inviteRepository.findByElectionIdAndRoundNumber(electionId, 1)) {
        electionVoterRepository.insertElectionVoter(electionId, previousInvite.getVoterId());
      }
      voterIds = electionVoterRepository.findVoterIdsByElectionId(electionId);
    }

    List<ElectionVoterInvite> toSend = new ArrayList<>();

    for (Long voterId : voterIds) {
      User user = userRepository.findById(voterId)
          .orElseThrow(() -> new RuntimeException("Người tham gia không hợp lệ: " + voterId));
      Voter voter = voterRepository.findById(voterId)
          .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin cử tri: " + voterId));

      ElectionVoterInvite invite = inviteRepository.findByElectionIdAndVoterIdAndRoundNumber(electionId, voterId, roundNumber)
          .orElseGet(ElectionVoterInvite::new);

      if (invite.getId() == null) {
        // Invite chưa tồn tại — tạo mới
        invite.setElectionId(electionId);
        invite.setVoterId(voterId);
        invite.setRoundId(round.getId());
        invite.setRoundNumber(roundNumber);
        invite.setEmail(user.getEmail());
        invite.setFullName(voter.getFullName() != null ? voter.getFullName() : user.getFullName());
        invite.setCitizenId(voter.getCitizenId());
        invite.setInviteToken(UUID.randomUUID().toString().replace("-", ""));
        inviteRepository.save(invite);
      }
      // Dù invite mới hay cũ (tạo từ lúc import nhưng chưa gửi email), đều gửi email khi round mở
      toSend.add(invite);
    }

    if (!toSend.isEmpty()) {
      final Election electionRef = election;
      final ElectionRound roundRef = round;
      CompletableFuture.runAsync(() -> {
        for (ElectionVoterInvite invite : toSend) {
          try {
            sendInvitationEmail(electionRef, roundRef, invite);
          } catch (Exception emailEx) {
            System.err.println("Không gửi được email/QR vòng " + roundNumber + " tới " + invite.getEmail()
                + ": " + emailEx.getMessage());
          }
        }
      });
    }
  }

  public void sendRoundClosedEmails(Long electionId, Integer roundNumber) {
    Election election = electionRepository.findById(electionId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử."));
    ElectionRound round = roundRepository.findByElectionIdAndRoundNumber(electionId, roundNumber)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy vòng bầu cử."));

    List<ElectionVoterInvite> invites = inviteRepository.findByElectionIdAndRoundNumber(electionId, roundNumber);
    for (ElectionVoterInvite invite : invites) {
      try {
        sendRoundClosedEmail(election, round, invite);
      } catch (Exception ignored) {
      }
    }
  }

  public Map<String, Object> getParticipantDashboard(Long electionId) {
    Election election = electionRepository.findById(electionId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử."));
    List<ElectionRound> rounds = roundRepository.findByElectionId(electionId);
    rounds.sort(java.util.Comparator.comparing(ElectionRound::getRoundNumber));

    long invited = inviteRepository.countInvitedByElectionId(electionId);
    long verified = inviteRepository.countVerifiedByElectionId(electionId);
    long voted = blindSignatureLogRepository.countDistinctVotedByElectionId(electionId);

    List<Map<String, Object>> roundStats = new ArrayList<>();
    for (ElectionRound round : rounds) {
      long roundInvited = inviteRepository.countInvitedByElectionIdAndRoundNumber(electionId, round.getRoundNumber());
      long roundVerified = inviteRepository.countVerifiedByElectionIdAndRoundNumber(electionId, round.getRoundNumber());
      long roundVoted = blindSignatureLogRepository.countDistinctVotedByElectionIdAndRoundId(electionId, round.getId());
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("roundId", round.getId());
      item.put("roundNumber", round.getRoundNumber());
      item.put("roundTitle", round.getTitle());
      item.put("status", round.getStatus());
      item.put("invitedCount", roundInvited);
      item.put("verifiedCount", roundVerified);
      item.put("votedCount", roundVoted);
      item.put("notVotedCount", Math.max(0, roundInvited - roundVoted));
      item.put("startTime", round.getStartTime());
      item.put("endTime", round.getEndTime());
      roundStats.add(item);
    }

    Map<String, Object> dashboard = new LinkedHashMap<>();
    dashboard.put("electionId", election.getId());
    dashboard.put("electionTitle", election.getTitle());
    dashboard.put("electionStatus", election.getStatus());
    dashboard.put("invitedCount", invited);
    dashboard.put("verifiedCount", verified);
    dashboard.put("votedCount", voted);
    dashboard.put("notVerifiedCount", Math.max(0, invited - verified));
    dashboard.put("notVotedCount", Math.max(0, invited - voted));
    dashboard.put("rounds", roundStats);
    return dashboard;
  }

  public List<Map<String, Object>> listParticipantInvites(Long electionId) {
    List<ElectionVoterInvite> invites = inviteRepository.findByElectionIdOrderByCreatedAtDesc(electionId);
    List<Map<String, Object>> result = new ArrayList<>();

    for (ElectionVoterInvite invite : invites) {
      ElectionRound round = invite.getRoundId() == null
          ? null
          : roundRepository.findById(invite.getRoundId()).orElse(null);
      boolean voted = invite.getRoundId() != null
          && blindSignatureLogRepository.existsByUserIdAndElectionIdAndRoundId(
              invite.getVoterId(), invite.getElectionId(), invite.getRoundId());

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", invite.getId());
      item.put("electionId", invite.getElectionId());
      item.put("voterId", invite.getVoterId());
      item.put("roundId", invite.getRoundId());
      item.put("roundNumber", invite.getRoundNumber());
      item.put("roundTitle", round != null ? round.getTitle() : "Vong " + invite.getRoundNumber());
      item.put("email", invite.getEmail());
      item.put("fullName", invite.getFullName());
      item.put("citizenIdMasked", maskCitizenId(invite.getCitizenId()));
      item.put("verifiedAt", invite.getVerifiedAt());
      item.put("createdAt", invite.getCreatedAt());
      item.put("voted", voted);
      item.put("status", resolveInviteStatus(invite, round, voted));
      result.add(item);
    }

    return result;
  }

  @Transactional
  public Map<String, Object> resendInvitation(Long electionId, Long inviteId) throws Exception {
    ElectionVoterInvite invite = inviteRepository.findById(inviteId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy lời mời."));
    if (!invite.getElectionId().equals(electionId)) {
      throw new RuntimeException("Lời mời không thuộc cuộc bầu cử này.");
    }
    Election election = electionRepository.findById(electionId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử."));
    ElectionRound round = invite.getRoundId() == null
        ? roundRepository.findByElectionIdAndRoundNumber(electionId, invite.getRoundNumber())
            .orElseThrow(() -> new RuntimeException("Không tìm thấy vòng bầu cử."))
        : roundRepository.findById(invite.getRoundId())
            .orElseThrow(() -> new RuntimeException("Không tìm thấy vòng bầu cử."));

    boolean voted = blindSignatureLogRepository.existsByUserIdAndElectionIdAndRoundId(
        invite.getVoterId(), invite.getElectionId(), round.getId());
    if (voted) {
      throw new RuntimeException("Cử tri này đã bỏ phiếu cho vòng này, không cần gửi lại QR.");
    }
    if (!"OPEN".equals(round.getStatus())) {
      throw new RuntimeException("Vòng bầu cử chưa bắt đầu hoặc đã kết thúc, không thể gửi lại QR.");
    }

    sendInvitationEmail(election, round, invite);
    return Map.of(
        "message", "Đã gửi lại email/QR thành công.",
        "inviteId", invite.getId(),
        "email", invite.getEmail(),
        "status", resolveInviteStatus(invite, round, false)
    );
  }

  @Transactional
  public Map<String, Object> resendAllNotVoted(Long electionId) throws Exception {
    Election election = electionRepository.findById(electionId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử."));

    List<ElectionVoterInvite> allInvites = inviteRepository.findByElectionIdOrderByCreatedAtDesc(electionId);

    int sent = 0, skipped = 0;
    for (ElectionVoterInvite invite : allInvites) {
      try {
        ElectionRound round = invite.getRoundId() == null
            ? roundRepository.findByElectionIdAndRoundNumber(electionId, invite.getRoundNumber()).orElse(null)
            : roundRepository.findById(invite.getRoundId()).orElse(null);
        if (round == null) { skipped++; continue; }
        if (!"OPEN".equals(round.getStatus())) { skipped++; continue; }

        boolean voted = invite.getVoterId() != null &&
            blindSignatureLogRepository.existsByUserIdAndElectionIdAndRoundId(
                invite.getVoterId(), electionId, round.getId());
        if (voted) { skipped++; continue; }

        sendInvitationEmail(election, round, invite);
        sent++;
      } catch (Exception ex) {
        skipped++;
      }
    }
    return Map.of("sent", sent, "skipped", skipped, "total", allInvites.size());
  }

  @Transactional
  public Map<String, Object> verifyInvite(String token, String citizenId) {
    ElectionVoterInvite invite = inviteRepository.findByInviteToken(token)
        .orElseThrow(() -> new RuntimeException("Mã mời không hợp lệ."));

    if (!loadTestSkipCccd && !normalize(invite.getCitizenId()).equals(normalize(citizenId))) {
      throw new RuntimeException("Mã CCCD không đúng. Vui lòng kiểm tra lại thông tin trong email mời.");
    }

    // Kiểm tra tài khoản có bị khóa không
    if (invite.getVoterId() != null) {
      userRepository.findById(invite.getVoterId()).ifPresent(u -> {
        if (u.getIsLock() != null && u.getIsLock() == 1) {
          throw new RuntimeException("Tài khoản của bạn đã bị khóa. Không thể tham gia bầu cử. Vui lòng liên hệ quản trị viên.");
        }
      });
    }

    if (invite.getVerifiedAt() == null) {
      invite.setVerifiedAt(LocalDateTime.now());
      inviteRepository.save(invite);
    }

    return Map.of(
        "electionId", invite.getElectionId(),
        "voterId", invite.getVoterId(),
        "email", invite.getEmail(),
        "fullName", invite.getFullName() != null ? invite.getFullName() : "",
        "roundId", invite.getRoundId(),
        "roundNumber", invite.getRoundNumber(),
        "inviteToken", invite.getInviteToken()
    );
  }

  public ElectionVoterInvite resolveVerifiedInvite(String token, Long electionId, Long roundId) {
    ElectionVoterInvite invite = inviteRepository.findByInviteToken(token)
        .orElseThrow(() -> new RuntimeException("Mã mời không hợp lệ."));
    if (!invite.getElectionId().equals(electionId)) {
      throw new RuntimeException("Mã mời không thuộc cuộc bầu cử này.");
    }
    if (roundId != null && invite.getRoundId() != null && !invite.getRoundId().equals(roundId)) {
      throw new RuntimeException("Mã mời này không đúng cho vòng bầu cử hiện tại.");
    }
    if (invite.getVerifiedAt() == null) {
      throw new RuntimeException("Vui lòng xác thực CCCD trước khi bỏ phiếu.");
    }
    return invite;
  }

  // Load test mode: bỏ qua verifiedAt, chỉ kiểm tra token + electionId + roundId
  public ElectionVoterInvite resolveInviteForLoadTest(String token, Long electionId, Long roundId) {
    ElectionVoterInvite invite = inviteRepository.findByInviteToken(token)
        .orElseThrow(() -> new RuntimeException("Mã mời không hợp lệ."));
    if (!invite.getElectionId().equals(electionId)) {
      throw new RuntimeException("Mã mời không thuộc cuộc bầu cử này.");
    }
    if (roundId != null && invite.getRoundId() != null && !invite.getRoundId().equals(roundId)) {
      throw new RuntimeException("Mã mời này không đúng cho vòng bầu cử hiện tại.");
    }
    return invite;
  }

  private User createVoterUser(ParticipantRow row, Role voterRole) {
    User user = new User();
    user.setFullName(row.fullName());
    user.setEmail(row.email());
    user.setPassword(DISABLED_VOTER_PASSWORD);
    user.setVerified(true);
    user.setIsLock(0);
    user.setRoles(Set.of(voterRole));
    return userRepository.save(user);
  }

  private Voter createVoter(User user, ParticipantRow row) {
    Voter voter = new Voter();
    voter.setUser(user);
    voter.setFullName(row.fullName());
    voter.setCitizenId(row.citizenId());
    voter.setVerified(true);
    return voterRepository.save(voter);
  }

  private List<ParticipantRow> readRows(MultipartFile file, List<String> errors) throws Exception {
    List<ParticipantRow> rows = new ArrayList<>();
    Set<String> seenEmails = new HashSet<>();
    try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
      Sheet sheet = workbook.getSheetAt(0);
      for (int i = 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null) {
          continue;
        }
        String email = getCellValueAsString(row.getCell(0)).toLowerCase();
        String fullName = getCellValueAsString(row.getCell(1));
        String citizenId = getCellValueAsString(row.getCell(2));

        if (email.isBlank() && fullName.isBlank() && citizenId.isBlank()) {
          continue;
        }
        if (email.isBlank() || fullName.isBlank() || citizenId.isBlank()) {
          errors.add("Dòng " + (i + 1) + ": email, full_name và citizen_id không được để trống.");
          continue;
        }
        if (!seenEmails.add(email)) {
          errors.add("Dòng " + (i + 1) + ": email " + email + " bị trùng trong file Excel.");
          continue;
        }
        rows.add(new ParticipantRow(email, fullName, citizenId));
      }
    }
    return rows;
  }

  private void sendInvitationEmail(Election election, ElectionRound round, ElectionVoterInvite invite) throws Exception {
    if (!inviteEmailEnabled) {
      return;
    }

    String link = frontendUrl + "/election-invite?token=" + invite.getInviteToken();
    String roundTitle = round.getTitle() != null && !round.getTitle().isBlank()
        ? round.getTitle()
        : "Vong " + round.getRoundNumber();
    notificationClient.sendElectionInvite(ElectionInviteEmailRequest.builder()
        .to(invite.getEmail())
        .fullName(invite.getFullName())
        .electionTitle(election.getTitle())
        .roundTitle(roundTitle)
        .inviteLink(link)
        .build());
  }

  private void sendRoundClosedEmail(Election election, ElectionRound round, ElectionVoterInvite invite) throws Exception {
    String resultLink = frontendUrl + "/results?electionId=" + election.getId();
    String roundTitle = round.getTitle() != null && !round.getTitle().isBlank()
        ? round.getTitle()
        : "Vong " + round.getRoundNumber();

    notificationClient.sendRoundClosed(RoundClosedEmailRequest.builder()
        .to(invite.getEmail())
        .fullName(invite.getFullName())
        .electionTitle(election.getTitle())
        .roundTitle(roundTitle)
        .resultLink(resultLink)
        .build());
  }

  private String getCellValueAsString(Cell cell) {
    if (cell == null) {
      return "";
    }
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue().trim();
      case NUMERIC -> DateUtil.isCellDateFormatted(cell)
          ? cell.getLocalDateTimeCellValue().toString()
          // Dùng BigDecimal để tránh mất chữ số khi số lớn, tránh scientific notation
          : new java.math.BigDecimal(cell.getNumericCellValue()).toBigInteger().toString();
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      case FORMULA -> cell.getCellFormula();
      default -> "";
    };
  }

  private String normalize(String value) {
    if (value == null) return "";
    // Xóa khoảng trắng và chuẩn hóa leading zero để so sánh đúng
    // VD: "07920100000998" == "7920100000998" đều về "7920100000998"
    String trimmed = value.trim().replaceAll("\\s+", "");
    return trimmed.replaceAll("^0+(?!$)", "");
  }

  private String resolveInviteStatus(ElectionVoterInvite invite, ElectionRound round, boolean voted) {
    if (voted) {
      return "USED";
    }
    if (round != null && round.getEndTime() != null && LocalDateTime.now().isAfter(round.getEndTime())) {
      return "EXPIRED";
    }
    if (invite.getVerifiedAt() != null) {
      return "VERIFIED";
    }
    return "ACTIVE";
  }

  private String maskCitizenId(String citizenId) {
    String normalized = normalize(citizenId);
    if (normalized.length() <= 4) {
      return "****";
    }
    return "*".repeat(Math.max(0, normalized.length() - 4)) + normalized.substring(normalized.length() - 4);
  }

  private record ParticipantRow(String email, String fullName, String citizenId) {}
}
