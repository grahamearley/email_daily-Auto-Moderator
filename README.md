# Auto Moderator for `email_daily_or_be_removed`
The `Moderator.kt` file contains the code to run the automoderator. The automoderator will look through its own gmail account (EmailyDailyMod@gmail.com) to determine who has emailed the list in the previous day. It will also email Sympa to request a current list of subscribers to the list. Then it will use these two lists to determine who *hasn't* emailed in the previous day.

### Installation
The easiest way to run this script is to download IntelliJ and to load this project in IntelliJ.

Then run the `main(...)` function in `Moderator.kt`.

### Security note:
As it is now, this repo MUST remain private. It contains the password to the auto-mod gmail account.
