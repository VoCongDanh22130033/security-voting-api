package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.CandidateResponse;
import com.nlu.electionservice.dto.CreateElectionRequest;
import com.nlu.electionservice.dto.CreateElectionRequest.RoundTimeSettingDto;
import com.nlu.electionservice.dto.RoundCandidateInfo;
import com.nlu.electionservice.dto.RoundDetailDto;
import com.nlu.electionservice.entity.Candidate;
import com.nlu.electionservice.entity.Department;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionRound;
import com.nlu.electionservice.entity.PublicBulletinBoard;
import com.nlu.electionservice.entity.RoundCandidate;
import com.nlu.electionservice.entity.UsedToken;
import com.nlu.electionservice.entity.Vote;
import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.DepartmentRepository;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import com.nlu.electionservice.repository.ElectionVoterRepository;
import com.nlu.electionservice.repository.PublicBulletinBoardRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ElectionService {
  
  private final ElectionRepository electionRepository;
  private final com.cloudinary.Cloudinary cloudinary;
  private final CandidateRepository candidateRepository;
  private final RoundCandidateRepository roundCandidateRepository;
  private final ElectionRoundRepository roundRepository;
  private final com.nlu.electionservice.repository.UsedTokenRepository usedTokenRepository;
  private final com.nlu.electionservice.repository.VoteRepository voteRepository;
  private final HomomorphicEncryptionService encryptionService;
  private final PublicBulletinBoardRepository publicBulletinBoardRepository;
  private final DepartmentRepository departmentRepository;
  private final ElectionVoterRepository electionVoterRepository;
  private final ElectionParticipantInviteService participantInviteService;


  @Autowired
  public ElectionService(ElectionRepository electionRepository, 
                         com.cloudinary.Cloudinary cloudinary, 
                         CandidateRepository candidateRepository, 
                         RoundCandidateRepository roundCandidateRepository, 
                         ElectionRoundRepository roundRepository, 
                         com.nlu.electionservice.repository.UsedTokenRepository usedTokenRepository, 
                          com.nlu.electionservice.repository.VoteRepository voteRepository, 
                          HomomorphicEncryptionService encryptionService, 
                          PublicBulletinBoardRepository publicBulletinBoardRepository,
                          DepartmentRepository departmentRepository,
                          ElectionVoterRepository electionVoterRepository,
                          ElectionParticipantInviteService participantInviteService) {
      this.electionRepository = electionRepository;
      this.cloudinary = cloudinary;
      this.candidateRepository = candidateRepository;
      this.roundCandidateRepository = roundCandidateRepository;
      this.roundRepository = roundRepository;
      this.usedTokenRepository = usedTokenRepository;
      this.voteRepository = voteRepository;
      this.encryptionService = encryptionService;
      this.publicBulletinBoardRepository = publicBulletinBoardRepository;
      this.departmentRepository = departmentRepository;
      this.electionVoterRepository = electionVoterRepository;
      this.participantInviteService = participantInviteService;
  }

  @Transactional
  public void processRoundAfterClose(Long electionId, Long roundId) {
    ElectionRound round = roundRepository.findById(roundId)
            .orElseThrow(() -> new RuntimeException("Round not found"));

    if (!"CLOSED".equals(round.getStatus())) {
      round.setStatus("CLOSED");
      roundRepository.save(round);
    }

    List<Map<String, Object>> stats = voteRepository.countVotesByCandidate(electionId, roundId);

    long totalVotes = 0;
    long highestVotes = 0;
    Long absoluteWinnerId = null;

    if (!stats.isEmpty()) {
        highestVotes = ((Number) stats.get(0).get("voteCount")).longValue();
        absoluteWinnerId = ((Number) stats.get(0).get("candidateId")).longValue();
        for (Map<String, Object> stat : stats) {
            totalVotes += ((Number) stat.get("voteCount")).longValue();
        }
    }

    boolean hasAbsoluteWinner = (totalVotes > 0 && highestVotes == totalVotes);

    Election parentElection = round.getElection();
    java.util.Optional<ElectionRound> nextRoundOpt = roundRepository.findByElectionIdAndRoundNumber(electionId, round.getRoundNumber() + 1);

    if (hasAbsoluteWinner) {
        System.out.println(">>> [CHIẾN THẮNG TUYỆT ĐỐI] Ứng viên ID " + absoluteWinnerId + " đã giành 100% phiếu bầu. Kết thúc bầu cử!");
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
        synchronizeVoteCounts(electionId);
        return;
    }

    int advance = round.getMaxAdvanceCount();
    List<Long> advancing = stats.stream()
            .limit(advance)
            .map(row -> ((Number) row.get("candidateId")).longValue())
            .collect(Collectors.toList());

    if (nextRoundOpt.isPresent()) {
      ElectionRound nextRound = nextRoundOpt.get();
      roundCandidateRepository.deleteAll(roundCandidateRepository.findByRoundId(nextRound.getId()));
      for (Long cid : advancing) {
        RoundCandidate rc = new RoundCandidate();
        rc.setRound(nextRound);
        rc.setCandidateId(cid);
        roundCandidateRepository.save(rc);
      }
      boolean nextRoundStarted = nextRound.getStartTime() == null || !LocalDateTime.now().isBefore(nextRound.getStartTime());
      nextRound.setStatus(nextRoundStarted ? "OPEN" : "UPCOMING");
      roundRepository.save(nextRound);
      if (nextRoundStarted) {
        try {
          participantInviteService.sendRoundInvitations(electionId, nextRound.getRoundNumber());
        } catch (Exception inviteEx) {
          throw new RuntimeException("Da mo vong tiep theo nhung khong gui duoc email moi: " + inviteEx.getMessage(), inviteEx);
        }
      }
    } else {
      if (!advancing.isEmpty()) {
        parentElection.setWinnerId(advancing.get(0));
      }
      parentElection.setStatus("CLOSED");
      electionRepository.save(parentElection);
      synchronizeVoteCounts(electionId);
    }
  }

  @Transactional
  public void determineElectionWinner(Long electionId) {
    Election election = electionRepository.findById(electionId)
            .orElseThrow(() -> new RuntimeException("Election not found"));

    ElectionRound round = roundRepository.findByElectionIdAndRoundNumber(electionId, election.getTotalRounds())
            .orElseThrow(() -> new RuntimeException("Final round not found"));

    List<Map<String, Object>> stats = voteRepository.countVotesByCandidate(electionId, round.getId());
    if (!stats.isEmpty()) {
      Long winnerId = ((Number) stats.get(0).get("candidateId")).longValue();
      election.setWinnerId(winnerId);
      electionRepository.save(election);
    }
  }

  @Transactional
  public void synchronizeVoteCounts(Long electionId) {
    List<ElectionRound> rounds = roundRepository.findByElectionId(electionId);
    Map<Long, Long> totalVoteCounts = new HashMap<>();

    for (ElectionRound round : rounds) {
      if (!"CANCELLED".equals(round.getStatus())) {
        List<Map<String, Object>> stats = voteRepository.countVotesByCandidate(electionId, round.getId());
        for (Map<String, Object> stat : stats) {
          Long candidateId = ((Number) stat.get("candidateId")).longValue();
          Long voteCount = ((Number) stat.get("voteCount")).longValue();
          totalVoteCounts.merge(candidateId, voteCount, Long::sum);
        }
      }
    }

    List<Candidate> candidatesToUpdate = candidateRepository.findAllById(totalVoteCounts.keySet());
    for (Candidate candidate : candidatesToUpdate) {
      candidate.setVoteCount(totalVoteCounts.get(candidate.getId()).intValue());
    }
    candidateRepository.saveAll(candidatesToUpdate);
  }

  public List<CandidateResponse> getElectionResults(Long electionId) {
    Election election = electionRepository.findById(electionId)
            .orElseThrow(() -> new RuntimeException("Election not found"));

    List<ElectionRound> rounds = roundRepository.findByElectionId(electionId);
    ElectionRound finalValidRound = rounds.stream()
            .filter(r -> "CLOSED".equals(r.getStatus()) || "OPEN".equals(r.getStatus()))
            .max(java.util.Comparator.comparing(ElectionRound::getRoundNumber))
            .orElseThrow(() -> new RuntimeException("No valid round found"));

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
    configureElectionAudience(election, request);
    validateRoundAdvancement(request);

    String electionRawBase64 = request.getBase64Image();
    if (electionRawBase64 != null && !electionRawBase64.trim().isEmpty() && !electionRawBase64.startsWith("http")) {
      try {
        String cleanBase64 = electionRawBase64.contains(",") ? electionRawBase64.split(",")[1] : electionRawBase64;
        byte[] imageBytes = java.util.Base64.getDecoder().decode(cleanBase64.trim().replaceAll("\\s+", ""));
        java.util.Map uploadResult = cloudinary.uploader().upload(imageBytes, com.cloudinary.utils.ObjectUtils.emptyMap());
        election.setImageUrl((String) uploadResult.get("url"));
      } catch (Exception e) {
        System.err.println("Lỗi upload ảnh cuộc bầu cử: " + e.getMessage());
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
                    candidateToUpdate.setImageUrl((String) uploadResult.get("url"));
                } catch (Exception e) {
                    System.err.println("Lỗi upload ảnh ứng viên: " + e.getMessage());
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
                                newCand.setImageUrl((String) uploadResult.get("url"));
                            } catch (Exception e) {
                                System.err.println("Lỗi upload ảnh ứng viên: " + e.getMessage());
                                newCand.setImageUrl("");
                            }
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
    
    Election savedElection = electionRepository.save(election);
    assignEligibleVoters(savedElection);
    return savedElection;
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

    Optional<Candidate> candidateOpt = candidateRepository.findById(request.getCandidateId());
    if (candidateOpt.isPresent()) {
      Candidate candidate = candidateOpt.get();
      candidate.setVoteCount(candidate.getVoteCount() + 1);
      candidateRepository.save(candidate);
    }

    UsedToken usedToken = new UsedToken();
    usedToken.setMessageToken(request.getMessageToken());
    usedToken.setRoundId(request.getRoundId());
    usedTokenRepository.save(usedToken);

    Vote vote = new Vote();
    vote.setElectionId(request.getElectionId());
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
    Election election = new Election();
    election.setTitle(request.getTitle());
    election.setDescription(request.getDescription());
    election.setTotalRounds(request.getTotalRounds());
    election.setIsDelete(1);
    configureElectionAudience(election, request);

    // E2EV: Tạo khóa ElGamal cho cuộc bầu cử
    validateRoundAdvancement(request);
    HomomorphicEncryptionService.ElGamalKeyPair keyPair = encryptionService.generateKeyPair(1024);
    election.setElGamalP(keyPair.p.toString(16));
    election.setElGamalG(keyPair.g.toString(16));
    election.setElGamalH(keyPair.h.toString(16));
    election.setElGamalX(keyPair.x.toString(16));

    String electionRawBase64 = request.getBase64Image();
    if (electionRawBase64 != null && !electionRawBase64.trim().isEmpty()) {
      try {
        String cleanBase64 = electionRawBase64.contains(",") ? electionRawBase64.split(",")[1] : electionRawBase64;
        byte[] imageBytes = java.util.Base64.getDecoder().decode(cleanBase64.trim().replaceAll("\\s+", ""));
        java.util.Map uploadResult = cloudinary.uploader().upload(imageBytes, com.cloudinary.utils.ObjectUtils.emptyMap());
        election.setImageUrl((String) uploadResult.get("url"));
      } catch (Exception e) {
        System.err.println("Lỗi upload ảnh cuộc bầu cử: " + e.getMessage());
        election.setImageUrl("");
      }
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
    // E2EV: Lưu Public Key lên Bulletin Board
    PublicBulletinBoard pbb = new PublicBulletinBoard();
    pbb.setElectionId(savedElection.getId());
    pbb.setEventType("ELECTION_CREATED_PUBLIC_KEY");
    pbb.setPayload("{\"p\":\"" + keyPair.p.toString(16) + "\",\"g\":\"" + keyPair.g.toString(16) + "\",\"h\":\"" + keyPair.h.toString(16) + "\"}");
    publicBulletinBoardRepository.save(pbb);

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
                      newCand.setImageUrl((String) uploadResult.get("url"));
                  } catch (Exception e) {
                      System.err.println("Lỗi upload ảnh ứng viên: " + e.getMessage());
                      newCand.setImageUrl("");
                  }
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
      throw new IllegalArgumentException("Tong so ung vien vong 1 phai tu 2 nguoi tro len.");
    }

    if (request.getTotalRounds() == null || request.getTotalRounds() < 1) {
      throw new IllegalArgumentException("So vong bau cu khong hop le.");
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
            "Vong " + roundNumber + " chi con " + availableCandidates + " ung vien, khong the tao them vong tiep theo.");
      }

      Integer maxAdvanceCount = roundSetting.getMaxAdvanceCount();
      if (maxAdvanceCount == null || maxAdvanceCount < 1) {
        throw new IllegalArgumentException(
            "So ung vien lot vao Vong " + (roundNumber + 1) + " phai lon hon 0.");
      }

      if (maxAdvanceCount >= availableCandidates) {
        throw new IllegalArgumentException(
            "Vong " + roundNumber + " dang co " + availableCandidates
                + " ung vien, so ung vien lot vao Vong " + (roundNumber + 1)
                + " bat buoc phai nho hon " + availableCandidates + ".");
      }

      availableCandidates = maxAdvanceCount;
    }
  }

  private void configureElectionAudience(Election election, CreateElectionRequest request) {
    if ("DEPARTMENT_SPECIFIC".equalsIgnoreCase(request.getAudienceType())) {
      if (request.getDepartmentIds() == null || request.getDepartmentIds().isEmpty()) {
        throw new RuntimeException("Vui long chon it nhat mot phong ban duoc tham gia bau cu.");
      }

      List<Department> departments = departmentRepository.findAllById(request.getDepartmentIds());
      if (departments.size() != request.getDepartmentIds().size()) {
        throw new RuntimeException("Danh sach phong ban tham gia bau cu khong hop le.");
      }

      election.setAudienceType(Election.AudienceType.DEPARTMENT_SPECIFIC);
      election.setTargetDepartments(new java.util.HashSet<>(departments));
      return;
    }

    election.setAudienceType(Election.AudienceType.COMPANY_WIDE);
    election.getTargetDepartments().clear();
  }

  private void assignEligibleVoters(Election election) {
    electionVoterRepository.deleteByElectionId(election.getId());
    if (Election.AudienceType.DEPARTMENT_SPECIFIC.equals(election.getAudienceType())) {
      List<Long> departmentIds = election.getTargetDepartments().stream()
          .map(Department::getId)
          .collect(Collectors.toList());
      if (!departmentIds.isEmpty()) {
        electionVoterRepository.insertDepartmentEligibleVoters(election.getId(), departmentIds);
      }
      return;
    }

    electionVoterRepository.insertCompanyWideEligibleVoters(election.getId());
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
