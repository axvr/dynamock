# Contributing code

Dynamock uses [Bass][bass] [Loop][bass-loop] as it's CI "platform".  So if you
are submitting a pull request, you will need to download [Bass][bass-download]
and [Docker][docker].  Once downloaded, make sure the Bass executable is in
your `PATH`.

[bass]: https://bass-lang.org
[bass-loop]: https://loop.bass-lang.org
[bass-download]: https://github.com/vito/bass/releases
[docker]: https://www.docker.com/

Before submitting a PR or pushing any commits you will need to start the "Bass
runner", this can be done with this command:

```sh
# Replace `username` with your GitHub username.
bass --runner username@github.bass-lang.org
```

Now when you push commits, Bass will run the CI checks on your system.  (The
checks must pass before your PR will be merged.)  If you really don't want to
use Bass, leave a comment on the PR saying so and I'll run them myself.

Before pushing changes you can also run the CI tests manually with the included
`./bass/test` script.

```sh
./bass/test -i src=./
```
