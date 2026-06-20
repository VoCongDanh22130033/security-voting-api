package com.nlu.notificationservice.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.nlu.notificationservice.dto.ElectionInviteEmailRequest;
import com.nlu.notificationservice.dto.RealtimeNotification;
import com.nlu.notificationservice.dto.RoundClosedEmailRequest;
import com.nlu.notificationservice.websocket.NativeNotificationWebSocketHandler;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

  private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

  private final SimpMessagingTemplate messagingTemplate;
  private final NativeNotificationWebSocketHandler nativeHandler;
  private final JavaMailSender mailSender;

  public NotificationService(
      SimpMessagingTemplate messagingTemplate,
      NativeNotificationWebSocketHandler nativeHandler,
      JavaMailSender mailSender) {
    this.messagingTemplate = messagingTemplate;
    this.nativeHandler = nativeHandler;
    this.mailSender = mailSender;
  }

  public void publish(RealtimeNotification notification) {
    messagingTemplate.convertAndSend("/topic/elections", notification);
    if (notification.getElectionId() != null) {
      messagingTemplate.convertAndSend("/topic/elections/" + notification.getElectionId(), notification);
    }
    nativeHandler.broadcast(notification);
  }

  public void sendElectionInvite(ElectionInviteEmailRequest request) throws Exception {
    try {
      byte[] qrBytes = createQrPng(request.getInviteLink());
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setTo(request.getTo());
      helper.setSubject("Mời tham gia " +" "+ request.getRoundTitle() + ": " + request.getElectionTitle());
      helper.setText("""
          <div style="font-family:Arial,sans-serif;line-height:1.6">
            <h2>Mời tham gia %s</h2>
            <p>Xin chào <b>%s</b>,</p>
            <p>Bạn được mời tham gia cuộc bầu cử: <b>%s</b>.</p>
            <p>Vòng bầu cử: <b>%s</b></p>
            <p>Vui lòng quét mã QA hoặc nhấn vào đường dẫn sau , sau đó nhập đúng mã CCCD để vào trang bầu cử.</p>
            <p><a href="%s">%s</a></p>
            <img src="cid:inviteQr" alt="QR tham gia bầu cử" width="180" height="180"/>
          </div>
          """.formatted(
              request.getRoundTitle(),
              request.getFullName(),
              request.getElectionTitle(),
              request.getRoundTitle(),
              request.getInviteLink(),
              request.getInviteLink()), true);
      helper.addInline("inviteQr", new ByteArrayResource(qrBytes), "image/png");
      mailSender.send(message);
    } catch (Exception e) {
      logger.error("Failed to send election invite email to {}", request.getTo(), e);
      throw e;
    }
  }

  public void sendRoundClosed(RoundClosedEmailRequest request) throws Exception {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    helper.setTo(request.getTo());
    helper.setSubject(request.getRoundTitle() + " đã kết thúc: " + request.getElectionTitle());
    helper.setText("""
        <div style="font-family:Arial,sans-serif;line-height:1.6">
          <h2>%s đã kết thúc</h2>
          <p>Xin chào <b>%s</b>,</p>
          <p>%s của cuộc bầu cử <b>%s</b> đã kết thúc.</p>
          <p>Hệ thống đang cập nhật kết quả và sẽ gửi lời mời cho vòng tiếp theo nếu bạn nằm trong danh sách được tham gia.</p>
          <p>Bạn có thể xem kết quả tại: <a href="%s">%s</a></p>
        </div>
        """.formatted(
            request.getRoundTitle(),
            request.getFullName(),
            request.getRoundTitle(),
            request.getElectionTitle(),
            request.getResultLink(),
            request.getResultLink()), true);
    mailSender.send(message);
  }

  private byte[] createQrPng(String text) throws Exception {
    QRCodeWriter qrCodeWriter = new QRCodeWriter();
    BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 260, 260);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
    return outputStream.toByteArray();
  }
}
