import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.search.*

class Moderator(val email: String, val password: String) {

    //// Properties, session, store, inbox
    ////  are all things needed to connect to email:
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
    private val inbox: Folder get() {
        val inbox = store.getFolder("INBOX")
        inbox.open(Folder.READ_ONLY)
        return inbox
    }

    //// Date constants for yesterday and today:
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

    init {
        store.connect("imap.gmail.com", 993, this.email, this.password)
    }

    /**
     * Emails Sympa to ask for the listserv's subscribers. Then, awkwardly, waits
     *   two minutes (to be safe) and then check's its inbox for Sympa's reply.
     *
     *   Then it assembles the list of subscribers into a `Set`.
     *
     *   (This is a convenience method, combining the helper methods that follow.)
     */
    private fun getSubscribers(): Set<String?> {
        this.requestSubscriberList()
        val timeOfRequest = Date()

        println("Sent email to Sympa requesting subscriber list at $timeOfRequest. \n")
        println("Pausing for 2 minutes...\n\n")

        Thread.sleep(120000)

        val subscriberListMessage = this.searchForRecentSubscriberEmail(timeOfRequest).last()

        println("Subscriber list email received from Sympa at ${subscriberListMessage.receivedDate}\n\n")

        return this.getSubscriberSetFromEmail(subscriberListMessage)
    }

    /**
     * Sends email to Sympa requesting subscribers (using Sympa email commands)
     */
    private fun requestSubscriberList() {
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(email))
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("sympa@lists.carleton.edu"))
        message.subject = "REVIEW email_daily_or_be_removed"
        message.setText("")

        Transport.send(message)
    }

    /**
     * Searches through inbox for emails that meet the following criteria:
     *   - they were received *after* we emailed Sympa asking for the subscriber list
     *   - they had the subject line "REVIEW email_daily_or_be_removed"
     *
     *   These two criteria will get us the response from sympa with the subscriber list!
     */
    private fun searchForRecentSubscriberEmail(timeRequestWasSent: Date): Array<Message> {
        val receivedSinceRequestWasSentTerm = ReceivedDateTerm(ComparisonTerm.GE, timeRequestWasSent)
        val subjectTerm = SubjectTerm("REVIEW email_daily_or_be_removed")
        val finalSearchTerm = AndTerm(receivedSinceRequestWasSentTerm, subjectTerm)

        return inbox.search(finalSearchTerm)
    }

    /**
     * Given the subscriber list email from Sympa, this function parses the email and
     *  returns a set of strings containing the subscribers' email addresses.
     */
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

    /**
     * Gathers the list of subscribers and the people who emailed
     *  and determines who should get removed. Returns a set of those
     *  people's email addresses.
     */
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
                .plus("emaildailymod@gmail.com") // Make sure we don't delete the mod!

        println("People who emailed between $yesterday and $today: ${peopleWhoEmailed.count()}\n")
        println(peopleWhoEmailed.sortedBy {it})
        println("\n\n")

        println("Current subscribers: ${subscribers.count()}\n")
        println(subscribers.sortedBy {it})
        println("\n\n")

        // Subscribers - People Who Emailed = People who didn't email!
        return subscribers.minus(peopleWhoEmailed)
    }

    /**
     * Email the listserv and announce who was removed. With photos!!
     */
    fun emailListWithRemovals(removals: Set<String?>) {
        val emailList = "email_daily_or_be_removed@lists.carleton.edu"
        val listAddress = InternetAddress.parse(emailList)

        val message = MimeMessage(session)
        message.setFrom(InternetAddress(email))
        message.setRecipients(Message.RecipientType.TO, listAddress)
        message.subject = "Robo Mod has an update for you..."

        if (removals.isEmpty()) {
            message.setText("No removals today! What a miracle!")
        } else {
            val builder = StringBuilder()
            builder.append("<html><head>"
                    + "<title>ALL HAIL LORD EDOBR</title>"
                    + "</head>"
                    + "<body><div><strong>Lord Edobr hath written that the following people are removed:</strong></div>")

            removals.forEach { email ->
                val username = email?.split("@")?.first()
                builder.append("<div>$email</div>")
                builder.append("<div><img src=https://apps.carleton.edu/stock/ldapimage.php?id=$username&source=campus_directory/></div>")
            }

            builder.append("<div>Let that be a lesson.</div></body></html>")
            message.setText(builder.toString(), "US-ASCII", "html")
        }


        Transport.send(message)
    }

    /**
     * Given a set of email addresses, unsubscribe them from
     *  the listserv, using Sympa email commands.
     */
    fun unsubscribeUsers(emailAddresses: Set<String?>) {
        if (emailAddresses.isNotEmpty()) {
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(email))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("sympa@lists.carleton.edu"))
            message.subject = ""
            message.setText(
                    emailAddresses.joinToString(separator = "\n", transform = {address -> "DEL email_daily_or_be_removed $address"}))

            Transport.send(message)
        }
    }
}