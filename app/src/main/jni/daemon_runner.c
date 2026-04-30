/*
 * daemon_runner.c - Double-fork daemonizer for HyperRoot
 *
 * Purpose: Execute a script in a fully detached daemon process so that
 * when zygote (and its parent system_server / IMQSNative host) is
 * restarted, this process is NOT killed — it has been re-parented to
 * init (PID 1) via the classic double-fork technique.
 *
 * Usage: daemon_runner <script_path> [args...]
 *
 * Compile (NDK cross-compile, static ARM64):
 *   aarch64-linux-android35-clang -static -o daemon_runner daemon_runner.c
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <signal.h>
#include <errno.h>

#define LOG_FILE "/data/local/tmp/daemon_runner.log"

static void write_log(const char *msg) {
    int fd = open(LOG_FILE, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd >= 0) {
        write(fd, msg, strlen(msg));
        write(fd, "\n", 1);
        close(fd);
    }
}

static void close_all_fds(void) {
    /* Close all file descriptors except 0-2 (will redirect those next) */
    int maxfd = sysconf(_SC_OPEN_MAX);
    if (maxfd < 0) maxfd = 1024;
    for (int fd = 3; fd < maxfd; fd++) {
        close(fd);
    }
}

static void redirect_stdio(void) {
    int devnull = open("/dev/null", O_RDWR);
    if (devnull < 0) return;
    dup2(devnull, STDIN_FILENO);
    dup2(devnull, STDOUT_FILENO);
    dup2(devnull, STDERR_FILENO);
    if (devnull > 2) close(devnull);
}

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <script_path> [args...]\n", argv[0]);
        return 1;
    }

    write_log("[daemon_runner] Starting double-fork...");

    /* ---- First fork ---- */
    pid_t pid = fork();
    if (pid < 0) {
        write_log("[daemon_runner] First fork failed");
        return 1;
    }
    if (pid > 0) {
        /* Parent: wait for child to exit, then return success to caller */
        int status;
        waitpid(pid, &status, 0);
        write_log("[daemon_runner] First fork parent exiting");
        return 0;
    }

    /* ---- First child: create new session ---- */
    if (setsid() < 0) {
        write_log("[daemon_runner] setsid() failed");
        _exit(1);
    }

    /* Ignore SIGHUP so the second child won't be affected */
    signal(SIGHUP, SIG_IGN);

    /* ---- Second fork ---- */
    pid = fork();
    if (pid < 0) {
        write_log("[daemon_runner] Second fork failed");
        _exit(1);
    }
    if (pid > 0) {
        /* First child exits immediately — grandchild is orphaned to init */
        _exit(0);
    }

    /* ---- Grandchild: the actual daemon ---- */

    /* Reset signal handlers */
    signal(SIGHUP, SIG_DFL);

    /* New file permissions */
    umask(0);

    /* Move to a safe directory */
    chdir("/");

    /* Close inherited file descriptors */
    close_all_fds();

    /* Redirect stdin/stdout/stderr to /dev/null */
    redirect_stdio();

    char logbuf[512];
    snprintf(logbuf, sizeof(logbuf), "[daemon_runner] Daemon PID=%d executing: %s", getpid(), argv[1]);
    write_log(logbuf);

    /* Execute the target script/binary with remaining arguments */
    execv(argv[1], &argv[1]);

    /* If execv fails, try execvp as fallback */
    execvp(argv[1], &argv[1]);

    snprintf(logbuf, sizeof(logbuf), "[daemon_runner] exec failed: %s (errno=%d)", strerror(errno), errno);
    write_log(logbuf);
    _exit(1);
}
