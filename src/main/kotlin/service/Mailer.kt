package service

import org.springframework.stereotype.Service
import java.io.File
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import kotlin.collections.set

@Service
class Mailer(val mailFrom: String, val mailPassword: String) {

    private val props = Properties()
    private val session = Session.getDefaultInstance(props, object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(mailFrom, mailPassword)
        }
    })

    init {
        session.debug = false
        putIfMissing(props, "mail.smtp.host", "smtp.gmail.com")
        putIfMissing(props, "mail.smtp.port", "587")
        putIfMissing(props, "mail.smtp.auth", "true")
        putIfMissing(props, "mail.smtp.starttls.enable", "true")
    }

    private fun putIfMissing(props: Properties, key: String, value: String) {
        if (!props.containsKey(key)) {
            props[key] = value
        }
    }

    fun send(emailTo: String, subject: String, message: String) {

        try {

            val mimeMessage = MimeMessage(session)
            mimeMessage.setFrom(InternetAddress(mailFrom))
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailTo, false))

            mimeMessage.setText(message)
            mimeMessage.subject = subject
            mimeMessage.sentDate = Date()

            val mimeBody = MimeBodyPart()
            mimeBody.attachFile(File(""))

            val smtpTransport = session.getTransport("smtp")
            smtpTransport.connect()
            smtpTransport.sendMessage(mimeMessage, mimeMessage.allRecipients)
            smtpTransport.close()

        } catch (messagingException: MessagingException) {
            messagingException.printStackTrace()
        }

    }

    fun send(emailTo: String, subject: String, message: String, attachment: File) {

        try {

            val mimeMessage = MimeMessage(session)
            mimeMessage.setFrom(InternetAddress(mailFrom))
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailTo, false))
            mimeMessage.setText(message)
            mimeMessage.subject = subject
            mimeMessage.sentDate = Date()

            val wrap = MimeBodyPart()

            val cover = MimeMultipart("alternative")

            val textPart: BodyPart = MimeBodyPart()
            textPart.setContent(message, "text/plain; charset=utf-8")
            textPart.disposition = Part.INLINE
            cover.addBodyPart(textPart)

            val mimeBody = MimeBodyPart()
            mimeBody.attachFile(attachment)
            cover.addBodyPart(mimeBody)

            wrap.setContent(cover)

            val content = MimeMultipart("related")
            mimeMessage.setContent(content)
            content.addBodyPart(wrap)

            val smtpTransport = session.getTransport("smtp")
            smtpTransport.connect()
            smtpTransport.sendMessage(mimeMessage, mimeMessage.allRecipients)
            smtpTransport.close()

        } catch (messagingException: MessagingException) {
            messagingException.printStackTrace()
        }

    }

    fun send(mailTo: String, subject: String, message: String, htmlMessage: String) {

        try {

            val mimeMessage = MimeMessage(session)
            mimeMessage.setFrom(InternetAddress(mailFrom))
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(mailTo, false))
            mimeMessage.subject = subject
            mimeMessage.sentDate = Date()

            val wrap = MimeBodyPart()

            val cover = MimeMultipart("alternative")
            val textPart: BodyPart = MimeBodyPart()
            textPart.setContent(message, "text/plain; charset=utf-8")
            textPart.disposition = Part.INLINE
            cover.addBodyPart(textPart)

            val htmlPart: BodyPart = MimeBodyPart()
            htmlPart.setContent(htmlMessage, "text/html; charset=utf-8")
            htmlPart.disposition = Part.INLINE
            cover.addBodyPart(htmlPart)

            wrap.setContent(cover)

            val content = MimeMultipart("related")
            mimeMessage.setContent(content)
            content.addBodyPart(wrap)

            val smtpTransport = session.getTransport("smtp")
            smtpTransport.connect()
            smtpTransport.sendMessage(mimeMessage, mimeMessage.allRecipients)
            smtpTransport.close()

        } catch (messagingException: MessagingException) {
            messagingException.printStackTrace()
        }

    }

}