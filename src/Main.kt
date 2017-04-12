fun main(args: Array<String>) {
    if (args.size != 1) {
        println("You must supply exactly one argument: the email daily mod account's password.")
        return
    }

    val password = args.first()
    val moderator = Moderator(EMAIL, password)
    // TODO: search for subscriber email until it arrives, instead of hardcoding 2 minutes

    val emailsToUnsubscribe = moderator.getEmailsToUnsubscribe()

    // Print out the removees to the console, just for fun:
    emailsToUnsubscribe.forEach(::println)

    moderator.unsubscribeUsers(emailsToUnsubscribe)
    moderator.emailListWithRemovals(emailsToUnsubscribe)
}