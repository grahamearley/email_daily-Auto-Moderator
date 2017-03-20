import java.io.File
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.search.*

class Moderator(val email: String, val password: String) {

    // We'll use this to print thing we want to send to the mods as an email
    val emailStringBuilder: StringBuilder = StringBuilder()

    val properties: Properties get() {
        val props = Properties()
        props.put("mail.smtp.host", "smtp.gmail.com")
        props.put("mail.smtp.socketFactory.port", "465")
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
        props.put("mail.smtp.auth", "true")
        props.put("mail.smtp.port", "465")
        return props
    }
    val session: Session = Session.getDefaultInstance(properties, object: Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(email, password)
        }
    })
    val store: Store = session.getStore("imaps")
    private val yesterday: Date get() {
        val yesterdayCal = Calendar.getInstance()
        yesterdayCal.timeZone = TimeZone.getTimeZone("America/Chicago")

        // Subtract a day to get to yesterday!
        yesterdayCal.add(Calendar.DATE, -1)

        // Set time to be start of yesterday:
        yesterdayCal.set(Calendar.HOUR_OF_DAY, 0)
        yesterdayCal.set(Calendar.MINUTE, 0)
        yesterdayCal.set(Calendar.SECOND, 0)
        return yesterdayCal.time
    }
    private val today: Date get() {
        val todayCal = Calendar.getInstance()
        todayCal.timeZone = TimeZone.getTimeZone("America/Chicago")

        // Set time to be end of yesterday:
        todayCal.set(Calendar.HOUR_OF_DAY, 0)
        todayCal.set(Calendar.MINUTE, 0)
        todayCal.set(Calendar.SECOND, 0)
        return todayCal.time
    }
    private val inbox: Folder get() {
        val inbox = store.getFolder("INBOX")
        inbox.open(Folder.READ_ONLY)
        return inbox
    }

    init {
        store.connect("imap.gmail.com", 993, this.email, this.password)
    }

    private fun getSubscribers(): Set<String?> {
        this.requestSubscriberList()
        val timeOfRequest = Date()

        emailStringBuilder.append("Sent email to Sympa requesting subscriber list at $timeOfRequest. \n")
        emailStringBuilder.append("Pausing for 2 minutes...\n\n")

        Thread.sleep(120000)

        val subscriberListMessage = this.searchForRecentSubscriberEmail(timeOfRequest).last()

        emailStringBuilder.append("Subscriber list email received from Sympa at ${subscriberListMessage.receivedDate}\n\n")

        return this.getSubscriberSetFromEmail(subscriberListMessage)
    }

    private fun requestSubscriberList() {
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(email))
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("sympa@lists.carleton.edu"))
        message.subject = "REVIEW email_daily_or_be_removed"
        message.setText("")

        Transport.send(message)
    }

    private fun searchForRecentSubscriberEmail(timeRequestWasSent: Date): Array<Message> {
        val receivedSinceRequestWasSentTerm = ReceivedDateTerm(ComparisonTerm.GE, timeRequestWasSent)
        val subjectTerm = SubjectTerm("REVIEW email_daily_or_be_removed")
        val finalSearchTerm = AndTerm(receivedSinceRequestWasSentTerm, subjectTerm)

        return inbox.search(finalSearchTerm)
    }

    private fun getSubscriberSetFromEmail(subscriberListMessage: Message): Set<String?> {
        val subscriberListText = subscriberListMessage.content as String

        val emailRegex = Regex("([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9_\\-\\.]+)\\.([a-zA-Z]{2,5})")
        val subscribers = subscriberListText.lines().map { emailRegex.find(it)?.value }.toMutableSet()

        // The regex from above catches the list name. Don't need this in the subscriber list:
        subscribers.remove("email_daily_or_be_removed@lists.carleton.edu")

        // Also remove NULL from when the regex doesn't match anything in a line
        subscribers.remove(null)

        return subscribers
    }

    fun getEmailsToUnsubscribe(): Set<String?> {
        val subscribers = this.getSubscribers()

        val toListTerm = RecipientTerm(Message.RecipientType.TO, InternetAddress("email_daily_or_be_removed@lists.carleton.edu"))

        // Chain together the Date terms so the search is for after yesterday but before today!
        //  (Note: due to IMAP protocol, I guess, only the DAY part of today/yesterday values are taken into account. Not time!)
        val beforeBeginningOfTodayTerm = SentDateTerm(ComparisonTerm.LT, today)
        val afterBeginningOfYesterdayTerm = SentDateTerm(ComparisonTerm.GE, yesterday)
        val sentYesterdayTerm = AndTerm(beforeBeginningOfTodayTerm, afterBeginningOfYesterdayTerm)

        // Now chain together the yesterday term with the term that specifies that the email was sent to the list!
        val toListSentYesterdayTerm = AndTerm(toListTerm, sentYesterdayTerm)

        val messages = inbox.search(toListSentYesterdayTerm)

        // The email result list formats senders like this: "Name Name <email@email.edu>"
        //  Extract the email address using regex, and create a set of people who emailed the list.
        val peopleWhoEmailed = messages.map { Regex("<.*>").find(it.from[0].toString())?.value?.removeSurrounding("<", ">") }
                .toSet()
                .plus("emaildailymod@gmail.com") // Don't forget the automod! Don't delete the mod!


        emailStringBuilder.append("People who emailed between $yesterday and $today: ${peopleWhoEmailed.count()}\n")
        emailStringBuilder.append(peopleWhoEmailed.sortedBy {it})
        emailStringBuilder.append("\n\n")

        emailStringBuilder.append("Current subscribers: ${subscribers.count()}\n")
        emailStringBuilder.append(subscribers.sortedBy {it})
        emailStringBuilder.append("\n\n")

        // Subscribers - People Who Emailed = People who didn't email!
        return subscribers.minus(peopleWhoEmailed)
    }

    fun sendEmailToMods(emailBody: String) {

        // TODO: Extract this to a file?
        val moderators = "earleyg@carleton.edu, garfinklei@carleton.edu"

        val modEmails = InternetAddress.parse(moderators)

        val message = MimeMessage(session)
        message.setFrom(InternetAddress(email))
        message.setRecipients(Message.RecipientType.TO, modEmails)
        message.subject = "Auto Moderator Removals Update"
        message.setText(emailBody)

        Transport.send(message)
    }
}

fun main(args: Array<String>) {
    val mod = Moderator(EMAIL, PASSWORD)
    // TODO: search for subscriber email until it arrives, instead of hardcoding 2 minutes

    val emailsToUnsubscribe = mod.getEmailsToUnsubscribe()
    emailsToUnsubscribe.forEach(::println)

    // Email this list to the mods!
    mod.sendEmailToMods("Time to make some cuts. \n\n" +
            mod.emailStringBuilder.toString() +
            "\nPeople to unsubscribe: ${emailsToUnsubscribe.size} \n" +
            emailsToUnsubscribe)
}