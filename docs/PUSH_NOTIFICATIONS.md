# Push messages from the server

HYDRA-UMC-SERVER can tell this phone, even with the app closed, when a job finishes, when a global stop is issued and when the
heartbeat of an operator is lost. The messages travel through Firebase Cloud Messaging.

**They only inform.** A message carries a title and a sentence. It has no button and no action: nothing can be stopped, moved
or confirmed from it, and tapping it only opens the app.

## The Firebase configuration of the build

The configuration file is not kept in the repository. The build takes it from:

1. the environment variable `GOOGLE_SERVICES_JSON` (in GitHub Actions, the repository secret of the same name), or
2. `app/google-services.json`, which Git ignores.

It must be the `google-services.json` of an Android app registered in the Firebase project with the package name
`com.hydraumc.control`. The build log says `PUSH_CONFIG=ON` or `PUSH_CONFIG=OFF`, and never prints anything of the file. The
values the app needs are compiled into the APK, as in any Android app that uses Firebase.

Without it (a clone of the repository, a pull request from a fork) the app builds and works the same: the switch is greyed out
and says that this build has no Firebase configuration.

## Switching them on

1. The server: push switched on and the key of the Firebase service account in place (see `docs/PUSH_NOTIFICATIONS.md` of
   HYDRA-UMC-SERVER).
2. The app: Settings, Notifications, **Push messages from the server** (off by default), signed in to the server.

The phone is registered with the account of the signed-in session. Switching the messages off or signing out takes the token
back. With **Listen only** on, the phone is not registered.

With several servers kept, each one is registered with its own session when it is used, and signing out of one takes back only
its registration. The token of the phone is the same for all of them: switching the messages off drops it, and so does leaving a
server that cannot be told (or forgetting one), after which the others register again the next time they are used.

## What is tested, and what is not

The registration, its removal and the answers of the server are covered by unit tests against a simulated server. No message has
been received on a real phone.
