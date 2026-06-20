package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.CandidateResponse;
import com.nlu.electionservice.dto.CreateElectionRequest;
import com.nlu.electionservice.dto.CreateElectionRequest.RoundTimeSettingDto;
import com.nlu.electionservice.dto.RoundCandidateInfo;
import com.nlu.electionservice.dto.RoundDetailDto;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ElectionService {
  
  private final ElectionRepository electionRepository;
  private final com.cloudinary.Cloudinary cloudinary;
  private final CandidateRepository candidateRepository;
  private final RoundCandidateRepository roundCandidateRepository;
  private final ElectionRoundRepository roundRepository;
  private final com.nlu.electionservice.repository.UsedTokenRepository usedTokenRepository;
  private final com.nlu.electionservice.repository.VoteRepository voteRepository;
  private final ElectionParticipantInviteService participantInviteService;
  private final RealtimeNotificationService realtimeNotificationService;
  private final VoteService voteService;

  @Lazy @Autowired
  private ElectionService self;

  @Autowired
  public ElectionService(ElectionRepository electionRepository,
                         com.cloudinary.Cloudinary cloudinary,
                         CandidateRepository candidateRepository,
                         RoundCandidateRepository roundCandidateRepository,
                         ElectionRoundRepository roundRepository,
                         com.nlu.electionservice.repository.UsedTokenRepository usedTokenRepository,
                          com.nlu.electionservice.repository.VoteRepository voteRepository,
                          ElectionParticipantInviteService participantInviteService,
                          RealtimeNotificationService realtimeNotificationService,
                          VoteService voteService) {
      this.electionRepository = electionRepository;
      this.cloudinary = cloudinary;
      this.candidateRepository = candidateRepository;
      this.roundCandidateRepository = roundCandidateRepository;
      this.roundRepository = roundRepository;
      this.usedTokenRepository = usedTokenRepository;
      this.voteRepository = voteRepository;
      this.participantInviteService = participantInviteService;
      this.realtimeNotificationService = realtimeNotificationService;
      this.voteService = voteService;
  }

  /**
   * Đóng trạng thái round trong transaction REQUIRES_NEW — commit ngay lập tức, độc lập
   * với transaction ngoài. Trả về true nếu đây là lần đầu đóng (cần gửi email).
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean tryCloseElection(Long electionId) {
    Election e = electionRepository.findById(electionId).orElse(null);
    if (e == null || "CLOSED".equals(e.getStatus())) return false;
    e.setStatus("CLOSED");
    electionRepository.save(e);
    return true;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean tryCloseRound(Long roundId) {
    ElectionRound r = roundRepository.findById(roundId).orElse(null);
    if (r == null || "CLOSED".equals(r.getStatus())) return false;
    r.setStatus("CLOSED");
    roundRepository.save(r);
    return true;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processRoundAfterClose(Long electionId, Long roundId) {
    // Commit trạng thái CLOSED ngay trong REQUIRES_NEW — không bị rollback bởi transaction này
    boolean shouldNotify = self.tryCloseRound(roundId);

    ElectionRound round = roundRepository.findById(roundId)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy vòng bầu cử."));

    if (shouldNotify) {
      try {
        realtimeNotificationService.roundClosed(round);
        participantInviteService.sendRoundClosedEmails(electionId, round.getRoundNumber());
      } catch (Exception notifyEx) {
        log.error("Cảnh báo: Không gửi được thông báo/email đóng vòng {}: {}", round.getRoundNumber(), notifyEx.getMessage());
      }
    }

    Election parentElection = round.getElection();
    java.util.Optional<ElectionRound> nextRoundOpt = roundRepository.findByElectionIdAndRoundNumber(electionId, round.getRoundNumber() + 1);
    boolean hasNextRound = nextRoundOpt.isPresent();

    // Giải mã phiếu E2E trước khi tổng hợp — phiếu lưu với candidateId=null cần được gán đúng
    try {
      voteService.decryptRoundVotes(electionId, roundId);
    } catch (Exception decryptEx) {
      log.error("Cảnh báo: Không giải mã được phiếu E2E vòng {}: {}", roundId, decryptEx.getMessage());
    }

    List<Map<String, Object>> stats = voteRepository.countVotesByCandidate(electionId, roundId);
    Map<Long, Long> votesByCandidate = new HashMap<>();

    long totalVotes = 0;
    long highestVotes = 0;
    Long absoluteWinnerId = null;

    for (Map<String, Object> stat : stats) {
        Object cidObj = stat.get("candidateId");
        Object vcObj = stat.get("voteCount");
        if (cidObj == null || vcObj == null) continue;
        Long candidateId = ((Number) cidObj).longValue();
        Long voteCount = ((Number) vcObj).longValue();
        votesByCandidate.put(candidateId, voteCount);
        totalVotes += voteCount;
        if (voteCount > highestVotes) {
            highestVotes = voteCount;
            absoluteWinnerId = candidateId;
        }
    }

    boolean hasAbsoluteWinner = (!hasNextRound && totalVotes > 0 && highestVotes == totalVotes);

    if (hasAbsoluteWinner) {
        log.info(">>> [CHIẾN THẮNG TUYỆT ĐỐI] Ứng viên ID {} đã giành 100% phiếu bầu. Kết thúc bầu cử!", absoluteWinnerId);
        parentElection.setWinnerId(absoluteWinnerId);
        parentElection.setStatus("CLOSED");
        electionRepository.save(parentElection);

        if (nextRoundOpt.isPresent()) {
            List<ElectionRound> allRounds = roundRepository.findByElectionId(electionId);
            for (ElectionRound r : allRounds) {
                if (r.getRoundNumber() > round.getRoundNumber()) {
                    r.setStatus("CANCELLED");
                    roundRepository.save(r);
                }
            }
        }
        try { synchronizeVoteCounts(electionId); } catch (Exception ex) {
            log.error("Cảnh báo: Lỗi đồng bộ phiếu bầu {}: {}", electionId, ex.getMessage());
        }
        try { realtimeNotificationService.electionClosed(parentElection); } catch (Exception ex) {
            log.error("Cảnh báo: Không gửi được thông báo đóng bầu cử {}: {}", electionId, ex.getMessage());
        }
        return;
    }

    int advance = round.getMaxAdvanceCount() != null && round.getMaxAdvanceCount() > 0
            ? round.getMaxAdvanceCount()
            : 1;
    List<RoundCandidate> currentRoundCandidates = roundCandidateRepository.findByRoundId(roundId);
    List<Long> advancing = currentRoundCandidates.stream()
            .map(RoundCandidate::getCandidateId)
            .distinct()
            .sorted(Comparator
                    .comparing((Long candidateId) -> votesByCandidate.getOrDefault(candidateId, 0L)).reversed()
                    .thenComparing(Long::longValue))
            .limit(advance)
            .collect(Collectors.toList());

    if (advancing.isEmpty()) {
      advancing = stats.stream()
              .map(row -> ((Number) row.get("candidateId")).longValue())
              .limit(advance)
              .collect(Collectors.toList());
    }

    if (advancing.isEmpty()) {
      log.error("Không có ứng viên nào đủ điều kiện vào vòng tiếp theo - roundId={}, electionId={}", roundId, electionId);
      throw new RuntimeException("Không thể chuyển vòng: không có ứng viên đủ điều kiện.");
    }

    if (hasNextRound) {
      ElectionRound nextRound = nextRoundOpt.get();
      roundCandidateRepository.deleteByRoundId(nextRound.getId());
      int inserted = roundCandidateRepository.insertAdvancingCandidates(electionId, roundId, nextRound.getId(), advance);

      if (inserted == 0) {
        for (Long cid : advancing) {
          RoundCandidate rc = new RoundCandidate();
          rc.setRound(nextRound);
          rc.setCandidateId(cid);
          roundCandidateRepository.save(rc);
        }
      }

      LocalDateTime now = LocalDateTime.now();
      boolean nextRoundStarted = nextRound.getStartTime() == null || !now.isBefore(nextRound.getStartTime());
      boolean nextRoundEnded = nextRound.getEndTime() != null && now.isAfter(nextRound.getEndTime());

      if (nextRoundEnded) {
        nextRound.setStatus("CLOSED");
      } else {
        nextRound.setStatus(nextRoundStarted ? "OPEN" : "UPCOMING");
      }
      
      roundRepository.save(nextRound);

      if (nextRoundStarted && !nextRoundEnded) {
        try {
          participantInviteService.sendRoundInvitations(electionId, nextRound.getRoundNumber());
          realtimeNotificationService.roundOpened(nextRound);
        } catch (Exception inviteEx) {
          log.error("Cảnh báo: Đã mở vòng tiếp theo nhưng không gửi được email mời: {}", inviteEx.getMessage());
        }
      }
    } else {
      if (!advancing.isEmpty()) {
        parentElection.setWinnerId(advancing.get(0));
      }
      parentElection.setStatus("CLOSED");
      electionRepository.save(parentElection);
      try { realtimeNotificationService.electionClosed(parentElection); } catch (Exception ex) {
          log.error("Cảnh báo: Không gửi được thông báo đóng bầu cử {}: {}", electionId, ex.getMessage());
      }
      try { synchronizeVoteCounts(electionId); } catch (Exception ex) {
          log.error("Cảnh báo: Lỗi đồng bộ phiếu bầu {}: {}", electionId, ex.getMessage());
      }
    }
  }

  @Transactional
  public void determineElectionWinner(Long electionId) {
    Election election = electionRepository.findById(electionId)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử."));

    ElectionRound round = roundRepository.findByElectionIdAndRoundNumber(electionId, election.getTotalRounds())
            .orElseThrow(() -> new RuntimeException("Không tìm thấy vòng cuối của cuộc bầu cử."));

    List<Map<String, Object>> stats = voteRepository.countVotesByCandidate(electionId, round.getId());
    if (!stats.isEmpty()) {
      Object cidObj = stats.get(0).get("candidateId");
      if (cidObj == null) return;
      Long winnerId = ((Number) cidObj).longValue();
      election.setWinnerId(winnerId);
      electionRepository.save(election);
    }
  }

  @Transactional
  public void synchronizeVoteCounts(Long electionId) {
    List<ElectionRound> rounds = roundRepository.findByElectionId(electionId);

    ElectionRound lastRound = rounds.stream()
        .filter(r -> "CLOSED".equals(r.getStatus()) || "OPEN".equals(r.getStatus()))
        .max(Comparator.comparing(ElectionRound::getRoundNumber))
        .orElse(null);

    if (lastRound == null) return;

    // Reset tất cả ứng viên của election về 0 trước — tránh giữ voteCount cũ từ vòng trước
    List<Candidate> allCandidates = candidateRepository.findByElectionId(electionId);
    for (Candidate c : allCandidates) c.setVoteCount(0);
    candidateRepository.saveAll(allCandidates);

    Map<Long, Long> voteCounts = new HashMap<>();
    List<Map<String, Object>> stats = voteRepository.countVotesByCandidate(electionId, lastRound.getId());
    for (Map<String, Object> stat : stats) {
      Long candidateId = ((Number) stat.get("candidateId")).longValue();
      Long voteCount = ((Number) stat.get("voteCount")).longValue();
      voteCounts.put(candidateId, voteCount);
    }

    List<Candidate> candidatesToUpdate = candidateRepository.findAllById(voteCounts.keySet());
    for (Candidate candidate : candidatesToUpdate) {
      candidate.setVoteCount(voteCounts.get(candidate.getId()).intValue());
    }
    candidateRepository.saveAll(candidatesToUpdate);
  }

  public List<CandidateResponse> getElectionResults(Long electionId) {
    Election election = electionRepository.findById(electionId)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử."));

    List<ElectionRound> rounds = roundRepository.findByElectionId(electionId);
    ElectionRound finalValidRound = rounds.stream()
            .filter(r -> "CLOSED".equals(r.getStatus()) || "OPEN".equals(r.getStatus()))
            .max(java.util.Comparator.comparing(ElectionRound::getRoundNumber))
            .orElseThrow(() -> new RuntimeException("Không tìm thấy vòng bầu cử hợp lệ."));

    List<Map<String, Object>> stats = voteRepository.countVotesByCandidate(electionId, finalValidRound.getId());
    Map<Long, Long> voteCounts = stats.stream()
            .collect(Collectors.toMap(
                    s -> ((Number) s.get("candidateId")).longValue(),
                    s -> ((Number) s.get("voteCount")).longValue()
            ));

    List<Candidate> candidates = candidateRepository.findAllById(voteCounts.keySet());

    return candidates.stream()
            .map(c -> CandidateResponse.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .party(c.getParty())
                    .description(c.getDescription())
                    .imageUrl(c.getImageUrl())
                    .electionId(electionId)
                    .voteCount(voteCounts.getOrDefault(c.getId(), 0L))
                    .build())
            .sorted((c1, c2) -> c2.getVoteCount().compareTo(c1.getVoteCount()))
            .collect(Collectors.toList());
  }

  @Transactional
  public Election updateMultiRoundElection(Long electionId, CreateElectionRequest request) {
    Election election = electionRepository.findById(electionId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử với ID: " + electionId));

    if (!"UPCOMING".equals(election.getStatus())) {
        throw new IllegalStateException("Chỉ có thể chỉnh sửa các cuộc bầu cử ở trạng thái 'Sắp diễn ra'.");
    }

    election.setTitle(request.getTitle());
    election.setDescription(request.getDescription());
    election.setTotalRounds(request.getTotalRounds());
    validateRoundAdvancement(request);

    String electionRawBase64 = request.getBase64Image();
    if (electionRawBase64 != null && !electionRawBase64.trim().isEmpty() && !electionRawBase64.startsWith("http")) {
      try {
        String cleanBase64 = electionRawBase64.contains(",") ? electionRawBase64.split(",")[1] : electionRawBase64;
        byte[] imageBytes = java.util.Base64.getDecoder().decode(cleanBase64.trim().replaceAll("\\s+", ""));
        java.util.Map uploadResult = cloudinary.uploader().upload(imageBytes, com.cloudinary.utils.ObjectUtils.emptyMap());
        String imageUrl = (String) uploadResult.get("secure_url");
        if (imageUrl == null) imageUrl = (String) uploadResult.get("url");
        election.setImageUrl(imageUrl);
      } catch (Exception e) {
        throw new RuntimeException("Không thể upload ảnh cuộc bầu cử lên Cloudinary: " + e.getMessage(), e);
      }
    }

    // Handle updated candidates
    if (request.getUpdatedCandidates() != null) {
        for (CreateElectionRequest.UpdateCandidateDto updatedCandDto : request.getUpdatedCandidates()) {
            Candidate candidateToUpdate = candidateRepository.findById(updatedCandDto.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ứng viên để cập nhật với ID: " + updatedCandDto.getId()));
            
            candidateToUpdate.setName(updatedCandDto.getName());
            candidateToUpdate.setParty(updatedCandDto.getParty());
            candidateToUpdate.setDescription(updatedCandDto.getDescription());

            String rawBase64 = updatedCandDto.getBase64Image();
            if (rawBase64 != null && !rawBase64.trim().isEmpty() && !rawBase64.startsWith("http")) {
                try {
                    String cleanBase64Data = rawBase64.contains(",") ? rawBase64.split(",")[1] : rawBase64;
                    byte[] imageBytes = java.util.Base64.getDecoder().decode(cleanBase64Data.trim().replaceAll("\\s+", ""));
                    java.util.Map uploadResult = cloudinary.uploader().upload(imageBytes, com.cloudinary.utils.ObjectUtils.emptyMap());
                    String candImageUrl = (String) uploadResult.get("secure_url");
                    if (candImageUrl == null) candImageUrl = (String) uploadResult.get("url");
                    candidateToUpdate.setImageUrl(candImageUrl);
                } catch (Exception e) {
                    throw new RuntimeException("Không thể upload ảnh ứng viên lên Cloudinary: " + e.getMessage(), e);
                }
            }
            candidateRepository.save(candidateToUpdate);
        }
    }

    List<ElectionRound> existingRounds = roundRepository.findByElectionId(electionId);
    Map<Integer, ElectionRound> roundMap = existingRounds.stream()
        .collect(Collectors.toMap(ElectionRound::getRoundNumber, r -> r));

    for (ElectionRound r : existingRounds) {
        if (r.getRoundNumber() > request.getTotalRounds()) {
            roundRepository.delete(r);
        }
    }

    if (request.getRoundsTimeSettings() != null && !request.getRoundsTimeSettings().isEmpty()) {
        RoundTimeSettingDto firstRoundSetting = request.getRoundsTimeSettings().get(0);
        RoundTimeSettingDto lastRoundSetting = request.getRoundsTimeSettings().get(request.getRoundsTimeSettings().size() - 1);
        election.setStartTime(firstRoundSetting.getStartTime());
        election.setEndTime(lastRoundSetting.getEndTime());
        election.setStatus(calculateStatus(firstRoundSetting.getStartTime(), lastRoundSetting.getEndTime()));

        for (CreateElectionRequest.RoundTimeSettingDto roundSetting : request.getRoundsTimeSettings()) {
            ElectionRound round = roundMap.get(roundSetting.getRoundNumber());
            
            if (round == null) {
                round = new ElectionRound();
                round.setElection(election);
                round.setRoundNumber(roundSetting.getRoundNumber());
            }

            round.setTitle(roundSetting.getTitle());
            round.setDescription(roundSetting.getDescription());
            round.setStartTime(roundSetting.getStartTime());
            round.setEndTime(roundSetting.getEndTime());
            round.setMaxAdvanceCount(roundSetting.getMaxAdvanceCount() != null ? roundSetting.getMaxAdvanceCount() : 1);
            round.setStatus(calculateStatus(roundSetting.getStartTime(), roundSetting.getEndTime()));
            
            ElectionRound savedRound = roundRepository.save(round);

            if (round.getRoundNumber() == 1) {
                roundCandidateRepository.deleteAll(roundCandidateRepository.findByRoundId(savedRound.getId()));
                
                if (request.getCandidateIds() != null) {
                    for (Long candidateId : request.getCandidateIds()) {
                        RoundCandidate rc = new RoundCandidate();
                        rc.setRound(savedRound);
                        rc.setCandidateId(candidateId);
                        roundCandidateRepository.save(rc);
                    }
                }
                
                if (request.getNewCandidates() != null) {
                    for (CreateElectionRequest.NewCandidateDto newCandDto : request.getNewCandidates()) {
                        Candidate newCand = new Candidate();
                        newCand.setName(newCandDto.getName());
                        newCand.setParty(newCandDto.getParty());
                        newCand.setDescription(newCandDto.getDescription());
                        newCand.setElection(election);
                        
                        String rawBase64 = newCandDto.getBase64Image();
                        if (rawBase64 != null && !rawBase64.trim().isEmpty()) {
                            try {
                                String cleanBase64Data = rawBase64.contains(",") ? rawBase64.split(",")[1] : rawBase64;
                                byte[] imageBytes = java.util.Base64.getDecoder().decode(cleanBase64Data.trim().replaceAll("\\s+", ""));
                                java.util.Map uploadResult = cloudinary.uploader().upload(imageBytes, com.cloudinary.utils.ObjectUtils.emptyMap());
                                String newCandUrl = (String) uploadResult.get("secure_url");
                                if (newCandUrl == null) newCandUrl = (String) uploadResult.get("url");
                                newCand.setImageUrl(newCandUrl);
                            } catch (Exception e) {
                                throw new RuntimeException("Không thể upload ảnh ứng viên lên Cloudinary: " + e.getMessage(), e);
                            }
                        } else if (newCandDto.getImageUrl() != null && !newCandDto.getImageUrl().trim().isEmpty()) {
                            newCand.setImageUrl(newCandDto.getImageUrl());
                        } else {
                            newCand.setImageUrl("");
                        }

                        newCand = candidateRepository.save(newCand);
                        
                        RoundCandidate rc = new RoundCandidate();
                        rc.setRound(savedRound);
                        rc.setCandidateId(newCand.getId());
                        roundCandidateRepository.save(rc);
                    }
                }
            }
        }
    }
    
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

    boolean isEncrypted = request.getEncryptedVote() != null && !request.getEncryptedVote().isBlank();

    if (!isEncrypted) {
      // Chế độ cũ: candidateId rõ ràng → cập nhật vote count ngay
      Optional<Candidate> candidateOpt = candidateRepository.findById(request.getCandidateId());
      if (candidateOpt.isPresent()) {
        Candidate candidate = candidateOpt.get();
        candidate.setVoteCount(candidate.getVoteCount() + 1);
        candidateRepository.save(candidate);
      }
    }
    // Chế độ mã hóa: candidateId = null, chỉ đếm được sau khi giải mã

    UsedToken usedToken = new UsedToken();
    usedToken.setMessageToken(request.getMessageToken());
    usedToken.setRoundId(request.getRoundId());
    usedTokenRepository.save(usedToken);

    Vote vote = new Vote();
    vote.setElectionId(request.getElectionId());
    vote.setRoundId(request.getRoundId());
    vote.setMessageToken(request.getMessageToken());
    vote.setSignature(request.getSignature());

    if (isEncrypted) {
      // Phiếu mã hóa: candidateId = null, lưu ciphertext
      vote.setCandidateId(null);
      vote.setEncryptedVote(request.getEncryptedVote());
    } else {
      vote.setCandidateId(request.getCandidateId());
      vote.setEncryptedVote(null);
    }
    voteRepository.save(vote);

    // Broadcast vote counts ngay lập tức sau khi vote được lưu
    try {
      List<Map<String, Object>> stats = voteRepository.countVotesByCandidate(
          request.getElectionId(), request.getRoundId());
      realtimeNotificationService.voteCountUpdated(
          request.getElectionId(), request.getRoundId(), stats);
    } catch (Exception e) {
      log.error("[VoteCountUpdate] Không gửi được realtime update: {}", e.getMessage());
    }
  }

  @Transactional
  public void deleteElection(Long id) {
    Election election = electionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử"));
    electionRepository.delete(election);
  }

  @Transactional
  public Election getById(Long id) {
    Election election = electionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử với ID: " + id));
    org.hibernate.Hibernate.initialize(election.getCandidates());
    return election;
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
    return createMultiRoundElectionWithCandidates(request, null);
  }

  public Election createMultiRoundElectionWithCandidates(CreateElectionRequest request, Long creatorId) {
    Election election = new Election();
    election.setTitle(request.getTitle());
    election.setDescription(request.getDescription());
    election.setTotalRounds(request.getTotalRounds());
    election.setIsDelete(1);
    election.setRoleId(creatorId);

    validateRoundAdvancement(request);

    String electionRawBase64 = request.getBase64Image();
    if (electionRawBase64 != null && !electionRawBase64.trim().isEmpty()) {
      try {
        String cleanBase64 = electionRawBase64.contains(",") ? electionRawBase64.split(",")[1] : electionRawBase64;
        byte[] imageBytes = java.util.Base64.getDecoder().decode(cleanBase64.trim().replaceAll("\\s+", ""));
        java.util.Map uploadResult = cloudinary.uploader().upload(imageBytes, com.cloudinary.utils.ObjectUtils.emptyMap());
        String elecImageUrl = (String) uploadResult.get("secure_url");
        if (elecImageUrl == null) elecImageUrl = (String) uploadResult.get("url");
        election.setImageUrl(elecImageUrl);
      } catch (Exception e) {
        throw new RuntimeException("Không thể upload ảnh cuộc bầu cử lên Cloudinary: " + e.getMessage(), e);
      }
    } else if (request.getImageUrl() != null && !request.getImageUrl().trim().isEmpty()) {
      election.setImageUrl(request.getImageUrl());
    } else {
      election.setImageUrl("");
    }

    if (request.getRoundsTimeSettings() != null && !request.getRoundsTimeSettings().isEmpty()) {
      RoundTimeSettingDto firstRound = request.getRoundsTimeSettings().get(0);
      RoundTimeSettingDto lastRound = request.getRoundsTimeSettings().get(request.getRoundsTimeSettings().size() - 1);
      election.setStartTime(firstRound.getStartTime());
      election.setEndTime(lastRound.getEndTime());
      election.setStatus(calculateStatus(firstRound.getStartTime(), lastRound.getEndTime()));
    } else {
      election.setStatus("UPCOMING");
    }

    final Election savedElection = electionRepository.save(election);
    if (request.getRoundsTimeSettings() != null) {
      for (CreateElectionRequest.RoundTimeSettingDto roundSetting : request.getRoundsTimeSettings()) {
        ElectionRound round = new ElectionRound();
        round.setElection(savedElection);
        round.setRoundNumber(roundSetting.getRoundNumber());
        round.setTitle(roundSetting.getTitle());
        round.setDescription(roundSetting.getDescription());
        round.setStartTime(roundSetting.getStartTime());
        round.setEndTime(roundSetting.getEndTime());
        round.setMaxAdvanceCount(roundSetting.getMaxAdvanceCount() != null ? roundSetting.getMaxAdvanceCount() : 1);
        round.setStatus(calculateStatus(roundSetting.getStartTime(), roundSetting.getEndTime()));
        
        ElectionRound savedRound = roundRepository.save(round);

        if (roundSetting.getRoundNumber() == 1) {
          if (request.getCandidateIds() != null) {
            for (Long candidateId : request.getCandidateIds()) {
              RoundCandidate rc = new RoundCandidate();
              rc.setRound(savedRound);
              rc.setCandidateId(candidateId);
              roundCandidateRepository.save(rc);
            }
          }
          if (request.getNewCandidates() != null) {
            for (CreateElectionRequest.NewCandidateDto newCandDto : request.getNewCandidates()) {
              Candidate newCand = new Candidate();
              newCand.setName(newCandDto.getName());
              newCand.setParty(newCandDto.getParty());
              newCand.setDescription(newCandDto.getDescription());
              newCand.setElection(savedElection);
              
              // SỬA LỖI Ở ĐÂY: Bổ sung lại logic upload ảnh
              String rawBase64 = newCandDto.getBase64Image();
              if (rawBase64 != null && !rawBase64.trim().isEmpty()) {
                  try {
                      String cleanBase64Data = rawBase64.contains(",") ? rawBase64.split(",")[1] : rawBase64;
                      byte[] imageBytes = java.util.Base64.getDecoder().decode(cleanBase64Data.trim().replaceAll("\\s+", ""));
                      java.util.Map uploadResult = cloudinary.uploader().upload(imageBytes, com.cloudinary.utils.ObjectUtils.emptyMap());
                      String newCandUrl2 = (String) uploadResult.get("secure_url");
                      if (newCandUrl2 == null) newCandUrl2 = (String) uploadResult.get("url");
                      newCand.setImageUrl(newCandUrl2);
                  } catch (Exception e) {
                      throw new RuntimeException("Không thể upload ảnh ứng viên lên Cloudinary: " + e.getMessage(), e);
                  }
              } else if (newCandDto.getImageUrl() != null && !newCandDto.getImageUrl().trim().isEmpty()) {
                  newCand.setImageUrl(newCandDto.getImageUrl());
              } else {
                  newCand.setImageUrl("");
              }

              // Lưu ứng viên MỚI vào database
              candidateRepository.save(newCand);

              RoundCandidate rc = new RoundCandidate();
              rc.setRound(savedRound);
              rc.setCandidateId(newCand.getId());
              roundCandidateRepository.save(rc);
            }
          }
        }
      }
    }

    return savedElection;
  }

  private void validateRoundAdvancement(CreateElectionRequest request) {
    int totalCandidates = 0;
    if (request.getCandidateIds() != null) {
      totalCandidates += request.getCandidateIds().size();
    }
    if (request.getNewCandidates() != null) {
      totalCandidates += request.getNewCandidates().size();
    }

    if (totalCandidates < 2) {
      throw new IllegalArgumentException("Tổng số ứng viên vòng 1 phải từ 2 người trở lên.");
    }

    if (request.getTotalRounds() == null || request.getTotalRounds() < 1) {
      throw new IllegalArgumentException("Số vòng bầu cử không hợp lệ.");
    }

    if (request.getRoundsTimeSettings() == null || request.getRoundsTimeSettings().isEmpty()) {
      return;
    }

    List<RoundTimeSettingDto> sortedRounds = new ArrayList<>(request.getRoundsTimeSettings());
    sortedRounds.sort(Comparator.comparing(RoundTimeSettingDto::getRoundNumber));

    int availableCandidates = totalCandidates;
    for (RoundTimeSettingDto roundSetting : sortedRounds) {
      Integer roundNumber = roundSetting.getRoundNumber();
      if (roundNumber == null || roundNumber >= request.getTotalRounds()) {
        continue;
      }

      if (availableCandidates <= 1) {
        throw new IllegalArgumentException(
            "Vòng " + roundNumber + " chỉ còn " + availableCandidates + " ứng viên, không thể tạo thêm vòng tiếp theo.");
      }

      Integer maxAdvanceCount = roundSetting.getMaxAdvanceCount();
      if (maxAdvanceCount == null || maxAdvanceCount < 1) {
        throw new IllegalArgumentException(
            "Số ứng viên lọt vào Vòng " + (roundNumber + 1) + " phải lớn hơn 0.");
      }

      if (maxAdvanceCount >= availableCandidates) {
        throw new IllegalArgumentException(
            "Vòng " + roundNumber + " đang có " + availableCandidates
                + " ứng viên, số ứng viên lọt vào Vòng " + (roundNumber + 1)
                + " bắt buộc phải nhỏ hơn " + availableCandidates + ".");
      }

      availableCandidates = maxAdvanceCount;
    }
  }

  @Transactional(readOnly = true)
  public List<RoundDetailDto> getElectionDetailsWithRounds(Long electionId) {
    List<ElectionRound> rounds = roundRepository.findByElectionId(electionId);
    rounds.sort(Comparator.comparing(ElectionRound::getRoundNumber));

    List<RoundDetailDto> roundDetails = new ArrayList<>();

    for (int i = 0; i < rounds.size(); i++) {
        ElectionRound currentRound = rounds.get(i);
        List<Candidate> candidatesForRound = candidateRepository.findAllByRoundId(currentRound.getId());
        Map<Long, Long> voteCounts = new HashMap<>();

        if (!"UPCOMING".equals(currentRound.getStatus())) {
            List<Map<String, Object>> stats = voteRepository.countVotesByCandidate(electionId, currentRound.getId());
            stats.forEach(stat -> voteCounts.put(((Number) stat.get("candidateId")).longValue(), ((Number) stat.get("voteCount")).longValue()));
        }

        Set<Long> advancingCandidateIds = Collections.emptySet();
        if ("CLOSED".equals(currentRound.getStatus()) && (i + 1) < rounds.size()) {
            ElectionRound nextRound = rounds.get(i + 1);
            advancingCandidateIds = roundCandidateRepository.findByRoundId(nextRound.getId()).stream()
                                          .map(RoundCandidate::getCandidateId)
                                          .collect(Collectors.toSet());
        }

        final Set<Long> finalAdvancingIds = advancingCandidateIds;
        List<RoundCandidateInfo> candidateInfos = candidatesForRound.stream()
            .map(c -> {
                Boolean isAdvanced = null;
                if ("CLOSED".equals(currentRound.getStatus())) {
                    isAdvanced = finalAdvancingIds.contains(c.getId());
                }
                return RoundCandidateInfo.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .description(c.getDescription())
                    .imageUrl(c.getImageUrl())
                    .voteCount(voteCounts.getOrDefault(c.getId(), 0L))
                    .isAdvanced(isAdvanced)
                    .build();
            })
            .sorted(Comparator.comparing(RoundCandidateInfo::getVoteCount).reversed())
            .collect(Collectors.toList());

        roundDetails.add(RoundDetailDto.builder()
            .id(currentRound.getId())
            .roundNumber(currentRound.getRoundNumber())
            .title(currentRound.getTitle())
            .description(currentRound.getDescription())
            .startTime(currentRound.getStartTime())
            .endTime(currentRound.getEndTime())
            .status(currentRound.getStatus())
            .maxAdvanceCount(currentRound.getMaxAdvanceCount())
            .candidates(candidateInfos)
            .build());
    }

    return roundDetails;
  }
}
