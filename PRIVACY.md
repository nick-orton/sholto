# Privacy

Sholto is a boxing timer and punch caller that works entirely offline.

- **No internet access.** The app doesn't request the internet permission, so
  it can't send or receive anything over the network.
- **No data collected.** No accounts, analytics, ads, crash reporting or
  tracking of any kind.
- **What's stored, and where.** Your timer, punch-caller and combined-mode
  settings and your presets are saved on your phone. If Android backup is on
  for your device, they are included in your own backup, like other apps'
  settings. Uninstalling the app deletes them.
- **Permissions.**
  - *Notifications:* the ongoing notification that shows the round and time
    while a session runs, with Pause and Stop.
  - *Foreground service and wake lock:* keep the timer and callouts running on
    time with the screen off.
  - *Vibration:* buzz on phase changes, if you leave it on.
- **Phone calls.** The app checks the audio mode to pause a session when a
  call starts. It doesn't read call details or phone state.
- **Speech.** Calls are spoken by your phone's text-to-speech engine or by
  recorded clips bundled in the app. Sholto passes the engine only the punch
  being called, for example "three body".

Questions: open an issue at https://github.com/nick-orton/sholto/issues.
