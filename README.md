# Auto Moderator for `email_daily_or_be_removed`
The `Moderator.kt` file contains the code to run the automoderator. The automoderator will look through its own gmail account (EmailyDailyMod@gmail.com) to determine who has emailed the list in the previous day. It will also email Sympa to request a current list of subscribers to the list. Then it will use these two lists to determine who *hasn't* emailed in the previous day.

Once it knows who hasn't emailed, it sends an email command to Sympa to delete these people from the list. Then it sends an email to the listserv to announce the deletions (with pictures).

### Installation
The easiest way to run this script is to download IntelliJ and to load this project in IntelliJ.

Then run the `main(...)` function in `Moderator.kt` with a single command-line argument: the password for the email daily mod gmail account.

### Heroku
This code is compiled into a .jar file and run each day via Heroku Scheduler. (That's what the `procfile` is for, as well as some of the code in the `build.gradle` file).
