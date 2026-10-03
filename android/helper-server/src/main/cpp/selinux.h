// Origin: RikkaApps/Shizuku @ b844bc491f1790c72328e1a8e5b2349f8978f0ea, path: manager/src/main/jni/selinux.h
#ifndef SELINUX_H
#define SELINUX_H

namespace se {
    void init();

    using getcon_t = int(char **);
    using setcon_t = int(const char *);
    using setfilecon_t = int(const char *, const char *);
    using selinux_check_access_t = int(const char *, const char *, const char *, const char *,
                                       void *);
    using freecon_t = void(char *);

    extern getcon_t *getcon;
    extern setcon_t *setcon;
    extern setfilecon_t *setfilecon;
    extern selinux_check_access_t *selinux_check_access;
    extern freecon_t *freecon;
}

#endif // SELINUX_H
