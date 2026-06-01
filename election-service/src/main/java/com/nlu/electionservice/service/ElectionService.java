package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.CandidateRequest;
import com.nlu.electionservice.dto.CreateElectionRequest;
import com.nlu.electionservice.dto.CreateElectionRequest.RoundTimeSettingDto;
import com.nlu.electionservice.dto.CreateElectionWithCandidatesRequest;
import com.nlu.electionservice.dto.ElectionRequest;
import com.nlu.electionservice.dto.VoteAnonymousRequest;
import com.nlu.electionservice.entity.Candidate;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionRound;
import com.nlu.electionservice.entity.RoundCandidate;
import com.nlu.electionservice.entity.UsedToken;
import com.nlu.electionservice.entity.Vote;
import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import com.nlu.electionservice.repository.RoundCandidateRepository;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ElectionService {
  @Autowired
  private ElectionRepository electionRepository;
  @Autowired
  private com.cloudinary.Cloudinary cloudinary;
  @Autowired
  private CandidateRepository candidateRepository;
  @Autowired
  private RoundCandidateRepository roundCandidateRepository;
  public List<Election> getAllElections() {
    return electionRepository.findAll();
  }
  @Autowired
  private ElectionRoundRepository roundRepository;
  @Autowired
  private com.nlu.electionservice.repository.UsedTokenRepository usedTokenRepository;

  @Autowired
  private com.nlu.electionservice.repository.VoteRepository voteRepository;

  @Transactional
  public Election createElection(Election election, List<Candidate> candidates) {
    election.setStatus(calculateStatus(election.getStartTime(), election.getEndTime()));
    election.setIsDelete(1);

    Election saved = electionRepository.save(election);

    if (candidates != null && !candidates.isEmpty()) {
      candidates.forEach(c -> c.setElection(saved));
      candidateRepository.saveAll(candidates);
    }
    return saved;
  }

  @Transactional
  public Election updateElection(Long id, ElectionRequest request) {
    Election election = electionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử"));

    election.setTitle(request.getTitle());
    election.setDescription(request.getDescription());
    election.setStartTime(request.getStartTime());
    election.setEndTime(request.getEndTime());
    election.setImageUrl(request.getImageUrl());
    election.setStatus(request.getStatus());

    election.getCandidates().clear();

    if (request.getCandidates() != null) {
      request.getCandidates().forEach(cReq -> {
        Candidate candidate = new Candidate();
        candidate.setName(cReq.getName());
        candidate.setDescription(cReq.getDescription());
        candidate.setImageUrl(cReq.getImageUrl());
        candidate.setElection(election);
        election.setRoleId(request.getRoleId());
        election.getCandidates().add(candidate);
      });
    }
    election.setStatus(calculateStatus(request.getStartTime(), request.getEndTime()));
    return electionRepository.save(election);
  }

  @Transactional
  public void submitAnonymousVote(com.nlu.electionservice.dto.VoteAnonymousRequest request, java.math.BigInteger N, java.math.BigInteger E) {
    if (usedTokenRepository.existsByMessageToken(request.getMessageToken())) {
      throw new RuntimeException("Mã token phiếu bầu này đã được sử dụng! Lá phiếu vô hiệu.");
    }

    java.math.BigInteger M = new java.math.BigInteger(request.getMessageToken(), 16);
    java.math.BigInteger S = new java.math.BigInteger(request.getSignature(), 16);

    java.math.BigInteger V = S.modPow(E, N);

    if (!V.equals(M)) {
      throw new RuntimeException("Xác thực thất bại! Chữ ký phiếu bầu không hợp lệ hoặc đã bị chỉnh sửa cấu trúc.");
    }

    UsedToken usedToken = new UsedToken();
    usedToken.setMessageToken(request.getMessageToken());
    usedToken.setRoundId(request.getRoundId());
    usedTokenRepository.save(usedToken);

    Vote vote = new Vote();
    vote.setRoundId(request.getRoundId());
    vote.setCandidateId(request.getCandidateId());
    vote.setMessageToken(request.getMessageToken());
    vote.setSignature(request.getSignature());
    voteRepository.save(vote);

    System.out.println(">>> [VOTE SUCCESS] Một lá phiếu nặc danh hợp lệ vừa được chấp thuận thành công vào hòm phiếu.");
  }

  @Transactional
  public void deleteElection(Long id) {
    Election election = electionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử"));
    election.setIsDelete(2);
    electionRepository.save(election);
  }

  private void saveCandidates(Election election, ElectionRequest request) {
    if (request.getCandidates() != null) {
      List<Candidate> candidates = request.getCandidates().stream().map(cReq -> {
        Candidate c = new Candidate();
        c.setName(cReq.getName());
        c.setDescription(cReq.getDescription());
        c.setImageUrl(cReq.getImageUrl());
        c.setElection(election);
        return c;
      }).collect(Collectors.toList());
      candidateRepository.saveAll(candidates);
    }
  }

  public List<Candidate> getCandidatesByElection(Long electionId) {
    boolean exists = electionRepository.existsById(electionId);
    if (!exists) {
      return Collections.emptyList();
    }
    return candidateRepository.findByElectionId(electionId);
  }

  public Election getById(Long id) {
    return electionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử với ID: " + id));
  }

  public String calculateStatus(LocalDateTime start, LocalDateTime end) {
    LocalDateTime now = LocalDateTime.now();
    if (now.isBefore(start)) {
      return "UPCOMING";
    } else if (now.isAfter(end)) {
      return "CLOSED";
    } else {
      return "OPEN";
    }
  }

  @Transactional
  public Election createMultiRoundElectionWithCandidates(CreateElectionRequest request) {
    // ====================================================================
    // 1. KHỞI TẠO VÀ LƯU THÔNG TIN CUỘC BẦU CỬ GỐC
    // ====================================================================
    Election election = new Election();
    election.setTitle(request.getTitle());
    election.setDescription(request.getDescription());
    election.setTotalRounds(request.getTotalRounds());
    election.setIsDelete(1);

    // --- XỬ LÝ ĐẨY ẢNH CUỘC BẦU CỬ LÊN CLOUDINARY ---
    String electionRawBase64 = request.getBase64Image();
    if (electionRawBase64 != null && !electionRawBase64.trim().isEmpty()) {
      try {
        String cleanBase64 = electionRawBase64.contains(",") ? electionRawBase64.split(",")[1] : electionRawBase64;
        cleanBase64 = cleanBase64.trim().replaceAll("\\s+", "");

        byte[] imageBytes = java.util.Base64.getDecoder().decode(cleanBase64);
        java.util.Map uploadResult = cloudinary.uploader().upload(imageBytes, com.cloudinary.utils.ObjectUtils.emptyMap());

        String cloudinaryUrl = (String) uploadResult.get("url");
        election.setImageUrl(cloudinaryUrl);
        System.out.println(">>> [BE] Upload ảnh cuộc bầu cử thành công: " + cloudinaryUrl);
      } catch (Exception e) {
        System.err.println(">>> [BE] Lỗi upload ảnh cuộc bầu cử lên Cloudinary: " + e.getMessage());
        election.setImageUrl("");
      }
    } else {
      election.setImageUrl("");
    }

    // Thiết lập mốc thời gian tổng thể dựa vào danh sách các vòng đấu
    if (request.getRoundsTimeSettings() != null && !request.getRoundsTimeSettings().isEmpty()) {
      RoundTimeSettingDto firstRound = request.getRoundsTimeSettings().get(0);
      RoundTimeSettingDto lastRound = request.getRoundsTimeSettings().get(request.getRoundsTimeSettings().size() - 1);

      election.setStartTime(firstRound.getStartTime());
      election.setEndTime(lastRound.getEndTime());

      // ĐÃ SỬA: Tự động cấu hình trạng thái tổng thể chuẩn xác theo thời gian thực
      election.setStatus(calculateStatus(firstRound.getStartTime(), lastRound.getEndTime()));
    } else {
      election.setStatus("UPCOMING");
    }

    // Lưu bảng elections gốc
    final Election savedElection = electionRepository.save(election);

    // ====================================================================
    // 2. DUYỆT QUA MẢNG CẤU HÌNH ĐỂ TẠO TỰ ĐỘNG CÁC VÒNG (ELECTION_ROUNDS)
    // ====================================================================
  // ====================================================================

    if (request.getRoundsTimeSettings() != null) {
      for (CreateElectionRequest.RoundTimeSettingDto roundSetting : request.getRoundsTimeSettings()) {
        ElectionRound round = new ElectionRound();
        round.setElection(savedElection);
        round.setRoundNumber(roundSetting.getRoundNumber());

        // ĐÃ BỔ SUNG: Lưu tên riêng biệt của vòng đấu (Ví dụ: "Vòng sơ khảo", "Vòng ABC 1") vào Database
        round.setTitle(roundSetting.getTitle());

        round.setStartTime(roundSetting.getStartTime());
        round.setEndTime(roundSetting.getEndTime());
        round.setMaxAdvanceCount(roundSetting.getMaxAdvanceCount() != null ? roundSetting.getMaxAdvanceCount() : 1);
        round.setStatus(calculateStatus(roundSetting.getStartTime(), roundSetting.getEndTime()));

        round = roundRepository.save(round);

        // ================================================================
        // 3. XỬ LÝ LƯU DANH SÁCH ỨNG CỬ VIÊN VÀO VÒNG 1
        // ================================================================
        if (roundSetting.getRoundNumber() == 1) {

          // Lượt A: Nạp ứng cử viên sẵn có chọn từ hệ thống (Theo ID)
          if (request.getCandidateIds() != null) {
            for (Long candidateId : request.getCandidateIds()) {
              RoundCandidate rc = new RoundCandidate();
              rc.setRound(round);
              rc.setCandidateId(candidateId);
              roundCandidateRepository.save(rc);
            }
          }

          // Lượt B: Khởi tạo và nạp ứng cử viên tự điền thủ công ghi tay
          if (request.getNewCandidates() != null) {
            for (CreateElectionRequest.NewCandidateDto newCandDto : request.getNewCandidates()) {
              Candidate newCand = new Candidate();
              newCand.setName(newCandDto.getName());
              newCand.setParty(newCandDto.getParty());
              newCand.setDescription(newCandDto.getDescription());
              newCand.setElection(savedElection);

              String rawBase64 = newCandDto.getBase64Image();
              if (rawBase64 != null && !rawBase64.trim().isEmpty()) {
                try {
                  String cleanBase64Data = rawBase64.contains(",") ? rawBase64.split(",")[1] : rawBase64;
                  cleanBase64Data = cleanBase64Data.trim().replaceAll("\\s+", "");

                  byte[] imageBytes = java.util.Base64.getDecoder().decode(cleanBase64Data);
                  java.util.Map uploadResult = cloudinary.uploader().upload(imageBytes, com.cloudinary.utils.ObjectUtils.emptyMap());

                  String cloudinaryUrl = (String) uploadResult.get("url");
                  newCand.setImageUrl(cloudinaryUrl);
                  System.out.println(">>> [BE] Upload ảnh ứng viên thành công: " + cloudinaryUrl);
                } catch (Exception e) {
                  System.err.println(">>> [BE] Lỗi upload ảnh ứng viên lên Cloudinary: " + e.getMessage());
                  newCand.setImageUrl("");
                }
              } else {
                newCand.setImageUrl("");
              }

              newCand = candidateRepository.save(newCand);

              RoundCandidate rc = new RoundCandidate();
              rc.setRound(round);
              rc.setCandidateId(newCand.getId());
              roundCandidateRepository.save(rc);
            }
          }
        }
      }
    }

    return savedElection;
  }

  public Object getElectionById(Long electionId) {
    System.out.println(">>> [BE ElectionService] Đang truy vấn thông tin cuộc bầu cử ID: " + electionId);
    return electionRepository.findById(electionId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử với mã ID: " + electionId));
  }
}