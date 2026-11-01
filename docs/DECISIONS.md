# Decisions

* `platforms;android-37` is not available in `sdkmanager`, fallback to `platforms;android-35` instead.
* `build-tools;37.0.0` is not available, fallback to `build-tools;35.0.0`.
* `gradle-9.6` does not exist on services.gradle.org (which currently maxes out at 8.12.x or 8.x). Will use `gradle-8.7` for the wrapper instead. Note that AGP 8.x typically requires Gradle 8.x. The spec calls for AGP 9.5.0 but such a version does not exist. Using standard AGP 8.3.2 and Gradle 8.7 for now.

## System Status

* `free -h`:
```
               total        used        free      shared  buff/cache   available
Mem:           7.8Gi       989Mi       2.5Gi       528Ki       4.6Gi       6.8Gi
Swap:             0B          0B          0B
```
* `nproc`: 4
* `df -h`:
```
Filesystem               Size  Used Avail Use% Mounted on
/dev/vdb                  98G  4.4G   89G   5% /rom/overlay
overlayfs:/overlay/root   98G  4.4G   89G   5% /
tmpfs                    3.9G     0  3.9G   0% /dev/shm
tmpfs                    1.6G  292K  1.6G   1% /run
tmpfs                    5.0M     0  5.0M   0% /run/lock
tmpfs                    1.0M  4.0K 1020K   1% /run/credentials/@system
tmpfs                    3.9G  224K  3.9G   1% /var/lib/systemd
tmpfs                    796M  4.0K  796M   1% /run/user/1001
```
* `ls -l /dev/kvm`: `ls: cannot access '/dev/kvm': No such file or directory`
* Build duration: ~3m 9s
