# Google Play

Orbiter isn't listed on Google Play yet. Google won't publish an app from a new personal developer
account until it has passed a closed test: at least **12 testers opted in for 14 days in a row**.
Until that's done, F-Droid and GitHub Releases are how people install it. This page is the one-time
setup for the closed test, and what to do after it.

## How people join the closed test

Only Google accounts on the test's tester list can install from it. The list is either a set of
email addresses typed into Play Console, or a **Google Group** (everyone in the group is a tester).
The group is what makes signing up self-serve: anyone can join it from the README or the docs,
then opt in on the test's page, without you adding them by hand.

1. Join the group (open to anyone).
2. Open `https://play.google.com/apps/testing/com.pranavajk.orbiter` while signed in with the same
   Google account, and tap **Become a tester**.
3. Install Orbiter from the Play Store link on that page.

The opt-in page only works for accounts in the group, so the order matters.

## One-time setup

### 1. Google Group

At [groups.google.com](https://groups.google.com), create a group, for example
`orbiter-testers@googlegroups.com`:

- **Who can join group:** Anyone can join.
- **Who can view conversations / post:** keep it to group members (it's a tester list, not a forum).

### 2. The app in Play Console

Once the developer account's identity verification has gone through:

1. **Create app:** name `Orbiter – for BookOrbit`, default language English (United States), App,
   Free.
2. **Store listing:** copy the text from `fastlane/metadata/android/en-US/` (`title.txt`,
   `short_description.txt`, `full_description.txt`). The icon is `images/icon.png`, the feature
   graphic is `store-assets/feature-graphic.png`, and the screenshots are
   `images/phoneScreenshots/` and `images/tenInchScreenshots/`.
3. **App content:**
   - Privacy policy: `https://pranavajk1.github.io/orbiter-for-bookorbit/privacy.html`
   - Ads: none.
   - Data safety: no data is sent to the developer. Answer as the privacy policy describes: account
     and library data go only to the user's own server, and Translate sends selected text to Google
     or Microsoft when the user taps it.
   - Content rating and target audience questionnaires.
4. **Closed testing:** use the default closed track ("Closed testing - Alpha", which is what CI
   uploads to). Under **Testers**, choose Google Groups and add the group's address. Set the
   feedback channel to `https://github.com/pranavajk1/orbiter-for-bookorbit/issues`.

### 3. App signing: use the release key

**Do this before the first upload; it can't be undone.** Play signs the APKs it installs. By
default it signs them with a key Google generates, which would mean an Orbiter installed from Play
can't update from F-Droid or GitHub Releases (or the other way around), because Android refuses an
update signed with a different key.

When Play Console asks how the app should be signed, choose to **use your own app signing key**
and export the release key with the PEPK tool it offers:

```bash
java -jar pepk.jar --keystore=$HOME/.android/orbiter-release/orbiter-release.jks --alias=orbiter \
  --output=orbiter-signing-key.zip --include-cert --rsa-aes-encryption \
  --encryption-key-path=encryption_public_key.pem
```

`pepk.jar` and `encryption_public_key.pem` come from that Play Console page, and the keystore
password is in 1Password ("Orbiter release key"). Upload the zip, then delete it. The same key also
works as the upload key, and CI already signs the bundle with it.

### 4. First release, by hand

Play's API can only add draft releases to an app that has never been reviewed, so the first one goes
up through the Console:

1. In closed testing, **Create new release**, and upload `orbiter-<version>.aab` from the matching
   [GitHub Release](https://github.com/pranavajk1/orbiter-for-bookorbit/releases).
2. Release notes: `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Send it for review. Once it's approved, the opt-in page above starts working.

### 5. Uploads from CI

So that each later release reaches the closed test on its own:

1. In Google Cloud, create a service account and a JSON key for it.
2. In Play Console → **Users and permissions**, invite the service account's email with access to
   Orbiter and the **Release to testing tracks** permission.
3. Save the JSON as the repo secret `PLAY_SERVICE_ACCOUNT_JSON`.

From then on, `release.yml` uploads each release's bundle to closed testing. Without the secret it
skips only that step.

## After setup

- The README's and the docs' install sections link the group (`orbiter-testers`) and the opt-in page.
- After 14 days with 12 or more testers opted in the whole time, apply for production access in
  Play Console. Google says the review usually takes up to a week. Testers who leave the group stop
  counting, so keep an eye on the number near the end.
- Once production access is granted, promote a closed-testing release to production in Play Console.
  Open testing (a public "early access" listing) also becomes available then.
