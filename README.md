# Payment timer (demo)

A tiny Android app that measures how long it takes for a payment notification to reach
the phone that receives the money. It compares the payment app's notification, the
bank SMS notification and the soundbox, using exact timestamps.

It reads notifications on the device only. It has **no internet permission**, so nothing
it sees can leave the phone. By default it keeps only payment-like notifications
(an amount, or words such as "received" and "credited") and ignores the rest.

## Build the APK (free, about 5 minutes)

1. On GitHub create a **new public repository**, for example `notif-timer`.
2. Upload everything in this folder, including the `.github` folder, to the repository
   (Add file > Upload files, drag the contents in, Commit).
3. Open the **Actions** tab and wait for "Build APK" to finish (green tick).
4. Open the **Releases** page of the repository. The file `payment-timer.apk` is attached.

If the build fails, open the failed run, copy the red error lines and send them over.

## Install on the phone that RECEIVES the money

1. Open the Releases page in Chrome on that phone, download `payment-timer.apk`, open it.
   Allow "install unknown apps" for Chrome if asked. If Play Protect warns, choose "Install anyway".
2. Open **Payment timer** and tap **Open notification access settings**. Switch on "Payment timer".
   On Android 13 and newer the switch may be greyed out ("restricted setting"). If so:
   Settings > Apps > Payment timer > three dots (top right) > **Allow restricted settings**, then try again.
   This is Android's rule for apps installed outside an app store. Some phones (for example Samsung)
   hide or change that menu, and Android 15 can be stricter. If you cannot switch it on, tell the
   author your phone model and Android version.
3. Pay Rs 1 to the shop QR **from a different phone**. At the moment you press Pay, tap **Pay tapped**
   in this app. If the soundbox speaks, tap **Soundbox spoke** the moment you hear it.
4. The top of the screen shows the delays. **Copy log** copies the details. Check it for
   private information before sending it to anyone.

## What is tested

The parsing and timing logic is unit-tested (`tests/PaymentLogicTest.kt`) and all app source
compiles against the Android 34 API. The APK itself has not been built or run by the author,
so the first GitHub build may need a fix.
