#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <sys/wait.h>
#include <signal.h>
#include <fcntl.h>
#include <string>
#include <mutex>

#define LOG_TAG "PikafishJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex g_engineMutex;
pid_t g_childPid = -1;
int g_toChildFd = -1;
int g_fromChildFd = -1;
std::string g_readBuffer;

void cleanupLocked() {
    if (g_toChildFd >= 0) {
        close(g_toChildFd);
        g_toChildFd = -1;
    }
    if (g_fromChildFd >= 0) {
        close(g_fromChildFd);
        g_fromChildFd = -1;
    }
    if (g_childPid > 0) {
        kill(g_childPid, SIGTERM);
        int status = 0;
        waitpid(g_childPid, &status, 0);
        g_childPid = -1;
    }
    g_readBuffer.clear();
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_thanhvan_cotuongluyenky_engine_nativebridge_PikafishNativeBridge_nativeStartProcess(
    JNIEnv* env,
    jobject /* thiz */,
    jstring executablePath
) {
    std::lock_guard<std::mutex> lock(g_engineMutex);
    cleanupLocked();

    const char* pathChars = env->GetStringUTFChars(executablePath, nullptr);
    if (!pathChars) return JNI_FALSE;
    std::string path(pathChars);
    env->ReleaseStringUTFChars(executablePath, pathChars);

    int stdinPipe[2];
    int stdoutPipe[2];
    if (pipe(stdinPipe) != 0) {
        LOGE("Failed to create stdin pipe");
        return JNI_FALSE;
    }
    if (pipe(stdoutPipe) != 0) {
        close(stdinPipe[0]);
        close(stdinPipe[1]);
        LOGE("Failed to create stdout pipe");
        return JNI_FALSE;
    }

    pid_t pid = fork();
    if (pid < 0) {
        close(stdinPipe[0]);
        close(stdinPipe[1]);
        close(stdoutPipe[0]);
        close(stdoutPipe[1]);
        LOGE("fork() failed");
        return JNI_FALSE;
    }

    if (pid == 0) {
        dup2(stdinPipe[0], STDIN_FILENO);
        dup2(stdoutPipe[1], STDOUT_FILENO);
        dup2(stdoutPipe[1], STDERR_FILENO);
        close(stdinPipe[0]);
        close(stdinPipe[1]);
        close(stdoutPipe[0]);
        close(stdoutPipe[1]);

        execl(path.c_str(), path.c_str(), static_cast<char*>(nullptr));
        _exit(127);
    }

    close(stdinPipe[0]);
    close(stdoutPipe[1]);
    g_childPid = pid;
    g_toChildFd = stdinPipe[1];
    g_fromChildFd = stdoutPipe[0];
    LOGI("Started native engine pid=%d", static_cast<int>(pid));
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_thanhvan_cotuongluyenky_engine_nativebridge_PikafishNativeBridge_nativeSendCommand(
    JNIEnv* env,
    jobject /* thiz */,
    jstring command
) {
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (g_toChildFd < 0) return JNI_FALSE;

    const char* cmdChars = env->GetStringUTFChars(command, nullptr);
    if (!cmdChars) return JNI_FALSE;
    std::string line(cmdChars);
    env->ReleaseStringUTFChars(command, cmdChars);
    line.push_back('\n');

    ssize_t written = write(g_toChildFd, line.data(), line.size());
    return written == static_cast<ssize_t>(line.size()) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_thanhvan_cotuongluyenky_engine_nativebridge_PikafishNativeBridge_nativeReadLine(
    JNIEnv* env,
    jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (g_fromChildFd < 0) return nullptr;

    char chunk[512];
    while (true) {
        auto nlPos = g_readBuffer.find('\n');
        if (nlPos != std::string::npos) {
            std::string line = g_readBuffer.substr(0, nlPos);
            if (!line.empty() && line.back() == '\r') {
                line.pop_back();
            }
            g_readBuffer.erase(0, nlPos + 1);
            return env->NewStringUTF(line.c_str());
        }

        ssize_t n = read(g_fromChildFd, chunk, sizeof(chunk));
        if (n <= 0) {
            return nullptr;
        }
        g_readBuffer.append(chunk, static_cast<size_t>(n));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_thanhvan_cotuongluyenky_engine_nativebridge_PikafishNativeBridge_nativeStopProcess(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(g_engineMutex);
    cleanupLocked();
}
