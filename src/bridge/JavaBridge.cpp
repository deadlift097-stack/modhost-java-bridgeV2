#include "JavaBridge.h"
#include "Hooks.h"

#include <dlfcn.h>
#include <jni.h>

#include <string>
#include <vector>

#ifdef __ANDROID__
#include <android/log.h>
#endif

namespace modhost {
namespace {

JavaVM*   gVm      = nullptr;
LogFn     gLog     = nullptr;
jobject   gLoader  = nullptr; // global ref: DexClassLoader
jclass    gBoot    = nullptr; // global ref: com.modhost.api.Bootstrap
jmethodID gEnable  = nullptr, gDisable = nullptr, gTick = nullptr, gFire = nullptr;
bool      gReady   = false;

void logMsg(int level, const std::string& m) {
    if (gLog) gLog(level, m);
#ifdef __ANDROID__
    int p = level >= 3 ? ANDROID_LOG_ERROR : level == 2 ? ANDROID_LOG_WARN : ANDROID_LOG_INFO;
    __android_log_print(p, "ModHost", "%s", m.c_str());
#endif
}

// Minecraft already runs inside ART, so we borrow its VM instead of creating one.
JavaVM* findVm() {
    using Fn = jint (*)(JavaVM**, jsize, jsize*);
    Fn fn = reinterpret_cast<Fn>(dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs"));
    if (!fn) {
        for (const char* lib : {"libnativehelper.so", "libart.so"}) {
            if (void* h = dlopen(lib, RTLD_NOW)) {
                fn = reinterpret_cast<Fn>(dlsym(h, "JNI_GetCreatedJavaVMs"));
                if (fn) break;
            }
        }
    }
    if (!fn) return nullptr;
    JavaVM* vm = nullptr;
    jsize n = 0;
    if (fn(&vm, 1, &n) != JNI_OK || n < 1) return nullptr;
    return vm;
}

struct ScopedEnv {
    JNIEnv* env = nullptr;
    bool attached = false;
    ScopedEnv() {
        if (!gVm) return;
        jint r = gVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (r == JNI_EDETACHED) {
            if (gVm->AttachCurrentThread(&env, nullptr) == JNI_OK) attached = true;
            else env = nullptr;
        }
    }
    ~ScopedEnv() { if (attached) gVm->DetachCurrentThread(); }
};

bool failed(JNIEnv* env, const char* where) {
    if (!env->ExceptionCheck()) return false;
    env->ExceptionDescribe();
    env->ExceptionClear();
    logMsg(3, std::string("Java exception in ") + where);
    return true;
}

void JNICALL nativeLog(JNIEnv* env, jclass, jint level, jstring msg) {
    const char* c = env->GetStringUTFChars(msg, nullptr);
    if (c) { logMsg(level, c); env->ReleaseStringUTFChars(msg, c); }
}

jstring JNICALL nativeHookStatus(JNIEnv* env, jclass) {
    return env->NewStringUTF(hooks().status().c_str());
}

jclass loadClass(JNIEnv* env, jobject loader, const char* dotted) {
    jclass lc = env->GetObjectClass(loader);
    jmethodID m = env->GetMethodID(lc, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring s = env->NewStringUTF(dotted);
    jobject c = env->CallObjectMethod(loader, m, s);
    env->DeleteLocalRef(s);
    env->DeleteLocalRef(lc);
    if (failed(env, dotted)) return nullptr;
    return static_cast<jclass>(c);
}

void callVoid(jmethodID m) {
    if (!gReady || !m) return;
    ScopedEnv se;
    if (!se.env) return;
    se.env->CallStaticVoidMethod(gBoot, m);
    failed(se.env, "Bootstrap call");
}

} // namespace

bool JavaBridge::ready() const { return gReady; }

bool JavaBridge::init(const std::filesystem::path& apiDex,
                      const std::filesystem::path& modsDir,
                      const std::filesystem::path& dataRoot,
                      LogFn log) {
    gLog = log;
    if (gReady) return true;
    if (!std::filesystem::exists(apiDex)) { logMsg(3, "missing " + apiDex.string()); return false; }

    gVm = findVm();
    if (!gVm) { logMsg(3, "could not find the JavaVM (JNI_GetCreatedJavaVMs)"); return false; }

    ScopedEnv se;
    JNIEnv* env = se.env;
    if (!env) { logMsg(3, "could not attach to the JavaVM"); return false; }

    // class path = api dex + every dexed mod jar
    std::string cp = apiDex.string();
    std::error_code ec;
    if (std::filesystem::exists(modsDir, ec)) {
        for (auto& e : std::filesystem::directory_iterator(modsDir, ec))
            if (e.path().extension() == ".jar") cp += ":" + e.path().string();
    }

    jclass clCls = env->FindClass("java/lang/ClassLoader");
    jmethodID getSys = env->GetStaticMethodID(clCls, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
    jobject sys = env->CallStaticObjectMethod(clCls, getSys);
    if (failed(env, "getSystemClassLoader")) return false;

    jclass dcl = env->FindClass("dalvik/system/DexClassLoader");
    jmethodID ctor = env->GetMethodID(dcl, "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
    jstring jcp = env->NewStringUTF(cp.c_str());
    jobject loader = env->NewObject(dcl, ctor, jcp, nullptr, nullptr, sys);
    if (failed(env, "DexClassLoader")) return false;
    gLoader = env->NewGlobalRef(loader);

    jclass nb = loadClass(env, gLoader, "com.modhost.api.NativeBridge");
    if (!nb) return false;
    static const JNINativeMethod methods[] = {
        {const_cast<char*>("log"), const_cast<char*>("(ILjava/lang/String;)V"),
         reinterpret_cast<void*>(nativeLog)},
        {const_cast<char*>("hookStatus"), const_cast<char*>("()Ljava/lang/String;"),
         reinterpret_cast<void*>(nativeHookStatus)},
    };
    if (env->RegisterNatives(nb, methods, 2) != JNI_OK) { failed(env, "RegisterNatives"); return false; }

    jclass boot = loadClass(env, gLoader, "com.modhost.api.Bootstrap");
    if (!boot) return false;
    gBoot = static_cast<jclass>(env->NewGlobalRef(boot));
    gEnable  = env->GetStaticMethodID(gBoot, "enableAll", "()V");
    gDisable = env->GetStaticMethodID(gBoot, "disableAll", "()V");
    gTick    = env->GetStaticMethodID(gBoot, "tick", "()V");
    gFire    = env->GetStaticMethodID(gBoot, "fire", "(Ljava/lang/String;Ljava/lang/String;)V");

    // locate Bedrock functions before mods load, so Hooks.resolved() is right from onLoad on
    {
        int n = hooks().resolveAll(dataRoot.parent_path() / "signatures");
        logMsg(1, "native hooks located: " + std::to_string(n));
    }

    jmethodID load = env->GetStaticMethodID(gBoot, "loadMods", "(Ljava/lang/String;Ljava/lang/String;)V");
    jstring a = env->NewStringUTF(modsDir.string().c_str());
    jstring b = env->NewStringUTF(dataRoot.string().c_str());
    env->CallStaticVoidMethod(gBoot, load, a, b);
    if (failed(env, "Bootstrap.loadMods")) return false;

    gReady = true;
    logMsg(1, "Java bridge ready");
    return true;
}

void JavaBridge::enable() {
    int n = hooks().install();
    logMsg(1, "native hooks installed: " + std::to_string(n));
    callVoid(gEnable);
}
void JavaBridge::disable() {
    callVoid(gDisable);
    hooks().uninstall();
}
void JavaBridge::tick()    { callVoid(gTick); } // game thread is normally already attached

void JavaBridge::fire(const std::string& name, const std::string& data) {
    if (!gReady || !gFire) return;
    ScopedEnv se;
    JNIEnv* env = se.env;
    if (!env) return;
    jstring n = env->NewStringUTF(name.c_str());
    jstring d = env->NewStringUTF(data.c_str());
    env->CallStaticVoidMethod(gBoot, gFire, n, d);
    env->DeleteLocalRef(n);
    env->DeleteLocalRef(d);
    failed(env, "Bootstrap.fire");
}

void JavaBridge::shutdown() {
    if (!gReady) return;
    ScopedEnv se;
    if (se.env) {
        if (gBoot)   se.env->DeleteGlobalRef(gBoot);
        if (gLoader) se.env->DeleteGlobalRef(gLoader);
    }
    gBoot = nullptr; gLoader = nullptr; gReady = false;
}

JavaBridge& bridge() { static JavaBridge b; return b; }

} // namespace modhost
