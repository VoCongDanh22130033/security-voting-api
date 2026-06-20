package com.nlu.electionservice.service;

import com.nlu.electionservice.entity.BlindSignatureLog;
import com.nlu.electionservice.entity.Candidate;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionRound;
import com.nlu.electionservice.entity.ElectionVoterInvite;
import com.nlu.electionservice.entity.Vote;
import com.nlu.electionservice.repository.BlindSignatureLogRepository;
import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import com.nlu.electionservice.repository.ElectionVoterInviteRepository;
import com.nlu.electionservice.repository.VoteRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

@Service
public class ElectionReportService {

  private final ElectionRepository electionRepository;
  private final ElectionRoundRepository roundRepository;
  private final VoteRepository voteRepository;
  private final CandidateRepository candidateRepository;
  private final ElectionVoterInviteRepository inviteRepository;
  private final BlindSignatureLogRepository blindSignatureLogRepository;

  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  public ElectionReportService(
      ElectionRepository electionRepository,
      ElectionRoundRepository roundRepository,
      VoteRepository voteRepository,
      CandidateRepository candidateRepository,
      ElectionVoterInviteRepository inviteRepository,
      BlindSignatureLogRepository blindSignatureLogRepository) {
    this.electionRepository = electionRepository;
    this.roundRepository = roundRepository;
    this.voteRepository = voteRepository;
    this.candidateRepository = candidateRepository;
    this.inviteRepository = inviteRepository;
    this.blindSignatureLogRepository = blindSignatureLogRepository;
  }

  public byte[] exportExcel(Long electionId, String exportedBy) throws Exception {
    Election election = getElection(electionId);
    List<ElectionRound> rounds = getRounds(electionId);
    List<ElectionVoterInvite> allInvites = inviteRepository.findByElectionIdOrderByCreatedAtDesc(electionId);
    List<Candidate> candidates = candidateRepository.findByElectionId(electionId);
    String exportedAt = LocalDateTime.now().format(FMT);

    boolean electionClosed = "CLOSED".equalsIgnoreCase(election.getStatus());
    ElectionRound finalRound = electionClosed && !rounds.isEmpty() ? rounds.get(rounds.size() - 1) : null;

    // ── Compute stats ──────────────────────────────────────────────────────
    long totalInvited = allInvites.stream().map(ElectionVoterInvite::getEmail).filter(e -> e != null).map(String::toLowerCase).distinct().count();
    long totalVerified = allInvites.stream().filter(i -> i.getVerifiedAt() != null).map(ElectionVoterInvite::getEmail).filter(e -> e != null).map(String::toLowerCase).distinct().count();
    long totalVoted = blindSignatureLogRepository.countDistinctVotedByElectionId(electionId);
    long notVoted = Math.max(0, totalInvited - totalVoted);
    double participationRate = totalInvited > 0 ? (totalVoted * 100.0 / totalInvited) : 0;
    long totalVotesCast = voteRepository.countByElectionId(electionId);
    long validVotes = voteRepository.countByElectionIdAndCandidateIdIsNotNull(electionId);
    long invalidVotes = totalVotesCast - validVotes;

    // Final round stats for TALLY / charts
    List<Map<String, Object>> finalStats = finalRound != null
        ? voteRepository.countVotesByCandidate(electionId, finalRound.getId())
        : List.of();
    Map<Long, Long> finalVcMap = finalStats.stream().collect(Collectors.toMap(
        s -> ((Number) s.get("candidateId")).longValue(),
        s -> ((Number) s.get("voteCount")).longValue(),
        (a, b) -> a));
    List<Candidate> finalSorted = candidates.stream()
        .sorted((a, b) -> Long.compare(finalVcMap.getOrDefault(b.getId(), 0L), finalVcMap.getOrDefault(a.getId(), 0L)))
        .collect(Collectors.toList());

    try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      // ════════════════════════════════════════════════════════════════════
      // Styles
      // ════════════════════════════════════════════════════════════════════
      CellStyle hStyle = createHeaderStyle(wb);
      CellStyle titleStyle = createTitleStyle(wb, (short) 14);
      CellStyle sectionStyle = createTitleStyle(wb, (short) 11);
      CellStyle centeredStyle = wb.createCellStyle(); centeredStyle.setAlignment(HorizontalAlignment.CENTER);
      CellStyle greenStyle = createColorStyle(wb, IndexedColors.GREEN, true);
      CellStyle redStyle   = createColorStyle(wb, IndexedColors.RED, false);
      CellStyle goldStyle  = createColorStyle(wb, IndexedColors.GOLD, true);
      CellStyle grayStyle  = createColorStyle(wb, IndexedColors.GREY_50_PERCENT, false);
      CellStyle wrapStyle  = wb.createCellStyle(); wrapStyle.setWrapText(true);

      // ════════════════════════════════════════════════════════════════════
      // Sheet 1 — THONG_TIN_CUOC_BAU_CU
      // ════════════════════════════════════════════════════════════════════
      XSSFSheet sInfo = wb.createSheet("THONG_TIN_CUOC_BAU_CU");
      int r = 0;
      Cell h = sInfo.createRow(r++).createCell(0);
      h.setCellValue("THÔNG TIN CUỘC BẦU CỬ");
      h.setCellStyle(titleStyle);
      sInfo.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));
      r++;

      Row ih = sInfo.createRow(r++);
      styledCell(ih, 0, "Thuộc tính", hStyle);
      styledCell(ih, 1, "Giá trị", hStyle);

      r = pair(sInfo, r, "Mã cuộc bầu cử", electionId);
      r = pair(sInfo, r, "Tên cuộc bầu cử", election.getTitle());
      r = pair(sInfo, r, "Mô tả", election.getDescription() != null ? election.getDescription() : "—");
      r = pair(sInfo, r, "Ngày bắt đầu", fmt(election.getStartTime()));
      r = pair(sInfo, r, "Ngày kết thúc", fmt(election.getEndTime()));
      r = pair(sInfo, r, "Trạng thái", statusLabel(election.getStatus()));
      r = pair(sInfo, r, "Phương thức mã hóa", "RSA-2048 Blind Signature + RSA-OAEP");
      r = pair(sInfo, r, "Tổng số vòng bầu", election.getTotalRounds());
      r = pair(sInfo, r, "Số ứng viên", candidates.size());
      r = pair(sInfo, r, "Số cử tri được mời", totalInvited);
      r++;
      r = pair(sInfo, r, "Ngày xuất báo cáo", exportedAt);
      r = pair(sInfo, r, "Người xuất báo cáo", exportedBy != null && !exportedBy.isBlank() ? exportedBy : "Hệ thống");
      autosize(sInfo, 2);

      // ════════════════════════════════════════════════════════════════════
      // Sheet 2 — Tổng quan (stats table + charts)
      // ════════════════════════════════════════════════════════════════════
      XSSFSheet sOv = wb.createSheet("Tổng quan");
      r = 0;
      Cell ovTitle = sOv.createRow(r++).createCell(0);
      ovTitle.setCellValue("TỔNG QUAN CUỘC BẦU CỬ — " + election.getTitle());
      ovTitle.setCellStyle(titleStyle);
      sOv.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
      r++;

      // Stats section header
      Cell sh = sOv.createRow(r++).createCell(0);
      sh.setCellValue("CHỈ SỐ THỐNG KÊ");
      sh.setCellStyle(sectionStyle);
      r++;

      // Stats table header (for chart data source)
      Row statsHead = sOv.createRow(r++);
      styledCell(statsHead, 0, "Chỉ số", hStyle);
      styledCell(statsHead, 1, "Giá trị", hStyle);

      int statsDataStart = r;
      r = pair(sOv, r, "Tổng cử tri", totalInvited);
      r = pair(sOv, r, "Đã xác thực CCCD", totalVerified);
      r = pair(sOv, r, "Đã bỏ phiếu", totalVoted);
      r = pair(sOv, r, "Chưa bỏ phiếu", notVoted);
      r = pair(sOv, r, "Tỷ lệ tham gia", String.format("%.1f%%", participationRate));
      r = pair(sOv, r, "Phiếu hợp lệ", validVotes);
      r = pair(sOv, r, "Phiếu không hợp lệ", invalidVotes);
      int statsDataEnd = r - 1;

      // Color code rows
      for (int ri = statsDataStart; ri <= statsDataEnd; ri++) {
        Row row = sOv.getRow(ri);
        if (row == null) continue;
        Cell valCell = row.getCell(1);
        if (valCell == null) continue;
        String label = row.getCell(0) != null ? row.getCell(0).getStringCellValue() : "";
        if (label.contains("không hợp lệ") || label.contains("Chưa")) valCell.setCellStyle(redStyle);
        else if (label.contains("Đã") || label.contains("hợp lệ")) valCell.setCellStyle(greenStyle);
      }
      sOv.autoSizeColumn(0);
      sOv.setColumnWidth(1, 5000);

      // ── Pie chart — participation rate ──────────────────────────────────
      r += 2;
      // Write labels + values for pie chart data
      int pieDataRow = r;
      sOv.createRow(r).createCell(0).setCellValue("Đã bỏ phiếu");
      sOv.getRow(r++).createCell(1).setCellValue((double) totalVoted);
      sOv.createRow(r).createCell(0).setCellValue("Chưa bỏ phiếu");
      sOv.getRow(r++).createCell(1).setCellValue((double) notVoted);

      // Embed pie chart anchored right of the stats table
      if (totalInvited > 0) {
        XSSFDrawing pieDrawing = sOv.createDrawingPatriarch();
        XSSFClientAnchor pieAnchor = pieDrawing.createAnchor(0, 0, 0, 0, 3, 4, 9, 18);
        XSSFChart pieChart = pieDrawing.createChart(pieAnchor);
        pieChart.setTitleText("Tỷ lệ tham gia bầu cử");
        pieChart.setTitleOverlay(false);
        XDDFChartLegend pieLegend = pieChart.getOrAddLegend();
        pieLegend.setPosition(LegendPosition.BOTTOM);

        XDDFDataSource<String> pieCats = XDDFDataSourcesFactory.fromStringCellRange(sOv,
            new CellRangeAddress(pieDataRow, pieDataRow + 1, 0, 0));
        XDDFNumericalDataSource<Double> pieVals = XDDFDataSourcesFactory.fromNumericCellRange(sOv,
            new CellRangeAddress(pieDataRow, pieDataRow + 1, 1, 1));

        XDDFPieChartData pieData = (XDDFPieChartData) pieChart.createData(ChartTypes.PIE, null, null);
        pieData.setVaryColors(true);
        XDDFPieChartData.Series pieSeries = (XDDFPieChartData.Series) pieData.addSeries(pieCats, pieVals);
        pieSeries.setTitle("Tỷ lệ", null);
        pieChart.plot(pieData);
      }

      r += 2;

      // ── Winners section ────────────────────────────────────────────────
      if (electionClosed && finalRound != null && !finalSorted.isEmpty()) {
        Cell wh2 = sOv.createRow(r++).createCell(0);
        wh2.setCellValue("NGƯỜI TRÚNG CỬ CUỐI CÙNG");
        wh2.setCellStyle(sectionStyle);
        r++;

        Row whRow = sOv.createRow(r++);
        styledCell(whRow, 0, "STT", hStyle);
        styledCell(whRow, 1, "Họ tên ứng viên", hStyle);
        styledCell(whRow, 2, "Số phiếu", hStyle);
        styledCell(whRow, 3, "Tỷ lệ (%)", hStyle);

        int advance = finalRound.getMaxAdvanceCount();
        long totalFinalVotes = finalVcMap.values().stream().mapToLong(Long::longValue).sum();
        int wStt = 1;
        for (int wi = 0; wi < finalSorted.size(); wi++) {
          Candidate wc = finalSorted.get(wi);
          long wvotes = finalVcMap.getOrDefault(wc.getId(), 0L);
          boolean isWinner = advance > 0 ? wi < advance : wi == 0;
          if (!isWinner || wvotes == 0) break;
          double wpct = totalFinalVotes > 0 ? (wvotes * 100.0 / totalFinalVotes) : 0;
          Row wr = sOv.createRow(r++);
          wr.createCell(0).setCellValue(wStt++);
          Cell nameCell = wr.createCell(1);
          nameCell.setCellValue("🏆 " + (wc.getName() != null ? wc.getName() : ""));
          nameCell.setCellStyle(goldStyle);
          wr.createCell(2).setCellValue(wvotes);
          wr.createCell(3).setCellValue(Math.round(wpct * 10.0) / 10.0);
        }
        sOv.autoSizeColumn(1);
      }

      // ════════════════════════════════════════════════════════════════════
      // Sheet 3 — TALLY_REPORT
      // ════════════════════════════════════════════════════════════════════
      XSSFSheet sTally = wb.createSheet("TALLY_REPORT");
      r = 0;
      Cell tallyTitle = sTally.createRow(r++).createCell(0);
      tallyTitle.setCellValue("TALLY REPORT — KẾT QUẢ KIỂM PHIẾU");
      tallyTitle.setCellStyle(titleStyle);
      sTally.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
      r++;

      // Summary totals
      Row th = sTally.createRow(r++);
      styledCell(th, 0, "Nội dung", hStyle);
      styledCell(th, 1, "Giá trị", hStyle);

      r = pair(sTally, r, "Tổng phiếu đã nhận", totalVotesCast);
      r = pair(sTally, r, "Phiếu hợp lệ", validVotes);
      r = pair(sTally, r, "Phiếu không hợp lệ", invalidVotes);
      r = pair(sTally, r, "Tỷ lệ hợp lệ", totalVotesCast > 0
          ? String.format("%.1f%%", validVotes * 100.0 / totalVotesCast) : "—");

      // Winner row
      if (!finalSorted.isEmpty()) {
        long topVotes = finalVcMap.getOrDefault(finalSorted.get(0).getId(), 0L);
        r = pair(sTally, r, "Người trúng cử", finalSorted.get(0).getName() != null ? finalSorted.get(0).getName() : "—");
        r = pair(sTally, r, "Số phiếu người trúng cử", topVotes);
      }
      r++;

      // Per-round results + bar chart data
      for (ElectionRound round : rounds) {
        Cell rdTitle = sTally.createRow(r++).createCell(0);
        rdTitle.setCellValue((round.getTitle() != null ? round.getTitle() : "Vòng " + round.getRoundNumber())
            + " — " + statusLabel(round.getStatus()));
        rdTitle.setCellStyle(sectionStyle);

        Row rdHdr = sTally.createRow(r++);
        styledCell(rdHdr, 0, "Hạng", hStyle);
        styledCell(rdHdr, 1, "Ứng viên", hStyle);
        styledCell(rdHdr, 2, "Số phiếu", hStyle);
        styledCell(rdHdr, 3, "Tỷ lệ (%)", hStyle);
        styledCell(rdHdr, 4, "Kết quả", hStyle);

        List<Map<String, Object>> rdStats = voteRepository.countVotesByCandidate(electionId, round.getId());
        Map<Long, Long> rdVcMap = rdStats.stream().collect(Collectors.toMap(
            s -> ((Number) s.get("candidateId")).longValue(),
            s -> ((Number) s.get("voteCount")).longValue(),
            (a, b) -> a));
        List<Candidate> rdSorted = candidates.stream()
            .sorted((a, b) -> Long.compare(rdVcMap.getOrDefault(b.getId(), 0L), rdVcMap.getOrDefault(a.getId(), 0L)))
            .collect(Collectors.toList());
        long rdTotal = rdVcMap.values().stream().mapToLong(Long::longValue).sum();
        int rdAdvance = round.getMaxAdvanceCount();
        boolean isLastRound = finalRound != null && round.getId().equals(finalRound.getId());

        int chartDataStart = r;
        for (int i = 0; i < rdSorted.size(); i++) {
          Candidate c = rdSorted.get(i);
          long votes = rdVcMap.getOrDefault(c.getId(), 0L);
          double pct = rdTotal > 0 ? (votes * 100.0 / rdTotal) : 0;
          boolean qualified = rdAdvance > 0 && i < rdAdvance;
          Row cr = sTally.createRow(r++);
          cr.createCell(0).setCellValue(i + 1);
          cr.createCell(1).setCellValue(c.getName() != null ? c.getName() : "");
          cr.createCell(2).setCellValue(votes);
          cr.createCell(3).setCellValue(Math.round(pct * 10.0) / 10.0);
          Cell resCell = cr.createCell(4);
          if (rdAdvance > 0) {
            String label = qualified ? (isLastRound ? "🏆 Trúng cử" : (i == 0 ? "🏆 Dẫn đầu" : "✅ Vào vòng tiếp")) : "❌ Bị loại";
            resCell.setCellValue(label);
            resCell.setCellStyle(qualified ? (i == 0 ? goldStyle : greenStyle) : redStyle);
          }
        }
        int chartDataEnd = r - 1;

        // Add bar chart for this round
        if (rdSorted.size() > 0 && rdTotal > 0) {
          XSSFDrawing barDraw = sTally.createDrawingPatriarch();
          XSSFClientAnchor barAnchor = barDraw.createAnchor(0, 0, 0, 0, 5, chartDataStart - 1, 14, chartDataEnd + 3);
          XSSFChart barChart = barDraw.createChart(barAnchor);
          barChart.setTitleText("Số phiếu — " + (round.getTitle() != null ? round.getTitle() : "Vòng " + round.getRoundNumber()));
          barChart.setTitleOverlay(false);
          XDDFChartLegend legend = barChart.getOrAddLegend();
          legend.setPosition(LegendPosition.BOTTOM);

          XDDFCategoryAxis catAxis = barChart.createCategoryAxis(AxisPosition.BOTTOM);
          XDDFValueAxis valAxis = barChart.createValueAxis(AxisPosition.LEFT);
          valAxis.setCrosses(AxisCrosses.AUTO_ZERO);

          XDDFDataSource<String> catSrc = XDDFDataSourcesFactory.fromStringCellRange(sTally,
              new CellRangeAddress(chartDataStart, chartDataEnd, 1, 1));
          XDDFNumericalDataSource<Double> valSrc = XDDFDataSourcesFactory.fromNumericCellRange(sTally,
              new CellRangeAddress(chartDataStart, chartDataEnd, 2, 2));

          XDDFBarChartData barData = (XDDFBarChartData) barChart.createData(ChartTypes.BAR, catAxis, valAxis);
          barData.setBarDirection(BarDirection.COL);
          XDDFBarChartData.Series barSeries = (XDDFBarChartData.Series) barData.addSeries(catSrc, valSrc);
          barSeries.setTitle("Số phiếu", null);
          barChart.plot(barData);
        }

        r += 2;
      }
      sTally.autoSizeColumn(0);
      sTally.autoSizeColumn(1);
      sTally.setColumnWidth(4, 4000);

      // ════════════════════════════════════════════════════════════════════
      // Sheet 4 — Danh sách cử tri
      // ════════════════════════════════════════════════════════════════════
      XSSFSheet s2 = wb.createSheet("Danh sách cử tri");
      Row h2 = s2.createRow(0);
      int col = 0;
      styledCell(h2, col++, "STT", hStyle);
      styledCell(h2, col++, "Email", hStyle);
      styledCell(h2, col++, "Họ tên", hStyle);
      styledCell(h2, col++, "Đã xác thực CCCD", hStyle);
      for (ElectionRound rd : rounds) {
        styledCell(h2, col++, (rd.getTitle() != null ? rd.getTitle() : "Vòng " + rd.getRoundNumber()), hStyle);
      }
      styledCell(h2, col++, "Tổng vòng tham gia", hStyle);
      styledCell(h2, col, "Trạng thái tổng", hStyle);

      Map<String, List<ElectionVoterInvite>> byEmail = allInvites.stream()
          .collect(Collectors.groupingBy(i -> i.getEmail() != null ? i.getEmail().toLowerCase() : "unknown"));
      int rowIdx2 = 1, stt2 = 1;
      for (Map.Entry<String, List<ElectionVoterInvite>> entry : byEmail.entrySet()) {
        List<ElectionVoterInvite> invs = entry.getValue();
        ElectionVoterInvite sample = invs.get(0);
        boolean verified = invs.stream().anyMatch(i -> i.getVerifiedAt() != null);
        Long voterId = invs.stream().filter(i -> i.getVoterId() != null).map(ElectionVoterInvite::getVoterId).findFirst().orElse(null);
        Row row = s2.createRow(rowIdx2++);
        col = 0;
        row.createCell(col++).setCellValue(stt2++);
        row.createCell(col++).setCellValue(sample.getEmail() != null ? sample.getEmail() : "");
        row.createCell(col++).setCellValue(sample.getFullName() != null ? sample.getFullName() : "");
        Cell verCell = row.createCell(col++);
        verCell.setCellValue(verified ? "Đã xác thực" : "Chưa xác thực");
        verCell.setCellStyle(verified ? greenStyle : redStyle);
        int roundsVoted = 0;
        for (ElectionRound rd : rounds) {
          boolean voted = voterId != null &&
              blindSignatureLogRepository.existsByUserIdAndElectionIdAndRoundId(voterId, electionId, rd.getId());
          Cell vc = row.createCell(col++);
          vc.setCellValue(voted ? "Đã bỏ phiếu" : "Không tham gia");
          vc.setCellStyle(voted ? greenStyle : redStyle);
          if (voted) roundsVoted++;
        }
        row.createCell(col++).setCellValue(roundsVoted + "/" + rounds.size());
        Cell totalCell = row.createCell(col);
        if (roundsVoted == rounds.size()) { totalCell.setCellValue("Tham gia đầy đủ"); totalCell.setCellStyle(greenStyle); }
        else if (roundsVoted > 0) { totalCell.setCellValue("Tham gia một phần"); totalCell.setCellStyle(centeredStyle); }
        else { totalCell.setCellValue("Không tham gia"); totalCell.setCellStyle(redStyle); }
      }
      autosize(s2, col + 1);

      // ════════════════════════════════════════════════════════════════════
      // Sheet 5+ — Kết quả từng vòng
      // ════════════════════════════════════════════════════════════════════
      for (ElectionRound round : rounds) {
        boolean isFinalSheet = finalRound != null && round.getId().equals(finalRound.getId());
        String sheetName = safeSheetName("KQ " + (round.getTitle() != null ? round.getTitle() : "Vong " + round.getRoundNumber()));
        XSSFSheet sr = wb.createSheet(sheetName);
        Row ri = sr.createRow(0);
        Cell riCell = ri.createCell(0);
        riCell.setCellValue((round.getTitle() != null ? round.getTitle() : "Vòng " + round.getRoundNumber())
            + " — " + statusLabel(round.getStatus())
            + " | " + fmt(round.getStartTime()) + " → " + fmt(round.getEndTime())
            + " | Số vào vòng tiếp: " + (round.getMaxAdvanceCount() > 0 ? round.getMaxAdvanceCount() : "—"));
        riCell.setCellStyle(titleStyle);
        sr.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
        Row hr = sr.createRow(1);
        styledCell(hr, 0, "Hạng", hStyle);
        styledCell(hr, 1, "Tên ứng viên", hStyle);
        styledCell(hr, 2, "Tổ chức / đơn vị", hStyle);
        styledCell(hr, 3, "Số phiếu", hStyle);
        styledCell(hr, 4, "Tỷ lệ (%)", hStyle);
        styledCell(hr, 5, "Kết quả", hStyle);

        List<Map<String, Object>> stats = voteRepository.countVotesByCandidate(electionId, round.getId());
        Map<Long, Long> vcMap = stats.stream().collect(Collectors.toMap(
            s -> ((Number) s.get("candidateId")).longValue(),
            s -> ((Number) s.get("voteCount")).longValue(),
            (a, b) -> a));
        List<Candidate> roundCandidates = candidates.stream()
            .sorted((a, b) -> Long.compare(vcMap.getOrDefault(b.getId(), 0L), vcMap.getOrDefault(a.getId(), 0L)))
            .collect(Collectors.toList());
        long totalVotes = vcMap.values().stream().mapToLong(Long::longValue).sum();
        int advance = round.getMaxAdvanceCount();

        for (int i = 0; i < roundCandidates.size(); i++) {
          Candidate c = roundCandidates.get(i);
          long votes = vcMap.getOrDefault(c.getId(), 0L);
          double pct = totalVotes > 0 ? (votes * 100.0 / totalVotes) : 0;
          boolean qualified = advance > 0 && i < advance;
          Row cr = sr.createRow(i + 2);
          cr.createCell(0).setCellValue(i + 1);
          cr.createCell(1).setCellValue(c.getName() != null ? c.getName() : "");
          cr.createCell(2).setCellValue(c.getParty() != null ? c.getParty() : "");
          cr.createCell(3).setCellValue(votes);
          cr.createCell(4).setCellValue(Math.round(pct * 10.0) / 10.0);
          Cell resultCell = cr.createCell(5);
          if (advance > 0) {
            String label = qualified ? (isFinalSheet ? "🏆 Trúng cử" : (i == 0 ? "🏆 Dẫn đầu" : "✅ Vào vòng tiếp")) : "❌ Bị loại";
            resultCell.setCellValue(label);
            resultCell.setCellStyle(qualified ? (i == 0 ? goldStyle : greenStyle) : redStyle);
          } else {
            resultCell.setCellValue("—");
          }
        }
        Row sumRow = sr.createRow(roundCandidates.size() + 3);
        sumRow.createCell(0).setCellValue("Tổng phiếu");
        sumRow.createCell(3).setCellValue(totalVotes);
        autosize(sr, 6);
      }

      // ════════════════════════════════════════════════════════════════════
      // Sheet — BLIND_TOKEN_REPORT
      // ════════════════════════════════════════════════════════════════════
      XSSFSheet sBt = wb.createSheet("BLIND_TOKEN_REPORT");
      r = 0;
      Cell btTitle = sBt.createRow(r++).createCell(0);
      btTitle.setCellValue("BÁO CÁO TOKEN CHỮ KÝ MÙ (BLIND SIGNATURE TOKENS)");
      btTitle.setCellStyle(titleStyle);
      sBt.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
      r++;

      // Explanation
      Cell btNote = sBt.createRow(r++).createCell(0);
      btNote.setCellValue("Mỗi cử tri chỉ được cấp 1 token ký mù cho mỗi vòng bầu cử. Token đã cấp nghĩa là cử tri đã bỏ phiếu vòng đó.");
      btNote.setCellStyle(wrapStyle);
      sBt.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 5));
      r++;

      // Summary per round
      Cell btSumH = sBt.createRow(r++).createCell(0);
      btSumH.setCellValue("THỐNG KÊ THEO VÒNG");
      btSumH.setCellStyle(sectionStyle);
      Row btSumHdr = sBt.createRow(r++);
      styledCell(btSumHdr, 0, "Vòng", hStyle);
      styledCell(btSumHdr, 1, "Tổng token đã cấp", hStyle);
      styledCell(btSumHdr, 2, "Ghi chú", hStyle);
      for (ElectionRound rd : rounds) {
        long rdTokens = blindSignatureLogRepository.countDistinctVotedByElectionIdAndRoundId(electionId, rd.getId());
        Row btRow = sBt.createRow(r++);
        btRow.createCell(0).setCellValue(rd.getTitle() != null ? rd.getTitle() : "Vòng " + rd.getRoundNumber());
        btRow.createCell(1).setCellValue(rdTokens);
        btRow.createCell(2).setCellValue("Mỗi token chỉ được sử dụng 1 lần duy nhất");
      }
      r++;

      // Detail log
      Cell btDetailH = sBt.createRow(r++).createCell(0);
      btDetailH.setCellValue("CHI TIẾT NHẬT KÝ TOKEN");
      btDetailH.setCellStyle(sectionStyle);
      Row btHdr = sBt.createRow(r++);
      styledCell(btHdr, 0, "Token ID", hStyle);
      styledCell(btHdr, 1, "Mã cử tri (ẩn danh)", hStyle);
      styledCell(btHdr, 2, "Vòng bầu", hStyle);
      styledCell(btHdr, 3, "Thời gian cấp token", hStyle);
      styledCell(btHdr, 4, "Trạng thái", hStyle);
      styledCell(btHdr, 5, "Ghi chú bảo mật", hStyle);

      List<BlindSignatureLog> tokens = blindSignatureLogRepository.findByElectionIdOrderByRoundIdAscCreatedAtAsc(electionId);
      // Map roundId -> title
      Map<Long, String> roundTitles = rounds.stream().collect(Collectors.toMap(
          ElectionRound::getId,
          rd -> rd.getTitle() != null ? rd.getTitle() : "Vòng " + rd.getRoundNumber(),
          (a, b) -> a));

      for (int ti = 0; ti < tokens.size(); ti++) {
        BlindSignatureLog token = tokens.get(ti);
        Row btRow = sBt.createRow(r++);
        btRow.createCell(0).setCellValue(String.format("BT-%05d", token.getId()));
        // Anonymize userId: show only last 3 digits
        String anonId = token.getUserId() != null ? "CTV-" + String.format("%04d", token.getUserId() % 10000) : "—";
        btRow.createCell(1).setCellValue(anonId);
        btRow.createCell(2).setCellValue(roundTitles.getOrDefault(token.getRoundId(), "Vòng ?"));
        btRow.createCell(3).setCellValue(fmt(token.getCreatedAt()));
        Cell statusCell = btRow.createCell(4);
        statusCell.setCellValue("Đã sử dụng");
        statusCell.setCellStyle(greenStyle);
        btRow.createCell(5).setCellValue("Token 1 lần — không thể bỏ phiếu lại");
      }
      autosize(sBt, 6);

      // ════════════════════════════════════════════════════════════════════
      // Sheet — RECEIPT_VERIFICATION
      // ════════════════════════════════════════════════════════════════════
      XSSFSheet sRv = wb.createSheet("RECEIPT_VERIFICATION");
      r = 0;
      Cell rvTitle = sRv.createRow(r++).createCell(0);
      rvTitle.setCellValue("XÁC MINH PHIẾU BẦU (RECEIPT VERIFICATION)");
      rvTitle.setCellStyle(titleStyle);
      sRv.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
      r++;

      Cell rvNote = sRv.createRow(r++).createCell(0);
      rvNote.setCellValue("Mỗi phiếu bầu được ký bằng RSA Blind Signature và mã hóa RSA-OAEP. Signature có thể được xác minh độc lập.");
      rvNote.setCellStyle(wrapStyle);
      sRv.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 5));
      r++;

      Row rvHdr = sRv.createRow(r++);
      styledCell(rvHdr, 0, "Receipt ID", hStyle);
      styledCell(rvHdr, 1, "Vòng bầu", hStyle);
      styledCell(rvHdr, 2, "Message Token (64 ký tự đầu)", hStyle);
      styledCell(rvHdr, 3, "Chữ ký RSA (64 ký tự đầu)", hStyle);
      styledCell(rvHdr, 4, "Trạng thái", hStyle);

      List<Vote> allVotesForElection = voteRepository.findByElectionId(electionId);

      for (int vi = 0; vi < allVotesForElection.size(); vi++) {
        Vote vote = allVotesForElection.get(vi);
        Row rvRow = sRv.createRow(r++);
        rvRow.createCell(0).setCellValue(String.format("RCP-%06d", vote.getId()));
        rvRow.createCell(1).setCellValue(roundTitles.getOrDefault(vote.getRoundId(), "Vòng ?"));

        String token = vote.getMessageToken();
        rvRow.createCell(2).setCellValue(token != null && token.length() > 64 ? token.substring(0, 64) + "…" : (token != null ? token : "—"));

        String sig = vote.getSignature();
        rvRow.createCell(3).setCellValue(sig != null && sig.length() > 64 ? sig.substring(0, 64) + "…" : (sig != null ? sig : "—"));

        Cell rvStatus = rvRow.createCell(4);
        rvStatus.setCellValue("Hợp lệ");
        rvStatus.setCellStyle(greenStyle);
      }
      autosize(sRv, 5);
      sRv.setColumnWidth(2, 8000);
      sRv.setColumnWidth(3, 8000);

      wb.write(out);
      return out.toByteArray();
    }
  }

  // ── Style helpers ──────────────────────────────────────────────────────────

  private CellStyle createHeaderStyle(XSSFWorkbook wb) {
    CellStyle s = wb.createCellStyle();
    Font f = wb.createFont();
    f.setBold(true);
    f.setColor(IndexedColors.WHITE.getIndex());
    s.setFont(f);
    s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
    s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    s.setBorderBottom(BorderStyle.THIN);
    s.setAlignment(HorizontalAlignment.CENTER);
    return s;
  }

  private CellStyle createTitleStyle(XSSFWorkbook wb, short size) {
    CellStyle s = wb.createCellStyle();
    Font f = wb.createFont();
    f.setBold(true);
    f.setFontHeightInPoints(size);
    s.setFont(f);
    return s;
  }

  private CellStyle createColorStyle(XSSFWorkbook wb, IndexedColors color, boolean bold) {
    CellStyle s = wb.createCellStyle();
    Font f = wb.createFont();
    f.setColor(color.getIndex());
    f.setBold(bold);
    s.setFont(f);
    s.setAlignment(HorizontalAlignment.CENTER);
    return s;
  }

  // ── Data helpers ──────────────────────────────────────────────────────────

  private Election getElection(Long electionId) {
    return electionRepository.findById(electionId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử."));
  }

  private List<ElectionRound> getRounds(Long electionId) {
    List<ElectionRound> rounds = roundRepository.findByElectionId(electionId);
    rounds.sort(java.util.Comparator.comparing(ElectionRound::getRoundNumber));
    return rounds;
  }

  private int pair(Sheet sheet, int rowIndex, String label, Object value) {
    Row row = sheet.createRow(rowIndex);
    row.createCell(0).setCellValue(label);
    row.createCell(1).setCellValue(value == null ? "" : String.valueOf(value));
    return rowIndex + 1;
  }

  private void styledCell(Row row, int col, String value, CellStyle style) {
    Cell cell = row.createCell(col);
    cell.setCellValue(value);
    cell.setCellStyle(style);
  }

  private void autosize(Sheet sheet, int columns) {
    for (int i = 0; i < columns; i++) sheet.autoSizeColumn(i);
  }

  private String safeSheetName(String name) {
    String safe = name.replaceAll("[\\\\/?*\\[\\]:]", "_");
    return safe.length() > 31 ? safe.substring(0, 31) : safe;
  }

  private String fmt(java.time.LocalDateTime dt) {
    return dt == null ? "—" : dt.format(FMT);
  }

  private String statusLabel(String status) {
    if (status == null) return "";
    return switch (status.toUpperCase()) {
      case "OPEN" -> "Đang diễn ra";
      case "CLOSED" -> "Đã kết thúc";
      case "UPCOMING" -> "Sắp diễn ra";
      default -> status;
    };
  }

  // ── PDF (giữ nguyên) ─────────────────────────────────────────────────────

  public byte[] exportPdf(Long electionId) {
    Election election = getElection(electionId);
    List<String> lines = new ArrayList<>();
    lines.add("BÁO CÁO KẾT QUẢ BẦU CỬ");
    lines.add("Tên: " + election.getTitle());
    lines.add("Trạng thái: " + statusLabel(election.getStatus()));
    lines.add("");
    for (ElectionRound round : getRounds(electionId)) {
      lines.add((round.getTitle() != null ? round.getTitle() : "Vòng " + round.getRoundNumber())
          + " | " + statusLabel(round.getStatus())
          + " | " + fmt(round.getStartTime()) + " - " + fmt(round.getEndTime()));
      List<Map<String, Object>> stats = voteRepository.countVotesByCandidate(electionId, round.getId());
      if (stats.isEmpty()) lines.add("  Chưa có phiếu.");
      for (int i = 0; i < stats.size(); i++) {
        Map<String, Object> stat = stats.get(i);
        lines.add("  " + (i + 1) + ". Ứng viên #" + stat.get("candidateId") + ": " + stat.get("voteCount") + " phiếu");
      }
      lines.add("");
    }
    return buildSimplePdf(lines);
  }

  private byte[] buildSimplePdf(List<String> lines) {
    StringBuilder text = new StringBuilder();
    text.append("BT\n/F1 11 Tf\n50 790 Td\n14 TL\n");
    for (String line : lines) text.append("(").append(escapePdf(line)).append(") Tj\nT*\n");
    text.append("ET");
    byte[] stream = text.toString().getBytes(StandardCharsets.UTF_8);
    List<String> objects = new ArrayList<>();
    objects.add("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n");
    objects.add("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n");
    objects.add("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n");
    objects.add("4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n");
    objects.add("5 0 obj << /Length " + stream.length + " >> stream\n" + text + "\nendstream endobj\n");
    StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
    List<Integer> offsets = new ArrayList<>();
    for (String object : objects) {
      offsets.add(pdf.toString().getBytes(StandardCharsets.UTF_8).length);
      pdf.append(object);
    }
    int xrefOffset = pdf.toString().getBytes(StandardCharsets.UTF_8).length;
    pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
    pdf.append("0000000000 65535 f \n");
    for (Integer offset : offsets) pdf.append(String.format("%010d 00000 n \n", offset));
    pdf.append("trailer << /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
    pdf.append("startxref\n").append(xrefOffset).append("\n%%EOF");
    return pdf.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String escapePdf(String value) {
    return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
  }
}
