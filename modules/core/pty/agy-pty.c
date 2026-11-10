#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <pty.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/select.h>
#include <sys/wait.h>
#include <signal.h>
#include <errno.h>
#include <string.h>

static volatile pid_t g_child_pid = -1;

static void forward_signal(int sig) {
    if (g_child_pid > 0) {
        kill(-g_child_pid, sig);
        kill(g_child_pid, sig);
    }
}

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <command> [args...]\n", argv[0]);
        return 1;
    }

    struct winsize ws;
    ws.ws_row = 40;
    ws.ws_col = 120;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;

    int master = -1, slave = -1;
    if (openpty(&master, &slave, NULL, NULL, &ws) < 0) {
        perror("agy-pty: openpty");
        return 1;
    }

    pid_t pid = fork();
    if (pid < 0) {
        perror("agy-pty: fork");
        close(master);
        close(slave);
        return 1;
    }

    if (pid == 0) {
        // Child: initialize controlling terminal and exec target
        close(master);
        setsid();
        ioctl(slave, TIOCSCTTY, 0);

        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) {
            close(slave);
        }

        execvp(argv[1], &argv[1]);
        perror("agy-pty: execvp");
        _exit(127);
    }

    // Parent: bridge pipes and forward signals
    close(slave);
    g_child_pid = pid;

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = forward_signal;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGTERM, &sa, NULL);
    sigaction(SIGINT, &sa, NULL);
    sigaction(SIGHUP, &sa, NULL);

    int stdin_open = 1;
    char buf[4096];

    while (1) {
        fd_set rfds;
        FD_ZERO(&rfds);
        int maxfd = master;

        if (stdin_open) {
            FD_SET(STDIN_FILENO, &rfds);
            if (STDIN_FILENO > maxfd) {
                maxfd = STDIN_FILENO;
            }
        }
        FD_SET(master, &rfds);

        struct timeval tv;
        tv.tv_sec = 0;
        tv.tv_usec = 100000; // 100ms poll interval

        int ret = select(maxfd + 1, &rfds, NULL, NULL, &tv);
        if (ret < 0) {
            if (errno == EINTR) continue;
            break;
        }

        if (stdin_open && FD_ISSET(STDIN_FILENO, &rfds)) {
            ssize_t n = read(STDIN_FILENO, buf, sizeof(buf));
            if (n <= 0) {
                stdin_open = 0; // EOF on stdin; stop polling stdin
            } else {
                ssize_t written = 0;
                while (written < n) {
                    ssize_t w = write(master, buf + written, n - written);
                    if (w <= 0) break;
                    written += w;
                }
            }
        }

        if (FD_ISSET(master, &rfds)) {
            ssize_t n = read(master, buf, sizeof(buf));
            if (n <= 0) {
                // Slave closed or EIO on hangup
                break;
            }
            ssize_t written = 0;
            while (written < n) {
                ssize_t w = write(STDOUT_FILENO, buf + written, n - written);
                if (w <= 0) break;
                written += w;
            }
        }

        int status;
        pid_t dead = waitpid(pid, &status, WNOHANG);
        if (dead > 0) {
            // Child exited: drain master once more
            while (1) {
                ssize_t n = read(master, buf, sizeof(buf));
                if (n <= 0) break;
                ssize_t written = 0;
                while (written < n) {
                    ssize_t w = write(STDOUT_FILENO, buf + written, n - written);
                    if (w <= 0) break;
                    written += w;
                }
            }
            close(master);
            if (WIFEXITED(status)) return WEXITSTATUS(status);
            if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
            return 0;
        }
    }

    close(master);
    int status;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return 0;
}
