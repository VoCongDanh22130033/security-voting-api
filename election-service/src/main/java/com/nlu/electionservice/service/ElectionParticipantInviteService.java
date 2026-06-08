package com.nlu.electionservice.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
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
import com.nlu.electionservice.repository.RoleRepository;
import com.nlu.electionservice.repository.UserRepository;
import com.nlu.electionservice.repository.VoterRepository;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
  private final JavaMailSender mailSender;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  @Value("${app.frontend-url:http://localhost:5173}")
  private String frontendUrl;

  public ElectionParticipantInviteService(
      ElectionRepository electionRepository,
      ElectionRoundRepository roundRepository,
      UserRepository userRepository,
      VoterRepository voterRepository,
      RoleRepository roleRepository,
      ElectionVoterRepository electionVoterRepository,
      ElectionVoterInviteRepository inviteRepository,
      JavaMailSender mailSender) {
    this.electionRepository = electionRepository;
    this.roundRepository = roundRepository;
    this.userRepository = userRepository;
    this.voterRepository = voterRepository;
    this.roleRepository = roleRepository;
    this.electionVoterRepository = electionVoterRepository;
    this.inviteRepository = inviteRepository;
    this.mailSender = mailSender;
  }

  @Transactional
  public List<String> importParticipants(Long electionId, MultipartFile file) throws Exception {
    List<String> errors = new ArrayList<>();
    List<ParticipantRow> rows = readRows(file, errors);
    if (!errors.isEmpty()) {
      return errors;
    }

    for (ParticipantRow row : rows) {
      if (inviteRepository.existsByElectionIdAndEmailAndRoundNumber(electionId, row.email(), 1)) {
        errors.add("Email " + row.email() + " da co trong danh sach moi cua cuoc bau cu nay.");
      }
    }
    if (!errors.isEmpty()) {
      return errors;
    }

    for (ParticipantRow row : rows) {
      User user = userRepository.findByEmail(row.email()).orElseGet(() -> createVoterUser(row));
      Voter voter = voterRepository.findByEmail(row.email()).orElseGet(() -> createVoter(user, row));

      if (voter.getCitizenId() == null || voter.getCitizenId().isBlank()) {
        voter.setCitizenId(row.citizenId());
      }
      if (voter.getFullName() == null || voter.getFullName().isBlank()) {
        voter.setFullName(row.fullName());
      }
      voter.setVerified(true);
      voterRepository.save(voter);

      electionVoterRepository.insertElectionVoter(electionId, user.getId());
    }

    sendRoundInvitations(electionId, 1);
    return errors;
  }

  @Transactional
  public void sendRoundInvitations(Long electionId, Integer roundNumber) throws Exception {
    Election election = electionRepository.findById(electionId)
        .orElseThrow(() -> new RuntimeException("Khong tim thay cuoc bau cu."));
    ElectionRound round = roundRepository.findByElectionIdAndRoundNumber(electionId, roundNumber)
        .orElseThrow(() -> new RuntimeException("Khong tim thay vong bau cu."));

    List<Long> voterIds = electionVoterRepository.findVoterIdsByElectionId(electionId);
    for (Long voterId : voterIds) {
      User user = userRepository.findById(voterId)
          .orElseThrow(() -> new RuntimeException("Nguoi tham gia khong hop le: " + voterId));
      Voter voter = voterRepository.findById(voterId)
          .orElseThrow(() -> new RuntimeException("Khong tim thay thong tin cu tri: " + voterId));

      ElectionVoterInvite invite = inviteRepository.findByElectionIdAndVoterIdAndRoundNumber(electionId, voterId, roundNumber)
          .orElseGet(ElectionVoterInvite::new);
      if (invite.getId() != null) {
        continue;
      }

      invite.setElectionId(electionId);
      invite.setVoterId(voterId);
      invite.setRoundId(round.getId());
      invite.setRoundNumber(roundNumber);
      invite.setEmail(user.getEmail());
      invite.setFullName(voter.getFullName() != null ? voter.getFullName() : user.getFullName());
      invite.setCitizenId(voter.getCitizenId());
      invite.setInviteToken(UUID.randomUUID().toString().replace("-", ""));
      inviteRepository.save(invite);

      sendInvitationEmail(election, round, invite);
    }
  }

  @Transactional
  public Map<String, Object> verifyInvite(String token, String citizenId) {
    ElectionVoterInvite invite = inviteRepository.findByInviteToken(token)
        .orElseThrow(() -> new RuntimeException("Ma moi khong hop le."));

    if (!normalize(invite.getCitizenId()).equals(normalize(citizenId))) {
      throw new RuntimeException("Ma CCCD khong dung. Vui long kiem tra lai thong tin trong email moi.");
    }

    invite.setVerifiedAt(LocalDateTime.now());
    inviteRepository.save(invite);

    return Map.of(
        "electionId", invite.getElectionId(),
        "voterId", invite.getVoterId(),
        "email", invite.getEmail(),
        "fullName", invite.getFullName(),
        "roundId", invite.getRoundId(),
        "roundNumber", invite.getRoundNumber(),
        "inviteToken", invite.getInviteToken()
    );
  }

  public ElectionVoterInvite resolveVerifiedInvite(String token, Long electionId, Long roundId) {
    ElectionVoterInvite invite = inviteRepository.findByInviteToken(token)
        .orElseThrow(() -> new RuntimeException("Ma moi khong hop le."));
    if (!invite.getElectionId().equals(electionId)) {
      throw new RuntimeException("Ma moi khong thuoc cuoc bau cu nay.");
    }
    if (roundId != null && invite.getRoundId() != null && !invite.getRoundId().equals(roundId)) {
      throw new RuntimeException("Ma moi nay khong dung cho vong bau cu hien tai.");
    }
    if (invite.getVerifiedAt() == null) {
      throw new RuntimeException("Vui long xac thuc CCCD truoc khi bo phieu.");
    }
    return invite;
  }

  private User createVoterUser(ParticipantRow row) {
    Role voterRole = roleRepository.findByName("ROLE_VOTER")
        .orElseThrow(() -> new RuntimeException("Khong tim thay ROLE_VOTER."));

    User user = new User();
    user.setFullName(row.fullName());
    user.setEmail(row.email());
    user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
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
          errors.add("Dong " + (i + 1) + ": email, full_name va citizen_id khong duoc de trong.");
          continue;
        }
        if (!seenEmails.add(email)) {
          errors.add("Dong " + (i + 1) + ": email " + email + " bi trung trong file Excel.");
          continue;
        }
        rows.add(new ParticipantRow(email, fullName, citizenId));
      }
    }
    return rows;
  }

  private void sendInvitationEmail(Election election, ElectionRound round, ElectionVoterInvite invite) throws Exception {
    String link = frontendUrl + "/election-invite?token=" + invite.getInviteToken();
    byte[] qrBytes = createQrPng(link);

    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    helper.setTo(invite.getEmail());
    String roundTitle = round.getTitle() != null && !round.getTitle().isBlank()
        ? round.getTitle()
        : "Vong " + round.getRoundNumber();
    helper.setSubject("Moi tham gia " + roundTitle + ": " + election.getTitle());
    helper.setText("""
        <div style="font-family:Arial,sans-serif;line-height:1.6">
          <h2>Moi tham gia %s</h2>
          <p>Xin chao <b>%s</b>,</p>
          <p>Ban duoc moi tham gia cuoc bau cu: <b>%s</b>.</p>
          <p>Vong bau cu: <b>%s</b></p>
          <p>Vui long quet ma QR ben duoi hoac bam vao link sau, sau do nhap dung ma CCCD de vao trang bau cu.</p>
          <p><a href="%s">%s</a></p>
          <img src="cid:inviteQr" alt="QR tham gia bau cu" width="180" height="180"/>
        </div>
        """.formatted(roundTitle, invite.getFullName(), election.getTitle(), roundTitle, link, link), true);
    helper.addInline("inviteQr", new ByteArrayResource(qrBytes), "image/png");
    mailSender.send(message);
  }

  private byte[] createQrPng(String text) throws Exception {
    QRCodeWriter qrCodeWriter = new QRCodeWriter();
    BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 260, 260);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
    return outputStream.toByteArray();
  }

  private String getCellValueAsString(Cell cell) {
    if (cell == null) {
      return "";
    }
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue().trim();
      case NUMERIC -> DateUtil.isCellDateFormatted(cell)
          ? cell.getLocalDateTimeCellValue().toString()
          : String.format("%.0f", cell.getNumericCellValue());
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      case FORMULA -> cell.getCellFormula();
      default -> "";
    };
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", "");
  }

  private record ParticipantRow(String email, String fullName, String citizenId) {}
}
