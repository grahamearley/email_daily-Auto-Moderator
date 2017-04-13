fun main(args: Array<String>) {
    if (args.size != 1) {
        println("You must supply exactly one argument: the email daily mod account's password.")
        return
    }

    val password = args.first()
    val moderator = Moderator("emaildailymod@gmail.com", password)
    moderator.sendDebugEmail("earleyg@carleton.edu", "0. Successfully signed in and stuff.")

    try {
        moderator.sendDebugEmail("earleyg@carleton.edu", "1")
        val emailsToUnsubscribe = moderator.getEmailsToUnsubscribe()

        // Print out the removees to the console, just for fun:
        moderator.sendDebugEmail("earleyg@carleton.edu", "2")
        emailsToUnsubscribe.forEach(::println)

        moderator.sendDebugEmail("earleyg@carleton.edu", "3")
        moderator.unsubscribeUsers(emailsToUnsubscribe)

        moderator.sendDebugEmail("earleyg@carleton.edu", "4")
        moderator.emailListWithRemovals(emailsToUnsubscribe)
    } catch (e: Exception) {
        moderator.sendDebugEmail("earleyg@carleton.edu", "Caught error! ${e.message}")
    }
}