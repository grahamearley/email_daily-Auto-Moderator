fun main(args: Array<String>) {
    if (args.size != 1) {
        println("You must supply exactly one argument: the email daily mod account's password.")
        return
    }

    val password = args.first()
    val moderator = Moderator("emaildailymod@gmail.com", password)

    val emailsToUnsubscribe = moderator.getEmailsToUnsubscribe()

    // Print out the removees to the console, just for fun:
    emailsToUnsubscribe.forEach(::println)

    moderator.unsubscribeUsers(emailsToUnsubscribe)
    moderator.emailListWithRemovals(emailsToUnsubscribe)
}