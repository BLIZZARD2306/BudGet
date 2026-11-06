<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_logo.png" alt="Budget Goblin" width="128">
</p>

<h1 align="center">BudGet</h1>

<p align="center">
  A friendly Android budget tracker, guarded by the Budget Goblin.<br>
  <a href="https://budget-goblin.vercel.app"><b>Download the latest version</b></a>
</p>

BudGet helps you see where your money goes without spreadsheets. Log what you spend and earn in a few taps, keep each card's balance straight, and let the goblin react as you go: proud when you save, nervous when a budget runs low, sad when you go over.

## Features

### Home
- **Remaining budget** for the current pay period, with how much is spent and when it resets.
- **Add money** for a one-time boost to this period's budget (a gift, a side job). The next period starts fresh.
- **This week** chart of daily spending, with today and yesterday side by side.
- **Income** summary: money in this period, how much went into the budget, and any spending beyond income.
- **Recent** transactions. Deleting one asks first.
- **Streak, budget left and trophies** in the top bar, and a goblin tip that changes with how you're doing.

### Add a transaction
- **Expense or income**, with 7 categories: Food, Transport, Bills, Fun, Other, Shopping and Coffee.
- **Quick amounts** (5, 10, 20, 50, 100) or type your own.
- **Paid with / Received in**: pick Cash or one of your cards.
- **Split with friends**: enter the total and the number of people, and only your share is logged.
- **Add to my current budget** (income only): raises Remaining until the next payday.
- **A goblin reaction** to every entry: a big purchase, a category going over, a savings goal reached, or a challenge broken.

### Wallet
- **Your cards in a wallet stack**: Cash, e-wallets, debit and credit cards, each with its own colour.
- **Balances that add up**: starting balance + money in − money out, and a total across all cards.
- **Tap a card to see its history**, with money in, money out and a running balance after every entry.
- **Edit or remove a card.** Removing one moves its history to Cash, so the totals stay right.
- **Category budgets**: a progress bar for each category's monthly limit.

### Stats
- **Spending by category** as a donut chart, with percentages and amounts.
- **Income vs. spent** for the month.
- **Savings goal** of 20% of income, with progress.
- **Goblin's report**: which category takes the biggest bite.

### Challenges
- **Four challenges**: No shopping (7 days), No coffee runs (5 days), No fun spending (3 days) and a No-spend weekend (2 days).
- **A live countdown** while one runs. Logging a matching expense breaks it.
- **Trophies** for the challenges you finish.

### Account and settings
- **Passwordless sign-in** with Google or an email link. Opened the link on a computer? Type the 6-digit code BudGet shows and the phone signs in.
- **Sync across your phones** through Firebase. A dot on the avatar shows whether this phone is synced.
- **Pay schedule**: monthly, twice a month, every 2 weeks or weekly, with your payday. The budget and Remaining follow it.
- **Appearance**: light, dark, or follow the phone.
- **Currency**: peso, dollar, euro or pound.
- **Sign out** (asks first, and warns if changes haven't synced yet) and **delete account** (removes everything from the cloud).

### Updates
The app checks for a new version when it opens and offers to download and install it, without the Play Store. The first time, Android asks you to allow BudGet to install apps.

## Download

Get the newest version from **[budget-goblin.vercel.app](https://budget-goblin.vercel.app)** and open the file on your phone. It runs on Android 7.0 or newer. Google Play Protect may ask to scan the app the first time, because it doesn't come from the Play Store.

## Built with

- Kotlin 2.4 and Jetpack Compose with Material 3, using the Nunito font and pixel-art icons and mascot poses
- Firebase Authentication (Google via Credential Manager, and email links)
- Cloud Firestore for sync, and Realtime Database for the computer-to-phone sign-in handoff
- Firebase App Check: Play Integrity in release builds, debug tokens in debug builds
- Firebase Hosting for the sign-in link page, and Vercel for the download page
- Android Gradle Plugin 9.4, Gradle 9.6, compile SDK 37, minimum SDK 24

## Project layout

| Path | What's there |
| --- | --- |
| `app/src/main/java/com/budgetgoblin/app/data/` | Budget data, pay schedules, cards, challenges, sign-in, cloud sync and the updater |
| `app/src/main/java/com/budgetgoblin/app/ui/` | Every screen, the goblin mascot and its animations, and shared components |
| `app/src/main/res/` | Pixel-art icons, mascot sprites and the Nunito font |
| `firestore.rules`, `database.rules.json` | Security rules: each person can only read and write their own data |
| `hosting/public/` | The page that opens email sign-in links (Firebase Hosting) |
| `email-templates/` | The sign-in email design |
| `release.ps1` | Builds and publishes a new version |

The download page has its own repository, [BudGet-Goblin-web](https://github.com/BLIZZARD2306/BudGet-Goblin-web), so changes here never break it.

## Building it yourself

1. Open the project in Android Studio.
2. Add your own `app/google-services.json` from the Firebase console. It is not in this repository. Without it the app still runs, but sign-in and sync are off.
3. In Firebase, add your signing key's SHA-1 and SHA-256 to the Android app, and turn on Google and Email link sign-in.
4. On first run, a debug build prints an App Check debug token in Logcat. Add it in Firebase > App Check, or the phone can't sync.
5. Press Run.

## Releasing a new version

Version names follow **BIG.MAJOR.MINOR**:
- **BIG** for very big updates with lots of additions and changes
- **MAJOR** for smaller feature updates
- **MINOR** for fixes and small tweaks

To release:
1. Raise `versionCode` by 1 and set `versionName` in `app/build.gradle.kts`.
2. Keep [BudGet-Goblin-web](https://github.com/BLIZZARD2306/BudGet-Goblin-web) cloned next to this folder, and have the GitHub CLI signed in.
3. Run:

```powershell
.\release.ps1 -Notes "What's new in this version"
```

The script builds the app and attaches it to a GitHub Release of the web repository. It then updates that repository's `app/latest.json`, and Vercel refreshes the download page. Installed apps see the update the next time they open.

Release builds are signed with this computer's debug key, the same one Android Studio uses, so they install over existing copies. Back up `%USERPROFILE%\.android\debug.keystore`: without it, updates can't install over the current app.
